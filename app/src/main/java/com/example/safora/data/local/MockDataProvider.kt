package com.example.safora.data.local

import com.example.safora.data.model.DiscoveredBleDevice
import com.example.safora.data.model.EvidenceSegment
import com.example.safora.data.model.Responder

object MockDataProvider {
    // Zero hardcoded contacts - starts empty until user imports or adds
    fun getInitialContacts(): List<com.example.safora.data.model.Contact> = emptyList()

    fun getInitialResponders(userLat: Double = 28.6139, userLng: Double = 77.2090): List<Responder> = listOf(
        Responder(
            id = "r1",
            name = "Security Officer Deepak",
            role = "Patrol Unit A",
            latitude = userLat + 0.0018,
            longitude = userLng + 0.0012,
            distanceMeters = 240,
            isAvailable = true,
            rating = 4.9f,
            etaMinutes = 2
        ),
        Responder(
            id = "r2",
            name = "Community Patrol Sunita",
            role = "Citizen First Responder",
            latitude = userLat - 0.0022,
            longitude = userLng + 0.0015,
            distanceMeters = 380,
            isAvailable = true,
            rating = 4.8f,
            etaMinutes = 4
        ),
        Responder(
            id = "r3",
            name = "Campus Guard Vikram",
            role = "Gate Security",
            latitude = userLat + 0.0031,
            longitude = userLng - 0.0025,
            distanceMeters = 520,
            isAvailable = true,
            rating = 4.95f,
            etaMinutes = 5
        )
    )

    fun getMockNearbyBleDevices(): List<DiscoveredBleDevice> = listOf(
        DiscoveredBleDevice(
            name = "SAFORA_NECKLACE_01",
            address = "7C:1E:00:1B:6A:01",
            rssi = -54,
            isSaforaDevice = true
        ),
        DiscoveredBleDevice(
            name = "ESP32-CAM_SECURE",
            address = "30:AE:A4:C2:10:98",
            rssi = -68,
            isSaforaDevice = true
        )
    )

    fun getSampleEvidence(incidentId: String = "INC-8921"): List<EvidenceSegment> = emptyList()
}
