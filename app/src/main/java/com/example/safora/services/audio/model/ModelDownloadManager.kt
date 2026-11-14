package com.example.safora.services.audio.model

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.safora.MainActivity
import com.example.safora.core.utils.PermissionHelper
import com.example.safora.services.audio.AudioMonitoringService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

enum class SafetyModelType(
    val id: String,
    val title: String,
    val subtitle: String,
    val description: String,
    val sizeText: String,
    val approxSizeBytes: Long
) {
    YAMNET(
        id = "yamnet",
        title = "Model 1: YAMNet Acoustic Distress",
        subtitle = "Continuous Acoustic Distress & Scream Classifier",
        description = "Runs 16 kHz mono floating-point inference across 521 AudioSet categories. Detects screaming, crying, shouting, and wailing with dedicated fast-track escalation.",
        sizeText = "4.1 MB",
        approxSizeBytes = 4_126_810L
    ),
    VOSK_EN(
        id = "vosk_en",
        title = "Model 2: Vosk English Speech Recognition",
        subtitle = "Offline Kaldi VAD-Gated Speech-to-Text",
        description = "Complete on-device English acoustic & language model. Activated by Voice Activity Detection to continuously transcribe speech context with zero cloud dependency.",
        sizeText = "68 MB",
        approxSizeBytes = 71_303_168L
    ),
    SAFETY_CLASSIFIER(
        id = "safety_classifier",
        title = "Model 4: DistilBERT Threat Classifier",
        subtitle = "Offline Semantic Abuse & Coercion Classifier",
        description = "Evaluates rolling Vosk conversational transcripts against physical violence, harassment, and coercion patterns using the 0.05 safety decision threshold.",
        sizeText = "1.2 MB",
        approxSizeBytes = 1_258_291L
    )
}

enum class ModelDownloadStatus {
    NOT_DOWNLOADED,
    DOWNLOADING,
    DOWNLOADED,
    ERROR
}

data class ModelDownloadItem(
    val type: SafetyModelType,
    val status: ModelDownloadStatus,
    val progress: Float = 0.0f,
    val errorMessage: String? = null,
    val localPath: String = ""
)

/**
 * Model Download and Provisioning Manager.
 *
 * Manages downloading, local storage, lifecycle, and integrity checks for the
 * SAFORA on-device safety models (YAMNet, Vosk English, Model 4 Threat Classifier).
 */
class ModelDownloadManager private constructor(private val appContext: Context) {

