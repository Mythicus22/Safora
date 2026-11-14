package com.example.safora.data.model

data class User(
    val name: String = "",
    val phone: String = "",
    val bloodGroup: String = "O+",
    val medicalNotes: String = "",
    val isOnboarded: Boolean = false
)

enum class DeviceConnectionState {
    DISCONNECTED,
    SCANNING,
    CONNECTING,
    CONNECTED
}

enum class HardwareErrorState {
    NONE,
    CAMERA_ERROR,
    SD_CARD_ERROR,
    STORAGE_FULL,
    DISCONNECTED
}

data class DiscoveredBleDevice(
    val name: String,
    val address: String,
    val rssi: Int,
    val isSaforaDevice: Boolean = true
)

data class WearableDevice(
    val name: String = "SAFORA_DEVICE",
    val address: String = "7C:1E:00:1B:6A:01",
    val state: DeviceConnectionState = DeviceConnectionState.DISCONNECTED,
    val batteryLevel: Int = 85,
    val isRecording: Boolean = false,
    val lastPingMs: Long = 0L,
    val hardwareError: HardwareErrorState = HardwareErrorState.NONE,
    val consoleLogs: List<String> = listOf("System ready. Bluetooth Low Energy stack initialized.")
)

enum class IncidentState {
    IDLE,
    POSSIBLE_DISTRESS,
    VERIFYING,
    CANCELLED,
    CONFIRMED_SOS,
    ALERTS_SENT,
    EVIDENCE_SYNCING,
    RESOLVED
}

data class Incident(
    val id: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val latitude: Double = 28.6139,
    val longitude: Double = 77.2090,
    val state: IncidentState = IncidentState.IDLE,
    val triggerSource: String = "MANUAL",
    val evidenceSegmentsCount: Int = 0,
    val alertsSentCount: Int = 0,
    val timeline: List<String> = emptyList(),
    val encryptionKeyHex: String = "",
    val thresholdRequired: Int = 1,
    val totalSharesCount: Int = 1
)

data class EvidenceSegment(
    val id: String,
    val incidentId: String,
    val segmentNumber: Int,
    val timestamp: Long,
    val fileSizeKb: Int,
    val isEncrypted: Boolean = true,
    val isUploaded: Boolean = false,
    val cloudUrl: String? = null,
    val localUri: String? = null,
    val sha256Hash: String = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
    val durationSeconds: Int = 5,
    val resolution: String = "640x480 (ESP32-CAM OV2640)"
)

data class Contact(
    val id: String,
    val name: String,
    val phone: String,
    val relationship: String,
    val isEmergencyContact: Boolean = true,
    val keyShareHex: String = "",
    val hasApprovedAccess: Boolean = false
)

data class Responder(
    val id: String,
    val name: String,
    val role: String = "Campus Security",
    val latitude: Double,
    val longitude: Double,
    val distanceMeters: Int,
    val isAvailable: Boolean = true,
    val rating: Float = 4.9f,
    val etaMinutes: Int = 3
)

data class AudioDetectionResult(
    val detectedClass: String,
    val confidence: Float,
    val timestamp: Long,
    val riskLevel: RiskLevel = RiskLevel.LOW,
    val details: String = ""
)

enum class RiskLevel {
    LOW,
    MEDIUM,
    HIGH
}

data class YamnetInferenceResult(
    val dominantClass: String,
    val dominantConfidence: Float,
    val screamConfidence: Float,
    val isDistressAcoustic: Boolean,
    val inferenceTimeMs: Long,
    val timestamp: Long = System.currentTimeMillis()
)

data class SpeechTranscriptResult(
    val text: String,
    val isFinal: Boolean,
    val language: String = "EN", // "EN" or "HI"
    val timestamp: Long = System.currentTimeMillis()
)

data class SafetyClassificationResult(
    val label: String,             // "BENIGN" or "HARMFUL"
    val score: Float,             // Raw probability / confidence
    val threshold: Float = 0.05f, // Baseline threshold from safora_metadata.json
    val isHarmful: Boolean,
    val isLocalFallback: Boolean = false,
    val latencyMs: Long = 0L,
    val timestamp: Long = System.currentTimeMillis()
)

data class RiskDecisionResult(
    val riskLevel: RiskLevel,
    val compositeScore: Float,
    val reason: String,
    val yamnetScore: Float,
    val conversationScore: Float,
    val timestamp: Long = System.currentTimeMillis(),
    val triggeredEmergency: Boolean = false
)

data class AudioPipelineDiagnostics(
    val isAudioCaptureActive: Boolean = false,
    val isVadSpeechActive: Boolean = false,
    val currentRmsDbfs: Float = -90.0f,
    val noiseFloorDbfs: Float = -60.0f,
    val yamnetDominantClass: String = "Standby",
    val yamnetDominantConfidence: Float = 0.0f,
    val yamnetScreamConfidence: Float = 0.0f,
    val isScreamFastTrackFired: Boolean = false,
    val latestTranscriptWord: String = "",
    val detectedLanguage: String = "EN", // "EN" or "HI"
    val rollingTranscriptBuffer: String = "",
    val safetyLabel: String = "BENIGN",
    val safetyRiskScore: Float = 0.0f,
    val safetyThreshold: Float = 0.05f,
    val currentRiskLevel: RiskLevel = RiskLevel.LOW,
    val riskDecisionReason: String = "Waiting for microphone audio...",
    val totalInferencesCount: Long = 0L,
    val lastInferenceLatencyMs: Long = 0L,
    val lastUpdatedTimestamp: Long = System.currentTimeMillis()
)
