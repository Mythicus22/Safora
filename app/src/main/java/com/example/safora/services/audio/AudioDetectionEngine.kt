package com.example.safora.services.audio

import android.content.Context
import android.util.Log
import com.example.safora.core.crypto.EvidenceCipher
import com.example.safora.data.model.AudioDetectionResult
import com.example.safora.data.model.EvidenceSegment
import com.example.safora.data.model.RiskDecisionResult
import com.example.safora.data.model.RiskLevel
import com.example.safora.data.model.SafetyClassificationResult
import com.example.safora.data.model.SpeechTranscriptResult
import com.example.safora.data.model.YamnetInferenceResult
import com.example.safora.data.repository.SaforaRepository
import com.example.safora.services.audio.capture.SharedAudioCaptureEngine
import com.example.safora.services.audio.classifier.SafetyClassifierListener
import com.example.safora.services.audio.classifier.SafetyTextClassifier
import com.example.safora.services.audio.decision.RiskDecisionEngine
import com.example.safora.services.audio.decision.RiskDecisionListener
import com.example.safora.services.audio.speech.VoskSpeechRecognitionEngine
import com.example.safora.services.audio.speech.VoskTranscriptListener
import com.example.safora.services.audio.vad.VadListener
import com.example.safora.services.audio.vad.VoiceActivityDetector
import com.example.safora.services.audio.yamnet.YamnetDetectionEngine
import com.example.safora.services.audio.yamnet.YamnetDetectionListener
import kotlinx.coroutines.*
import java.io.File

interface AudioDetectionEngine {
    fun startAnalysis(onDetection: (AudioDetectionResult) -> Unit)
    fun stopAnalysis()
}

/**
 * Production implementation of the complete multi-stage offline-first audio detection pipeline:
 * 1. SharedAudioCaptureEngine (16 kHz Mono PCM via AudioRecord)
 * 2. VoiceActivityDetector (Energy & Zero-Crossing VAD)
 * 3. YamnetDetectionEngine (Continuous Acoustic Scream & Distress via TFLite)
 * 4. VoskSpeechRecognitionEngine (Offline English STT)
 * 5. SafetyTextClassifier (Model 4: DistilBERT / FullSafety Conversation Safety)
 * 6. RiskDecisionEngine (Multi-modal fusion, LOW/MEDIUM/HIGH risk, anti-duplicate cooldowns)
 */
