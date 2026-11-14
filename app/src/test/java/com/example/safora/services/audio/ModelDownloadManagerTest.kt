package com.example.safora.services.audio

import com.example.safora.services.audio.model.ModelDownloadItem
import com.example.safora.services.audio.model.ModelDownloadStatus
import com.example.safora.services.audio.model.SafetyModelType
import org.junit.Assert.*
import org.junit.Test

class ModelDownloadManagerTest {

    @Test
    fun testSafetyModelTypesDefinitions() {
        val types = SafetyModelType.values()
        assertEquals("Must define exactly 4 on-device safety models", 4, types.size)

        val yamnet = SafetyModelType.YAMNET
        assertEquals("yamnet", yamnet.id)
        assertTrue(yamnet.title.contains("YAMNet"))
        assertEquals("4.1 MB", yamnet.sizeText)

        val voskEn = SafetyModelType.VOSK_EN
        assertEquals("vosk_en", voskEn.id)
        assertTrue(voskEn.title.contains("Vosk English"))
        assertEquals("68 MB", voskEn.sizeText)

        val voskHi = SafetyModelType.VOSK_HI
        assertEquals("vosk_hi", voskHi.id)
        assertTrue(voskHi.title.contains("Vosk Hindi"))
        assertEquals("79 MB", voskHi.sizeText)

        val safetyClassifier = SafetyModelType.SAFETY_CLASSIFIER
        assertEquals("safety_classifier", safetyClassifier.id)
        assertTrue(safetyClassifier.title.contains("Threat"))
        assertEquals("1.2 MB", safetyClassifier.sizeText)
    }

    @Test
    fun testModelDownloadItemStateTransitions() {
        var item = ModelDownloadItem(
            type = SafetyModelType.YAMNET,
            status = ModelDownloadStatus.NOT_DOWNLOADED,
            progress = 0.0f
        )
        assertEquals(ModelDownloadStatus.NOT_DOWNLOADED, item.status)
        assertEquals(0.0f, item.progress, 0.001f)

        item = item.copy(status = ModelDownloadStatus.DOWNLOADING, progress = 0.55f)
        assertEquals(ModelDownloadStatus.DOWNLOADING, item.status)
        assertEquals(0.55f, item.progress, 0.001f)

        item = item.copy(status = ModelDownloadStatus.DOWNLOADED, progress = 1.0f)
        assertEquals(ModelDownloadStatus.DOWNLOADED, item.status)
        assertEquals(1.0f, item.progress, 0.001f)
    }
}
