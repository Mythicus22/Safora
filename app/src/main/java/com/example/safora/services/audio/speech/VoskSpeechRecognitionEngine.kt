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
 * Offline Speech-to-Text engine supporting English and Hindi via Vosk Kaldi runtime.
 *
 * Models:
 * - English: vosk-model-small-en-us-0.15
 * - Hindi:   vosk-model-small-hi-0.22
 *
 * Gated by Voice Activity Detection (VAD) to preserve CPU and battery.
 * Fully thread-synchronized to prevent C++ native segfaults in libvosk.so.
 * Automatically identifies language based on acoustic recognition and Devanagari script,
 * then forwards transcripts to Model 4 safety analysis.
 */
class VoskSpeechRecognitionEngine(
    private val context: Context
) : VadListener {

    companion object {
        private const val TAG = "VoskSpeechEngine"
        const val MODEL_EN_ASSET_DIR = "models/vosk-model-small-en-us-0.15"
        const val MODEL_EN_TARGET_DIR_NAME = "vosk_en_model"
        const val MODEL_HI_ASSET_DIR = "models/vosk-model-small-hi-0.22"
        const val MODEL_HI_TARGET_DIR_NAME = "vosk_hi_model"
        const val MAX_CONTEXT_SENTENCES = 6
        const val MAX_CONTEXT_AGE_MS = 45000L // 45 seconds window
    }

    private val recognizerLock = Any()

    private var voskModelEn: Model? = null
    private var voskRecognizerEn: Recognizer? = null
    private var isModelEnLoaded = false

    private var voskModelHi: Model? = null
    private var voskRecognizerHi: Recognizer? = null
    private var isModelHiLoaded = false

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
        var anyLoaded = false

        // 1. Initialize English model
        try {
            val downloadedDirEn = File(context.filesDir, "models/vosk-model-small-en-us-0.15")
            val targetDirEn = if (isModelExtracted(downloadedDirEn)) {
                downloadedDirEn
            } else {
                val fallbackDir = File(context.filesDir, MODEL_EN_TARGET_DIR_NAME)
                if (!isModelExtracted(fallbackDir)) {
                    Log.i(TAG, "Extracting Vosk English model from assets...")
                    copyAssetFolder(context, MODEL_EN_ASSET_DIR, fallbackDir)
                }
                fallbackDir
            }

            if (isModelExtracted(targetDirEn)) {
                Log.i(TAG, "Loading Vosk English Model from ${targetDirEn.absolutePath}...")
                synchronized(recognizerLock) {
                    voskModelEn = Model(targetDirEn.absolutePath)
                    voskRecognizerEn = Recognizer(voskModelEn, 16000.0f)
                    isModelEnLoaded = true
                    anyLoaded = true
                }
                Log.i(TAG, "Vosk English model successfully initialized!")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing English Vosk model: ${e.message}", e)
        }

        // 2. Initialize Hindi model
        try {
            val downloadedDirHi = File(context.filesDir, "models/vosk-model-small-hi-0.22")
            val targetDirHi = if (isModelExtracted(downloadedDirHi)) {
                downloadedDirHi
            } else {
                val fallbackDir = File(context.filesDir, MODEL_HI_TARGET_DIR_NAME)
                if (!isModelExtracted(fallbackDir)) {
                    Log.i(TAG, "Extracting Vosk Hindi model from assets...")
                    copyAssetFolder(context, MODEL_HI_ASSET_DIR, fallbackDir)
                }
                fallbackDir
            }

            if (isModelExtracted(targetDirHi)) {
                Log.i(TAG, "Loading Vosk Hindi Model from ${targetDirHi.absolutePath}...")
                synchronized(recognizerLock) {
                    voskModelHi = Model(targetDirHi.absolutePath)
                    voskRecognizerHi = Recognizer(voskModelHi, 16000.0f)
                    isModelHiLoaded = true
                    anyLoaded = true
                }
                Log.i(TAG, "Vosk Hindi model successfully initialized!")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing Hindi Vosk model: ${e.message}", e)
        }

        anyLoaded
    }

    private fun isModelExtracted(targetDir: File): Boolean {
        if (!targetDir.exists() || !targetDir.isDirectory) return false
        val amDir = File(targetDir, "am")
        val graphDir = File(targetDir, "graph")
        return amDir.exists() && graphDir.exists()
    }

    private fun copyAssetFolder(context: Context, srcAssetPath: String, destDir: File): Boolean {
        return try {
            val assetManager = context.assets
            val files = assetManager.list(srcAssetPath) ?: return false

            if (!destDir.exists()) destDir.mkdirs()

            if (files.isEmpty()) {
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
            true
        } catch (e: Exception) {
            Log.e(TAG, "Asset copy error ($srcAssetPath): ${e.message}")
            false
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
        if (!isSpeechActive) {
            // Speech paused: finalize recognizer buffers safely under lock
            synchronized(recognizerLock) {
                if (isModelEnLoaded) {
                    try {
                        val finalEn = voskRecognizerEn?.finalResult
                        if (!finalEn.isNullOrBlank()) {
                            processVoskJson(finalEn, isFinal = true, language = "EN")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error finalizing EN recognizer: ${e.message}")
                    }
                }
                if (isModelHiLoaded) {
                    try {
                        val finalHi = voskRecognizerHi?.finalResult
                        if (!finalHi.isNullOrBlank()) {
                            processVoskJson(finalHi, isFinal = true, language = "HI")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error finalizing HI recognizer: ${e.message}")
                    }
                }
            }
        }
    }

    override fun onSpeechAudioFrame(pcmData: ShortArray, readSize: Int) {
        if (readSize <= 0) return

        // Convert ShortArray to ByteArray (Little-Endian 16-bit PCM)
        val byteBuffer = ByteBuffer.allocate(readSize * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until readSize) {
            byteBuffer.putShort(pcmData[i])
        }
        val bytes = byteBuffer.array()

        // Feed both recognizers under lock to avoid Kaldi native memory race conditions
        synchronized(recognizerLock) {
            if (isModelEnLoaded) {
                try {
                    val recognizer = voskRecognizerEn
                    if (recognizer != null) {
                        if (recognizer.acceptWaveForm(bytes, bytes.size)) {
                            val resultJson = recognizer.result
                            if (!resultJson.isNullOrBlank()) {
                                processVoskJson(resultJson, isFinal = true, language = "EN")
                            }
                        } else {
                            val partialJson = recognizer.partialResult
                            if (!partialJson.isNullOrBlank()) {
                                processVoskJson(partialJson, isFinal = false, language = "EN")
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing EN audio frame: ${e.message}")
                }
            }

            if (isModelHiLoaded) {
                try {
                    val recognizer = voskRecognizerHi
                    if (recognizer != null) {
                        if (recognizer.acceptWaveForm(bytes, bytes.size)) {
                            val resultJson = recognizer.result
                            if (!resultJson.isNullOrBlank()) {
                                processVoskJson(resultJson, isFinal = true, language = "HI")
                            }
                        } else {
                            val partialJson = recognizer.partialResult
                            if (!partialJson.isNullOrBlank()) {
                                processVoskJson(partialJson, isFinal = false, language = "HI")
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing HI audio frame: ${e.message}")
                }
            }
        }
    }

    private fun isDevanagari(text: String): Boolean {
        return text.any { it in '\u0900'..'\u097F' }
    }

    private fun processVoskJson(jsonStr: String, isFinal: Boolean, language: String) {
        if (jsonStr.isBlank()) return

        try {
            val json = JSONObject(jsonStr)
            val text = if (isFinal) {
                json.optString("text", "").trim()
            } else {
                json.optString("partial", "").trim()
            }

            if (text.isBlank()) return

            // Determine detected language: Devanagari script is strictly Hindi
            val detectedLang = if (isDevanagari(text)) "HI" else language

            if (isFinal) {
                // Avoid submitting duplicate sentences
                if (text.equals(lastEmittedSentence, ignoreCase = true)) return
                lastEmittedSentence = text

                val now = System.currentTimeMillis()
                synchronized(rollingTranscriptList) {
                    rollingTranscriptList.add(Pair(now, "[$detectedLang] $text"))
                    // Prune old sentences exceeding window or count limit
                    rollingTranscriptList.removeAll { (now - it.first) > MAX_CONTEXT_AGE_MS }
                    while (rollingTranscriptList.size > MAX_CONTEXT_SENTENCES) {
                        rollingTranscriptList.removeAt(0)
                    }
                }

                val fullContext = getRollingContext()
                Log.d(TAG, "[Vosk $detectedLang Final] \"$text\" | Rolling Context: \"$fullContext\"")

                val result = SpeechTranscriptResult(
                    text = text,
                    isFinal = true,
                    language = detectedLang,
                    timestamp = now
                )

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
        synchronized(recognizerLock) {
            try { voskRecognizerEn?.close() } catch (_: Exception) {}
            try { voskRecognizerHi?.close() } catch (_: Exception) {}
            try { voskModelEn?.close() } catch (_: Exception) {}
            try { voskModelHi?.close() } catch (_: Exception) {}
            voskRecognizerEn = null
            voskRecognizerHi = null
            voskModelEn = null
            voskModelHi = null
            isModelEnLoaded = false
            isModelHiLoaded = false
        }
    }
}
