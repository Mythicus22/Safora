package com.example.safora.data.local

import com.example.safora.data.model.Contact
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
            latitude = userLat + 0.0018,
            longitude = userLng + 0.0012,
            distanceMeters = 240,
            isAvailable = true,
            rating = 4.9f
        ),
        Responder(
            id = "r2",
            name = "Community Patrol Sunita",
            latitude = userLat - 0.0022,
            longitude = userLng + 0.0015,
            distanceMeters = 380,
            isAvailable = true,
            rating = 4.8f
        ),
        Responder(
            id = "r3",
            name = "Campus Guard Vikram",
            latitude = userLat + 0.0031,
            longitude = userLng - 0.0025,
            distanceMeters = 520,
            isAvailable = true,
            rating = 4.95f
        )
    )

    fun getSampleEvidence(incidentId: String = "INC-8921"): List<EvidenceSegment> = listOf(
        EvidenceSegment(
            id = "ev_seg_1",
            incidentId = incidentId,
            segmentNumber = 1,
            timestamp = System.currentTimeMillis() - 25000,
            fileSizeKb = 412,
            isEncrypted = true,
            isUploaded = true
        ),
        EvidenceSegment(
            id = "ev_seg_2",
            incidentId = incidentId,
            segmentNumber = 2,
            timestamp = System.currentTimeMillis() - 15000,
            fileSizeKb = 438,
            isEncrypted = true,
            isUploaded = false
        ),
        EvidenceSegment(
            id = "ev_seg_3",
            incidentId = incidentId,
            segmentNumber = 3,
            timestamp = System.currentTimeMillis() - 5000,
            fileSizeKb = 390,
            isEncrypted = true,
            isUploaded = false
        )
    )
}
