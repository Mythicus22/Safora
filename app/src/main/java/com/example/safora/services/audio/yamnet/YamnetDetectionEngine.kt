package com.example.safora.services.audio.yamnet

import android.content.Context
import android.content.res.AssetFileDescriptor
import android.util.Log
import com.example.safora.data.model.YamnetInferenceResult
import com.example.safora.services.audio.capture.AudioStreamListener
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import kotlin.math.max

interface YamnetDetectionListener {
    fun onYamnetInference(result: YamnetInferenceResult)
    fun onFastDistressEscalation(result: YamnetInferenceResult)
}

/**
 * Continuous acoustic event detection engine powered by TensorFlow Lite YAMNet.
 *
 * Runs continuously on 15,600 sample (0.975s) audio windows @ 16 kHz mono.
 * Specifically tracks screaming, yelling, shouting, crying and wailing.
 *
 * Implements temporal smoothing and a dedicated fast-escalation path for high-confidence screams.
 */
class YamnetDetectionEngine(
    private val context: Context,
    private var screamEmergencyThreshold: Float = 0.28f,
    private var generalDistressThreshold: Float = 0.18f
) : AudioStreamListener {

    companion object {
        private const val TAG = "YamnetDetectionEngine"
        const val YAMNET_INPUT_SAMPLES = 15600 // 0.975 seconds @ 16 kHz
        const val YAMNET_STEP_SAMPLES = 8000   // 0.500s step (50% overlap for responsive tracking)
        const val YAMNET_NUM_CLASSES = 521

        // Primary acoustic indices from official YAMNet 521-class specification
        const val CLASS_SHOUT = 6
        const val CLASS_YELL = 9
        const val CLASS_CHILDREN_SHOUTING = 10
        const val CLASS_SCREAMING = 11
        const val CLASS_CRYING = 19
        const val CLASS_BABY_CRY = 20
        const val CLASS_WAIL = 22
        const val CLASS_GROAN = 33
        const val CLASS_GASP = 39

        val DISTRESS_CLASS_NAMES = mapOf(
            CLASS_SCREAMING to "Screaming",
            CLASS_SHOUT to "Shout",
            CLASS_YELL to "Yell",
            CLASS_CHILDREN_SHOUTING to "Children shouting",
            CLASS_CRYING to "Crying, sobbing",
            CLASS_BABY_CRY to "Baby cry",
            CLASS_WAIL to "Wail, moan",
            CLASS_GROAN to "Groan",
            CLASS_GASP to "Gasp"
        )
    }

    private var tfliteInterpreter: Interpreter? = null
    private var isInitialized = false
    private val interpreterLock = Any()

    // Sliding audio sample accumulator (15600 samples)
    private val sampleBuffer = FloatArray(YAMNET_INPUT_SAMPLES)
    private var samplesAccumulated = 0
    private val bufferLock = Any()

    // Temporal smoothing state
    private var smoothedScreamScore = 0.0f
    private var smoothedGeneralDistress = 0.0f

    private val listeners = mutableListOf<YamnetDetectionListener>()

    fun addListener(listener: YamnetDetectionListener) {
        synchronized(listeners) { listeners.add(listener) }
    }

    fun removeListener(listener: YamnetDetectionListener) {
        synchronized(listeners) { listeners.remove(listener) }
    }

    fun initialize(): Boolean {
        synchronized(interpreterLock) {
            if (isInitialized && tfliteInterpreter != null) return true

            return try {
                val downloadedFile = java.io.File(context.filesDir, "models/yamnet.tflite")
                val modelBuffer = loadModelFile(downloadedFile, "models/yamnet.tflite")
                val options = Interpreter.Options().apply {
                    setNumThreads(2) // Balanced energy efficiency & fast latency
                }
                tfliteInterpreter = Interpreter(modelBuffer, options)
                isInitialized = true
                Log.i(TAG, "YAMNet TFLite interpreter initialized successfully.")
                true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load YAMNet TFLite model: ${e.message}", e)
                isInitialized = false
                false
            }
        }
    }

    fun setSensitivity(level: String) {
        when (level.lowercase()) {
            "high" -> {
                screamEmergencyThreshold = 0.20f
                generalDistressThreshold = 0.14f
            }
            "low" -> {
                screamEmergencyThreshold = 0.40f
                generalDistressThreshold = 0.28f
            }
            else -> { // Medium default
                screamEmergencyThreshold = 0.28f
                generalDistressThreshold = 0.18f
            }
        }
        Log.d(TAG, "YAMNet thresholds set to Scream/Distress: $screamEmergencyThreshold, General: $generalDistressThreshold ($level)")
    }

    override fun onAudioFrame(pcmData: ShortArray, readSize: Int) {
        if (readSize <= 0) return

        synchronized(bufferLock) {
            var readOffset = 0
            while (readOffset < readSize) {
                val availableInFrame = readSize - readOffset
                val neededInBuffer = YAMNET_INPUT_SAMPLES - samplesAccumulated
                val toCopy = minOf(availableInFrame, neededInBuffer)

                for (i in 0 until toCopy) {
                    // Normalize 16-bit PCM to float [-1.0, 1.0]
                    sampleBuffer[samplesAccumulated + i] = (pcmData[readOffset + i].toFloat() / 32768.0f).coerceIn(-1.0f, 1.0f)
                }

                samplesAccumulated += toCopy
                readOffset += toCopy

                if (samplesAccumulated >= YAMNET_INPUT_SAMPLES) {
                    // Full 0.975s window ready for inference
                    processInferenceWindow(sampleBuffer.copyOf())

                    // Slide buffer forward by YAMNET_STEP_SAMPLES (50% overlap)
                    System.arraycopy(sampleBuffer, YAMNET_STEP_SAMPLES, sampleBuffer, 0, YAMNET_INPUT_SAMPLES - YAMNET_STEP_SAMPLES)
                    samplesAccumulated = YAMNET_INPUT_SAMPLES - YAMNET_STEP_SAMPLES
                }
            }
        }
    }

    private fun processInferenceWindow(waveform: FloatArray) {
        val startTime = System.currentTimeMillis()
        var screamScore = 0.0f
        var dominantScore = 0.0f
        var dominantLabel = "Normal Ambient"
        var generalDistress = 0.0f

        val interpreter = tfliteInterpreter
        if (isInitialized && interpreter != null) {
            try {
                // YAMNet expects [15600] float32 input and outputs [1, 521] float32
                val outputTensor = Array(1) { FloatArray(YAMNET_NUM_CLASSES) }

                synchronized(interpreterLock) {
                    interpreter.run(waveform, outputTensor)
                }
                val scores = outputTensor[0]

                // Extract relevant distress scores across all scream & crying classes
                val rawScream = scores.getOrElse(CLASS_SCREAMING) { 0.0f }
                val rawShout = scores.getOrElse(CLASS_SHOUT) { 0.0f }
                val rawYell = scores.getOrElse(CLASS_YELL) { 0.0f }
                val rawCrying = scores.getOrElse(CLASS_CRYING) { 0.0f }
                val rawBabyCry = scores.getOrElse(CLASS_BABY_CRY) { 0.0f }
                val rawWail = scores.getOrElse(CLASS_WAIL) { 0.0f }
                val rawGroan = scores.getOrElse(CLASS_GROAN) { 0.0f }
                val rawGasp = scores.getOrElse(CLASS_GASP) { 0.0f }

                // Find dominant class across all 521 classes
                var maxIdx = 0
                for (i in scores.indices) {
                    if (scores[i] > dominantScore) {
                        dominantScore = scores[i]
                        maxIdx = i
                    }
                }
                val isDominantDistress = maxIdx in DISTRESS_CLASS_NAMES.keys
                dominantLabel = DISTRESS_CLASS_NAMES[maxIdx] ?: "Acoustic Event ($maxIdx)"

                // Comprehensive distress acoustic aggregation: screams, crying, wailing, yelling, shouting
                val rawDistress = maxOf(
                    rawScream,
                    rawCrying,
                    rawWail,
                    rawYell * 0.90f,
                    rawShout * 0.85f,
                    rawBabyCry * 0.85f,
                    rawGroan * 0.75f,
                    rawGasp * 0.70f
                )

                val combinedDistress = if (isDominantDistress) {
                    maxOf(rawDistress, dominantScore)
                } else {
                    rawDistress
                }

                screamScore = combinedDistress
                generalDistress = combinedDistress
            } catch (e: Exception) {
                Log.e(TAG, "Inference execution error: ${e.message}")
            }
        } else {
            // Passive energy observation while model is loading; NEVER trigger false scream alarms
            var energySum = 0.0f
            for (s in waveform) energySum += s * s
            val avgEnergy = energySum / waveform.size
            dominantScore = (avgEnergy * 2.0f).coerceIn(0.0f, 0.35f)
            dominantLabel = if (avgEnergy > 0.10f) "Acoustic Activity" else "Normal Ambient"
            screamScore = 0.0f
            generalDistress = 0.0f
        }

        // Guard against NaN / Infinity
        if (screamScore.isNaN() || screamScore.isInfinite()) screamScore = 0.0f
        if (generalDistress.isNaN() || generalDistress.isInfinite()) generalDistress = 0.0f

        // Apply temporal smoothing (EMA: 60% current, 40% previous)
        smoothedScreamScore = ((smoothedScreamScore * 0.40f) + (screamScore * 0.60f)).coerceIn(0.0f, 1.0f)
        smoothedGeneralDistress = ((smoothedGeneralDistress * 0.40f) + (generalDistress * 0.60f)).coerceIn(0.0f, 1.0f)

        val latencyMs = System.currentTimeMillis() - startTime
        val isDistress = smoothedScreamScore >= generalDistressThreshold || smoothedGeneralDistress >= generalDistressThreshold

        val result = YamnetInferenceResult(
            dominantClass = dominantLabel,
            dominantConfidence = dominantScore,
            screamConfidence = smoothedScreamScore,
            isDistressAcoustic = isDistress,
            inferenceTimeMs = latencyMs,
            timestamp = System.currentTimeMillis()
        )

        // Dispatch general inference update
        synchronized(listeners) {
            for (l in listeners) {
                try {
                    l.onYamnetInference(result)
                } catch (e: Exception) {
                    Log.e(TAG, "Listener error: ${e.message}")
                }
            }
        }

        // FAST ESCALATION PATH: Immediate trigger if acoustic distress/crying/scream confidence exceeds emergency threshold
        if (smoothedScreamScore >= screamEmergencyThreshold || (isDistress && dominantScore >= screamEmergencyThreshold)) {
            Log.w(TAG, "!!! FAST ACOUSTIC DISTRESS ESCALATION TRIGGERED: Distress score = $smoothedScreamScore >= $screamEmergencyThreshold ($dominantLabel)")
            synchronized(listeners) {
                for (l in listeners) {
                    try {
                        l.onFastDistressEscalation(result)
                    } catch (e: Exception) {
                        Log.e(TAG, "Fast escalation listener error: ${e.message}")
                    }
                }
            }
        }
    }

    private fun loadModelFile(targetFile: java.io.File?, modelAssetPath: String): ByteBuffer {
        if (targetFile != null && targetFile.exists() && targetFile.length() > 0) {
            try {
                val inputStream = FileInputStream(targetFile)
                val fileChannel = inputStream.channel
                return fileChannel.map(FileChannel.MapMode.READ_ONLY, 0, targetFile.length())
                    .order(ByteOrder.nativeOrder())
            } catch (e: Exception) {
                Log.w(TAG, "File channel map failed for ${targetFile.absolutePath}: ${e.message}")
            }
        }
        return try {
            val fileDescriptor: AssetFileDescriptor = context.assets.openFd(modelAssetPath)
            val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
            val fileChannel = inputStream.channel
            val startOffset = fileDescriptor.startOffset
            val declaredLength = fileDescriptor.declaredLength
            fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
                .order(ByteOrder.nativeOrder())
        } catch (e: Exception) {
            Log.i(TAG, "openFd not available for $modelAssetPath; reading asset into direct ByteBuffer")
            context.assets.open(modelAssetPath).use { input ->
                val bytes = input.readBytes()
                val directBuffer = ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder())
                directBuffer.put(bytes)
                directBuffer.rewind()
                directBuffer
            }
        }
    }

    fun close() {
        synchronized(interpreterLock) {
            try {
                tfliteInterpreter?.close()
            } catch (_: Exception) {}
            tfliteInterpreter = null
            isInitialized = false
        }
    }
}
