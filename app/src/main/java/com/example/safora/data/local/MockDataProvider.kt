package com.example.safora.data.local

import com.example.safora.data.model.Contact
import com.example.safora.data.model.DiscoveredBleDevice
import com.example.safora.data.model.EvidenceSegment
import com.example.safora.data.model.Responder

object MockDataProvider {
    fun getInitialContacts(): List<Contact> = listOf(
        Contact(
            id = "c1",
            name = "Ravi Sharma (Father)",
            phone = "+91 98111 22334",
            relationship = "Parent",
            isEmergencyContact = true
        ),
        Contact(
            id = "c2",
            name = "Priya Verma (Sister)",
            phone = "+91 98222 33445",
            relationship = "Sibling",
            isEmergencyContact = true
        ),
        Contact(
            id = "c3",
            name = "Aman Gupta (Friend)",
            phone = "+91 98333 44556",
            relationship = "Friend",
            isEmergencyContact = false
        )
    )

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
        ),
        DiscoveredBleDevice(
            name = "Generic BLE Tracker",
            address = "DC:2C:26:A1:9F:33",
            rssi = -82,
            isSaforaDevice = false
        )
    )

    fun getSampleEvidence(incidentId: String = "INC-8921"): List<EvidenceSegment> = listOf(
        EvidenceSegment(
            id = "ev_seg_1",
            incidentId = incidentId,
            segmentNumber = 1,
            timestamp = System.currentTimeMillis() - 45000,
            fileSizeKb = 412,
            isEncrypted = true,
            isUploaded = true,
            sha256Hash = "8a35f79a9c8b74709d293847e1293a9e992b8d009b91c28c36b8e8f828192801"
        ),
        EvidenceSegment(
            id = "ev_seg_2",
            incidentId = incidentId,
            segmentNumber = 2,
            timestamp = System.currentTimeMillis() - 25000,
            fileSizeKb = 438,
            isEncrypted = true,
            isUploaded = false,
            sha256Hash = "c24f910a30129bc8187e10a82b8109d774fba73b610368181a91bc747518923a"
        ),
        EvidenceSegment(
            id = "ev_seg_3",
            incidentId = incidentId,
            segmentNumber = 3,
            timestamp = System.currentTimeMillis() - 10000,
            fileSizeKb = 390,
            isEncrypted = true,
            isUploaded = false,
            sha256Hash = "592f8019aa14bc8190209489f029a8c17042894101e48bc8294901bc09129841"
        )
    )
}