    companion object {
        private const val TAG = "ModelDownloadManager"
        const val NOTIFICATION_CHANNEL_ID = "safora_model_status_channel"
        const val NOTIFICATION_CHANNEL_NAME = "SAFORA Model Status"
        const val NOTIFICATION_ID_MODELS_MISSING = 1009
        const val EXTRA_OPEN_PROFILE = "com.example.safora.OPEN_PROFILE"

        @Volatile
        private var instance: ModelDownloadManager? = null

        fun getInstance(context: Context): ModelDownloadManager {
            return instance ?: synchronized(this) {
                instance ?: ModelDownloadManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private val scope = CoroutineScope(Dispatchers.IO)
    private val modelsDir = File(appContext.filesDir, "models").apply { if (!exists()) mkdirs() }

    private val _modelsState = MutableStateFlow<List<ModelDownloadItem>>(emptyList())
    val modelsState: StateFlow<List<ModelDownloadItem>> = _modelsState.asStateFlow()

    private val _areAllModelsDownloaded = MutableStateFlow(false)
    val areAllModelsDownloaded: StateFlow<Boolean> = _areAllModelsDownloaded.asStateFlow()

    init {
        refreshModelStatuses()
    }

    fun refreshModelStatuses() {
        val list = SafetyModelType.values().map { type ->
            val target = getTargetFileOrDir(type)
            val downloaded = isModelDownloaded(type, target)
            ModelDownloadItem(
                type = type,
                status = if (downloaded) ModelDownloadStatus.DOWNLOADED else ModelDownloadStatus.NOT_DOWNLOADED,
                progress = if (downloaded) 1.0f else 0.0f,
                localPath = target.absolutePath
            )
        }
        _modelsState.value = list
        val allReady = list.all { it.status == ModelDownloadStatus.DOWNLOADED }
        _areAllModelsDownloaded.value = allReady
    }

    fun getTargetFileOrDir(type: SafetyModelType): File {
        return when (type) {
            SafetyModelType.YAMNET -> File(modelsDir, "yamnet.tflite")
            SafetyModelType.VOSK_EN -> File(modelsDir, "vosk-model-small-en-us-0.15")
            SafetyModelType.SAFETY_CLASSIFIER -> File(modelsDir, "safora_metadata.json")
        }
    }

    fun isModelDownloaded(type: SafetyModelType, fileOrDir: File = getTargetFileOrDir(type)): Boolean {
        return when (type) {
            SafetyModelType.YAMNET -> {
                fileOrDir.exists() && fileOrDir.length() >= 4_000_000L
            }
            SafetyModelType.VOSK_EN -> {
                if (!fileOrDir.exists() || !fileOrDir.isDirectory) return false
                val am = File(fileOrDir, "am")
                val graph = File(fileOrDir, "graph")
                am.exists() && graph.exists()
            }
            SafetyModelType.SAFETY_CLASSIFIER -> {
                fileOrDir.exists() && fileOrDir.length() > 100L
            }
        }
    }

    fun areAllDownloaded(): Boolean {
        return SafetyModelType.values().all { isModelDownloaded(it) }
    }

    /**
     * Downloads an individual model with progressive tracking.
     */
    fun downloadModel(type: SafetyModelType) {
        scope.launch {
            updateItemState(type, ModelDownloadStatus.DOWNLOADING, 0.05f)

            try {
                when (type) {
                    SafetyModelType.YAMNET -> {
                        downloadYamnetModel { progress ->
                            updateItemState(type, ModelDownloadStatus.DOWNLOADING, progress)
                        }
                    }
                    SafetyModelType.VOSK_EN -> {
                        downloadVoskEnglishModel { progress ->
                            updateItemState(type, ModelDownloadStatus.DOWNLOADING, progress)
                        }
                    }
                    SafetyModelType.SAFETY_CLASSIFIER -> {
                        downloadSafetyClassifierModel { progress ->
                            updateItemState(type, ModelDownloadStatus.DOWNLOADING, progress)
                        }
                    }
                }

                updateItemState(type, ModelDownloadStatus.DOWNLOADED, 1.0f)
                refreshModelStatuses()

                // If all models are now downloaded and mic permission is granted, ensure monitoring starts
                if (areAllDownloaded()) {
                    onAllModelsReady()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed downloading ${type.name}: ${e.message}", e)
                updateItemState(type, ModelDownloadStatus.ERROR, 0.0f, e.message ?: "Download failed")
            }
        }
    }

    /**
     * Downloads all un-downloaded models sequentially.
     */
    fun downloadAllModels() {
        scope.launch {
            for (type in SafetyModelType.values()) {
                if (!isModelDownloaded(type)) {
                    downloadModel(type)
                }
            }
        }
    }

    /**
     * Deletes a downloaded model from local storage.
     */
    fun deleteModel(type: SafetyModelType) {
        scope.launch {
            val target = getTargetFileOrDir(type)
            if (target.exists()) {
                target.deleteRecursively()
            }
            refreshModelStatuses()
        }
    }

    private fun updateItemState(
        type: SafetyModelType,
        status: ModelDownloadStatus,
        progress: Float,
        errorMessage: String? = null
    ) {
        _modelsState.value = _modelsState.value.map { item ->
            if (item.type == type) {
                item.copy(
                    status = status,
                    progress = progress,
                    errorMessage = errorMessage
                )
            } else {
                item
            }
        }
        _areAllModelsDownloaded.value = _modelsState.value.all { it.status == ModelDownloadStatus.DOWNLOADED }
    }

    private suspend fun downloadYamnetModel(onProgress: (Float) -> Unit) = withContext(Dispatchers.IO) {
        val destFile = getTargetFileOrDir(SafetyModelType.YAMNET)
        copyAssetWithProgress("models/yamnet.tflite", destFile, onProgress)
    }

    private suspend fun downloadSafetyClassifierModel(onProgress: (Float) -> Unit) = withContext(Dispatchers.IO) {
        val destFile = getTargetFileOrDir(SafetyModelType.SAFETY_CLASSIFIER)
        copyAssetWithProgress("models/safora_metadata.json", destFile) { p ->
            onProgress(p * 0.5f)
        }
        val configFile = File(modelsDir, "config.json")
        try {
            copyAssetWithProgress("models/config.json", configFile) { p ->
                onProgress(0.5f + p * 0.5f)
            }
        } catch (_: Exception) {}
        onProgress(1.0f)
    }

    private suspend fun downloadVoskEnglishModel(onProgress: (Float) -> Unit) = withContext(Dispatchers.IO) {
        val destDir = getTargetFileOrDir(SafetyModelType.VOSK_EN)
        if (!destDir.exists()) destDir.mkdirs()

        val assetManager = appContext.assets
        val rootPath = "models/vosk-model-small-en-us-0.15"

        // Collect all files to accurately compute progress
        val allFiles = mutableListOf<String>()
        fun listRecursive(path: String) {
            val list = assetManager.list(path) ?: return
            if (list.isEmpty()) {
                allFiles.add(path)
            } else {
                for (item in list) {
                    val sub = "$path/$item"
                    val subList = assetManager.list(sub)
                    if (subList != null && subList.isNotEmpty()) {
                        listRecursive(sub)
                    } else {
                        allFiles.add(sub)
                    }
                }
            }
        }
        listRecursive(rootPath)

        val totalFiles = allFiles.size.coerceAtLeast(1)
        var copiedFiles = 0

        for (srcPath in allFiles) {
            val relPath = srcPath.removePrefix("$rootPath/").removePrefix(rootPath)
            val targetFile = File(destDir, relPath)
            targetFile.parentFile?.mkdirs()

            assetManager.open(srcPath).use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            }
            copiedFiles++
            onProgress(copiedFiles.toFloat() / totalFiles)
        }
        onProgress(1.0f)
    }

    private fun copyAssetWithProgress(assetPath: String, destFile: File, onProgress: (Float) -> Unit) {
        destFile.parentFile?.mkdirs()
        val assetManager = appContext.assets
        val fd = try { assetManager.openFd(assetPath) } catch (_: Exception) { null }
        val totalBytes = fd?.length?.takeIf { it > 0 } ?: 4_000_000L

        var bytesWritten = 0L
        val buffer = ByteArray(32768)

        assetManager.open(assetPath).use { input ->
            FileOutputStream(destFile).use { output ->
                var read: Int
                while (input.read(buffer).also { read = it } != -1) {
                    output.write(buffer, 0, read)
                    bytesWritten += read
                    val p = (bytesWritten.toFloat() / totalBytes).coerceIn(0.0f, 1.0f)
                    onProgress(p)
                }
            }
        }
        onProgress(1.0f)
    }

    private fun onAllModelsReady() {
        Log.i(TAG, "All SAFORA safety AI models downloaded and verified.")
        cancelMissingModelsNotification()

        // If audio permission is available, start the microphone monitoring service
        if (PermissionHelper.hasAudioPermission(appContext)) {
            Log.i(TAG, "Starting AudioMonitoringService with live microphone protection.")
            AudioMonitoringService.start(appContext)
        }
    }

    /**
     * Called whenever the app is opened (onCreate/onResume).
     *
     * If models are NOT downloaded:
     * - Posts a persistent/alert notification to notify user to download in Profile.
     * If models ARE downloaded:
     * - Clears notification and ensures continuous microphone monitoring is active.
     */
    fun checkModelStatusAndNotify(context: Context) {
        refreshModelStatuses()
        val allReady = areAllDownloaded()

        if (!allReady) {
            Log.w(TAG, "Models missing on app open. Emitting notification.")
            postMissingModelsNotification(context)
        } else {
            cancelMissingModelsNotification()
            if (PermissionHelper.hasAudioPermission(context)) {
                AudioMonitoringService.start(context)
            }
        }
    }

    private fun postMissingModelsNotification(context: Context) {
        createNotificationChannel()

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN_PROFILE, true)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID_MODELS_MISSING,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("⚠️ Safety AI Models Required")
            .setContentText("Offline scream & speech detection is disabled. Tap to download models in Profile.")
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "SAFORA requires on-device AI models for offline scream detection, speech recognition, and threat evaluation. Tap to download them now in the Profile tab."
                )
            )
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID_MODELS_MISSING, notification)
    }

    fun cancelMissingModelsNotification() {
        try {
            val manager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.cancel(NOTIFICATION_ID_MODELS_MISSING)
        } catch (e: Exception) {
            Log.w(TAG, "Could not cancel notification: ${e.message}")
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                NOTIFICATION_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifies when required AI models are missing on device."
            }
            val manager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }
}
