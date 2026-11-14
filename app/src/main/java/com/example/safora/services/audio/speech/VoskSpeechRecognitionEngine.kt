package com.example.safora.services.audio.speech

import android.content.Context
import android.util.Log
import com.example.safora.data.model.SpeechTranscriptResult
import com.example.safora.services.audio.vad.VadListener
import kotlinx.coroutines.*
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

interface VoskTranscriptListener {
    fun onNewTranscriptSentence(result: SpeechTranscriptResult)
    fun onRollingContextUpdated(fullContext: String)
}

/**
 * Offline English speech-to-text engine using Vosk (vosk-model-small-en-us-0.15).
 *
 * Gated by Voice Activity Detection (VAD) to preserve CPU and battery.
 * Extracts sentence-level transcriptions, prevents duplicate submissions,
 * and maintains a rolling conversation context for Model 4 safety analysis.
 */
class VoskSpeechRecognitionEngine(
    private val context: Context
) : VadListener {

    companion object {
        private const val TAG = "VoskSpeechEngine"
        const val MODEL_ASSET_DIR = "models/vosk-model-small-en-us-0.15"
        const val MODEL_TARGET_DIR_NAME = "vosk_en_model"
        const val MAX_CONTEXT_SENTENCES = 6
        const val MAX_CONTEXT_AGE_MS = 45000L // 45 seconds window
    }

    private var voskModel: Model? = null
    private var voskRecognizer: Recognizer? = null
    private var isModelLoaded = false
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val listeners = mutableListOf<VoskTranscriptListener>()

    // Rolling transcript context with timestamps
    private val rollingTranscriptList = mutableListOf<Pair<Long, String>>()
    private var lastEmittedSentence: String = ""

    fun addListener(listener: VoskTranscriptListener) {
        synchronized(listeners) { listeners.add(listener) }
    }

    fun removeListener(listener: VoskTranscriptListener) {
        synchronized(listeners) { listeners.remove(listener) }
    }

    suspend fun initializeAsync(): Boolean = withContext(Dispatchers.IO) {
        if (isModelLoaded && voskRecognizer != null) return@withContext true

        try {
            val downloadedDir = File(context.filesDir, "models/vosk-model-small-en-us-0.15")
            val targetDir = if (isModelExtracted(downloadedDir)) {
                downloadedDir
            } else {
                val fallbackDir = File(context.filesDir, MODEL_TARGET_DIR_NAME)
                if (!isModelExtracted(fallbackDir)) {
                    Log.i(TAG, "Extracting Vosk English model from assets...")
                    val success = copyAssetFolder(context, MODEL_ASSET_DIR, fallbackDir)
                    if (!success) {
                        Log.e(TAG, "Failed to extract Vosk model from assets")
                        return@withContext false
                    }
                }
                fallbackDir
            }

            Log.i(TAG, "Loading Vosk Model from ${targetDir.absolutePath}...")
            voskModel = Model(targetDir.absolutePath)
            voskRecognizer = Recognizer(voskModel, 16000.0f)
            isModelLoaded = true
            Log.i(TAG, "Vosk English Speech Recognition model successfully initialized!")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing Vosk model: ${e.message}", e)
            isModelLoaded = false
            false
        }
    }

    private fun isModelExtracted(targetDir: File): Boolean {
        if (!targetDir.exists() || !targetDir.isDirectory) return false
        val amDir = File(targetDir, "am")
        val graphDir = File(targetDir, "graph")
        return amDir.exists() && graphDir.exists()
    }

    private fun copyAssetFolder(context: Context, srcAssetPath: String, destDir: File): Boolean {
        try {
            val assetManager = context.assets
            val files = assetManager.list(srcAssetPath) ?: return false

            if (!destDir.exists()) destDir.mkdirs()

            if (files.isEmpty()) {
                // Leaf file
                copyAssetFile(context, srcAssetPath, destDir)
            } else {
                for (file in files) {
                    val subSrc = "$srcAssetPath/$file"
                    val subDest = File(destDir, file)
                    val subFiles = assetManager.list(subSrc)
                    if (subFiles != null && subFiles.isNotEmpty()) {
                        copyAssetFolder(context, subSrc, subDest)
                    } else {
                        copyAssetFile(context, subSrc, subDest)
                    }
                }
            }
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Asset copy error: ${e.message}", e)
            return false
        }
    }

    private fun copyAssetFile(context: Context, srcAssetPath: String, destFile: File) {
        destFile.parentFile?.mkdirs()
        context.assets.open(srcAssetPath).use { input ->
            FileOutputStream(destFile).use { output ->
                input.copyTo(output)
            }
        }
    }

    override fun onSpeechStateChanged(isSpeechActive: Boolean, rmsDbfs: Float) {
        if (!isSpeechActive && isModelLoaded) {
            // Speech paused: finalize recognizer buffer
            val recognizer = voskRecognizer ?: return
            val finalJson = recognizer.finalResult
            processVoskJson(finalJson, isFinal = true)
        }
    }

    override fun onSpeechAudioFrame(pcmData: ShortArray, readSize: Int) {
        if (!isModelLoaded || readSize <= 0) return

        val recognizer = voskRecognizer ?: return

        // Convert ShortArray to ByteArray (Little-Endian 16-bit PCM)
        val byteBuffer = ByteBuffer.allocate(readSize * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until readSize) {
            byteBuffer.putShort(pcmData[i])
        }
        val bytes = byteBuffer.array()

        if (recognizer.acceptWaveForm(bytes, bytes.size)) {
            val resultJson = recognizer.result
            processVoskJson(resultJson, isFinal = true)
        } else {
            val partialJson = recognizer.partialResult
            processVoskJson(partialJson, isFinal = false)
        }
    }

    private fun processVoskJson(jsonStr: String, isFinal: Boolean) {
        if (jsonStr.isBlank()) return

        try {
            val json = JSONObject(jsonStr)
            val text = if (isFinal) {
                json.optString("text", "").trim()
            } else {
                json.optString("partial", "").trim()
            }

            if (text.isBlank()) return

            if (isFinal) {
                // Avoid submitting duplicate sentences
                if (text.equals(lastEmittedSentence, ignoreCase = true)) return
                lastEmittedSentence = text

                val now = System.currentTimeMillis()
                synchronized(rollingTranscriptList) {
                    rollingTranscriptList.add(Pair(now, text))
                    // Prune old sentences exceeding window or count limit
                    rollingTranscriptList.removeAll { (now - it.first) > MAX_CONTEXT_AGE_MS }
                    while (rollingTranscriptList.size > MAX_CONTEXT_SENTENCES) {
                        rollingTranscriptList.removeAt(0)
                    }
                }

                val fullContext = getRollingContext()
                Log.d(TAG, "[Vosk Final] \"$text\" | Rolling Context: \"$fullContext\"")

                val result = SpeechTranscriptResult(text, isFinal = true, timestamp = now)
                synchronized(listeners) {
                    for (l in listeners) {
                        try {
                            l.onNewTranscriptSentence(result)
                            l.onRollingContextUpdated(fullContext)
                        } catch (e: Exception) {
                            Log.e(TAG, "Listener error: ${e.message}")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing Vosk JSON: ${e.message}")
        }
    }

    fun getRollingContext(): String {
        synchronized(rollingTranscriptList) {
            val now = System.currentTimeMillis()
            rollingTranscriptList.removeAll { (now - it.first) > MAX_CONTEXT_AGE_MS }
            return rollingTranscriptList.joinToString(". ") { it.second }
        }
    }

    fun clearTranscriptHistory() {
        synchronized(rollingTranscriptList) {
            rollingTranscriptList.clear()
            lastEmittedSentence = ""
        }
    }

    fun close() {
        voskRecognizer?.close()
        voskModel?.close()
        voskRecognizer = null
        voskModel = null
        isModelLoaded = false
    }
}
