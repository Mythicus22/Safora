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
    val triggerSource: String = "MANUAL", // "MANUAL", "VOICE_ML", "WEARABLE_BUTTON"
    val evidenceSegmentsCount: Int = 0,
    val alertsSentCount: Int = 0,
    val timeline: List<String> = emptyList()
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
    val sha256Hash: String = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
    val durationSeconds: Int = 5,
    val resolution: String = "640x480 (ESP32-CAM OV2640)"
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
    val timestamp: Long
)