class RealAudioPipelineEngine(
    private val context: Context
) : AudioDetectionEngine {

    companion object {
        private const val TAG = "RealAudioPipeline"
    }

    private val repository = SaforaRepository.instance
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // Subsystems
    private val captureEngine = SharedAudioCaptureEngine.getInstance(context)
    private val vad = VoiceActivityDetector()
    private val yamnetEngine = YamnetDetectionEngine(context)
    private val voskEngine = VoskSpeechRecognitionEngine(context)
    private val textClassifier = SafetyTextClassifier(context)
    private val decisionEngine = RiskDecisionEngine()

    private var callback: ((AudioDetectionResult) -> Unit)? = null
    private var isRunning = false

    override fun startAnalysis(onDetection: (AudioDetectionResult) -> Unit) {
        if (isRunning) return
        isRunning = true
        callback = onDetection

        Log.i(TAG, "Initializing RealAudioPipelineEngine...")

        // Synchronize sensitivity settings from repository
        val sensitivity = repository.audioSensitivity.value
        vad.setSensitivity(sensitivity)
        yamnetEngine.setSensitivity(sensitivity)
        decisionEngine.updateSensitivity(sensitivity)

        // 1. Initialize TFLite YAMNet model
        val yamnetReady = yamnetEngine.initialize()
        if (!yamnetReady) {
            repository.logEmergencyTimeline("WARNING: YAMNet TFLite model failed to load. Using fallback acoustic detector.")
        }

        // 2. Initialize Vosk English Speech Model in background
        scope.launch {
            val voskReady = voskEngine.initializeAsync()
            if (!voskReady) {
                repository.logEmergencyTimeline("WARNING: Vosk English speech model failed to initialize.")
            }
        }

        // 3. Connect YAMNet & VAD to the shared audio capture stream
        captureEngine.addListener("yamnet", yamnetEngine)
        captureEngine.addListener("vad", vad)

        // 4. Connect VAD to Vosk Speech Engine (activates STT only when speech detected)
        vad.addListener(voskEngine)
        vad.addListener(object : VadListener {
            override fun onSpeechStateChanged(isSpeechActive: Boolean, rmsDbfs: Float) {
                repository.updateAudioDiagnostics { diag ->
                    diag.copy(
                        isAudioCaptureActive = isRunning,
                        isVadSpeechActive = isSpeechActive,
                        currentRmsDbfs = rmsDbfs,
                        lastUpdatedTimestamp = System.currentTimeMillis()
                    )
                }
            }

            override fun onSpeechAudioFrame(pcmData: ShortArray, readSize: Int) {}

            override fun onAudioEnergyTelemetry(rmsDbfs: Float, noiseFloorDbfs: Float, isSpeech: Boolean) {
                repository.updateAudioDiagnostics { diag ->
                    diag.copy(
                        isAudioCaptureActive = isRunning,
                        isVadSpeechActive = isSpeech,
                        currentRmsDbfs = rmsDbfs,
                        noiseFloorDbfs = noiseFloorDbfs,
                        lastUpdatedTimestamp = System.currentTimeMillis()
                    )
                }
            }
        })

        // 5. Connect YAMNet detections to Risk Decision Engine
        yamnetEngine.addListener(object : YamnetDetectionListener {
            override fun onYamnetInference(result: YamnetInferenceResult) {
                decisionEngine.onAcousticUpdate(result)
                repository.updateAudioDiagnostics { diag ->
                    diag.copy(
                        yamnetDominantClass = result.dominantClass,
                        yamnetDominantConfidence = result.dominantConfidence,
                        yamnetScreamConfidence = result.screamConfidence,
                        isScreamFastTrackFired = result.screamConfidence >= 0.72f,
                        totalInferencesCount = diag.totalInferencesCount + 1,
                        lastInferenceLatencyMs = result.inferenceTimeMs,
                        lastUpdatedTimestamp = System.currentTimeMillis()
                    )
                }
            }

            override fun onFastDistressEscalation(result: YamnetInferenceResult) {
                repository.logEmergencyTimeline("FAST ESCALATION: Severe acoustic distress scream (${(result.screamConfidence * 100).toInt()}%)")
            }
        })

        // 6. Connect Vosk Speech Transcripts to Model 4 Safety Classifier
        voskEngine.addListener(object : VoskTranscriptListener {
            override fun onNewTranscriptSentence(result: SpeechTranscriptResult) {
                repository.logEmergencyTimeline("Speech Transcript [${result.language}]: \"${result.text}\"")
                repository.updateAudioDiagnostics { diag ->
                    diag.copy(
                        latestTranscriptWord = result.text,
                        detectedLanguage = result.language,
                        rollingTranscriptBuffer = voskEngine.getRollingContext(),
                        lastUpdatedTimestamp = System.currentTimeMillis()
                    )
                }
                scope.launch {
                    val classification = textClassifier.classifyTextContext(result.text)
                    decisionEngine.onConversationUpdate(classification)
                }
            }

            override fun onRollingContextUpdated(fullContext: String) {
                repository.updateAudioDiagnostics { diag ->
                    diag.copy(
                        rollingTranscriptBuffer = fullContext,
                        lastUpdatedTimestamp = System.currentTimeMillis()
                    )
                }
                scope.launch {
                    val classification = textClassifier.classifyTextContext(fullContext)
                    decisionEngine.onConversationUpdate(classification)
                }
            }
        })

        // 7. Connect Model 4 Classifications to Emergency Timeline
        textClassifier.addListener(object : SafetyClassifierListener {
            override fun onSafetyClassification(result: SafetyClassificationResult) {
                if (result.isHarmful) {
                    repository.logEmergencyTimeline("Model 4 WARNING: Harmful conversation context detected (Score: ${(result.score * 100).toInt()}%)")
                }
                repository.updateAudioDiagnostics { diag ->
                    diag.copy(
                        safetyLabel = result.label,
                        safetyRiskScore = result.score,
                        safetyThreshold = result.threshold,
                        lastUpdatedTimestamp = System.currentTimeMillis()
                    )
                }
            }
        })

        // 8. Connect Risk Decision Engine to UI & Emergency Dispatch Workflow
        decisionEngine.addListener(object : RiskDecisionListener {
            override fun onRiskEvaluated(decision: RiskDecisionResult) {
                repository.updateAudioDiagnostics { diag ->
                    diag.copy(
                        currentRiskLevel = decision.riskLevel,
                        riskDecisionReason = decision.reason,
                        lastUpdatedTimestamp = System.currentTimeMillis()
                    )
                }
                val detectionResult = AudioDetectionResult(
                    detectedClass = decision.reason,
                    confidence = decision.compositeScore,
                    timestamp = decision.timestamp,
                    riskLevel = decision.riskLevel,
                    details = "Acoustic: ${(decision.yamnetScore * 100).toInt()}% | Safety: ${(decision.conversationScore * 100).toInt()}%"
                )

                callback?.invoke(detectionResult)
                repository.updateAudioDetection(detectionResult)

                when (decision.riskLevel) {
                    RiskLevel.MEDIUM -> {
                        repository.logEmergencyTimeline("RISK MEDIUM: ${decision.reason}")
                    }
                    RiskLevel.HIGH -> {
                        repository.logEmergencyTimeline("RISK HIGH: Emergency conditions met! Reason: ${decision.reason}")
                    }
                    RiskLevel.LOW -> {}
                }
            }

            override fun onEmergencyTriggerRequired(triggerSource: String, reason: String) {
                Log.w(TAG, "EMERGENCY TRIGGER DISPATCHED: $triggerSource ($reason)")

                // Snapshot last 10s of raw audio from RAM buffer and encrypt as evidence
                saveRollingAudioEvidenceSnapshot()

                // Trigger complete autonomous emergency workflow
                repository.confirmSos(triggerSource)
            }
        })

        // 9. Start microphone recording
        val captureSuccess = captureEngine.startCapture { errorMsg ->
            repository.logEmergencyTimeline("AUDIO ERROR: $errorMsg")
        }

        if (!captureSuccess) {
            Log.e(TAG, "Failed to start shared audio capture")
        }
    }

    override fun stopAnalysis() {
        if (!isRunning) return
        isRunning = false
        callback = null

        captureEngine.removeListener("yamnet")
        captureEngine.removeListener("vad")
        captureEngine.stopCapture()

        yamnetEngine.close()
        voskEngine.close()

        Log.i(TAG, "RealAudioPipelineEngine stopped.")
    }

    private fun saveRollingAudioEvidenceSnapshot() {
        scope.launch(Dispatchers.IO) {
            try {
                val incId = repository.currentIncident.value.id.ifBlank { "INC-${System.currentTimeMillis().toString().takeLast(6)}" }
                val cacheDir = context.cacheDir
                val tempWav = File(cacheDir, "temp_audio_snapshot_$incId.wav")

                if (captureEngine.exportRollingAudioSnapshot(tempWav)) {
                    val rawBytes = tempWav.readBytes()
                    tempWav.delete()

                    // Encrypt audio snapshot with AES-256-GCM
                    val encryptedAudio = EvidenceCipher.encrypt(rawBytes)
                    val encFile = File(cacheDir, "evidence_${incId}_audio.enc")
                    encFile.writeBytes(encryptedAudio)

                    val segment = EvidenceSegment(
                        id = "ev_${incId}_audio",
                        incidentId = incId,
                        segmentNumber = 0,
                        timestamp = System.currentTimeMillis(),
                        fileSizeKb = (encryptedAudio.size / 1024).coerceAtLeast(1),
                        isEncrypted = true,
                        isUploaded = false,
                        localUri = encFile.absolutePath,
                        sha256Hash = EvidenceCipher.hashSha256(encryptedAudio),
                        durationSeconds = 10,
                        resolution = "16 kHz Mono Audio Snapshot (RAM)"
                    )
                    repository.addEvidenceSegment(segment)
                    repository.logEmergencyTimeline("Saved & encrypted 10s audio RAM evidence snapshot (${encryptedAudio.size / 1024} KB)")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error saving audio snapshot evidence: ${e.message}")
            }
        }
    }
}

/**
 * Interactive Simulation Engine for live demos, physical device testing, and edge case evaluation.
 */
class AudioSimulationEngine : AudioDetectionEngine {
    private var isRunning = false
    private var callback: ((AudioDetectionResult) -> Unit)? = null

    override fun startAnalysis(onDetection: (AudioDetectionResult) -> Unit) {
        isRunning = true
        callback = onDetection
    }

    override fun stopAnalysis() {
        isRunning = false
        callback = null
    }

    fun simulateDistressTrigger() {
        callback?.invoke(
            AudioDetectionResult(
                detectedClass = "Scream / Severe Acoustic Distress",
                confidence = 0.94f,
                timestamp = System.currentTimeMillis(),
                riskLevel = RiskLevel.HIGH,
                details = "Simulated high-confidence acoustic scream"
            )
        )
    }

    fun simulateNormalSpeech() {
        callback?.invoke(
            AudioDetectionResult(
                detectedClass = "Normal Speech / Ambient Calm",
                confidence = 0.98f,
                timestamp = System.currentTimeMillis(),
                riskLevel = RiskLevel.LOW,
                details = "Simulated benign conversation"
            )
        )
    }

    fun simulatePhysicalImpact() {
        callback?.invoke(
            AudioDetectionResult(
                detectedClass = "Sudden High-G Acoustic Impact / Thud",
                confidence = 0.89f,
                timestamp = System.currentTimeMillis(),
                riskLevel = RiskLevel.HIGH,
                details = "Simulated sudden blunt impact sound"
            )
        )
    }

    fun simulateHarmfulConversation() {
        callback?.invoke(
            AudioDetectionResult(
                detectedClass = "Threatening / Abusive Speech Context",
                confidence = 0.91f,
                timestamp = System.currentTimeMillis(),
                riskLevel = RiskLevel.HIGH,
                details = "Model 4 Harmful Text: \"Don't scream, let me in\""
            )
        )
    }
}
