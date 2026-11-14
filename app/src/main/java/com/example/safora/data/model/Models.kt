package com.example.safora.data.model

data class User(
    val name: String = "Jane Doe",
    val phone: String = "+91 98765 43210",
    val bloodGroup: String = "O+",
    val medicalNotes: String = "Asthma inhaler in bag",
    val isOnboarded: Boolean = true
)

enum class DeviceConnectionState {
    DISCONNECTED,
    SCANNING,
    CONNECTING,
    CONNECTED
}

data class WearableDevice(
    val name: String = "SAFORA_DEVICE",
    val address: String = "AA:BB:CC:DD:EE:01",
    val state: DeviceConnectionState = DeviceConnectionState.DISCONNECTED,
    val batteryLevel: Int = 85,
    val isRecording: Boolean = false,
    val lastPingMs: Long = 0L
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
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val state: IncidentState = IncidentState.IDLE,
    val triggerSource: String = "MANUAL", // "MANUAL", "VOICE_ML", "WEARABLE_BUTTON"
    val evidenceSegmentsCount: Int = 0,
    val alertsSentCount: Int = 0
)

data class EvidenceSegment(
    val id: String,
    val incidentId: String,
    val segmentNumber: Int,
    val timestamp: Long,
    val fileSizeKb: Int,
    val isEncrypted: Boolean = true,
    val isUploaded: Boolean = false,
    val localUri: String? = null,
    val previewBase64: String? = null
)

data class Contact(
    val id: String,
    val name: String,
    val phone: String,
    val relationship: String,
    val isEmergencyContact: Boolean = true
)

data class Responder(
    val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val distanceMeters: Int,
    val isAvailable: Boolean = true,
    val rating: Float = 4.9f
)

data class AudioDetectionResult(
    val detectedClass: String,
    val confidence: Float,
    val timestamp: Long
)
