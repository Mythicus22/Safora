package com.example.safora.services.audio

import com.example.safora.services.audio.classifier.SafetyTextClassifier
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class SafetyTextClassifierTest {

    private lateinit var classifier: SafetyTextClassifier

    @Before
    fun setUp() {
        // Local offline fallback mode
        classifier = SafetyTextClassifier()
    }

    @Test
    fun testBenignConversationClassifiedAsBenign() = runBlocking {
        val transcript = "hey where are we meeting for dinner tonight let me know"
        val result = classifier.classifyTextContext(transcript)

        assertEquals("BENIGN", result.label)
        assertFalse("Benign text should not be harmful", result.isHarmful)
        assertTrue("Risk score should be below threshold 0.05", result.score < 0.05f)
        assertTrue("Should be local fallback", result.isLocalFallback)
    }

    @Test
    fun testClearHarmfulThreatExceedsThreshold() = runBlocking {
        val transcript = "shut up or i will kill you don't make a sound"
        val result = classifier.classifyTextContext(transcript)

        assertEquals("HARMFUL", result.label)
        assertTrue("Threatening text should be marked harmful", result.isHarmful)
        assertTrue("Risk score should exceed threshold 0.05", result.score >= 0.05f)
    }

    @Test
    fun testCoercionAndKidnappingKeywordsClassifiedHarmful() = runBlocking {
        val transcript = "get in the car right now and don't try to scream"
        val result = classifier.classifyTextContext(transcript)

        assertEquals("HARMFUL", result.label)
        assertTrue("Coercion/abduction attempt must be classified harmful", result.isHarmful)
        assertTrue(result.score >= 0.05f)
    }

    @Test
    fun testHindiDevanagariThreatsClassifiedHarmful() = runBlocking {
        val transcript = "बचाओ मुझे छोड़ दो मुझे"
        val result = classifier.classifyTextContext(transcript)

        assertEquals("HARMFUL", result.label)
        assertTrue("Hindi emergency plea must be marked harmful", result.isHarmful)
        assertTrue("Score must exceed 0.05 baseline threshold", result.score >= 0.05f)
    }

    @Test
    fun testHinglishThreatsClassifiedHarmful() = runBlocking {
        val transcript = "gadi mein baith mar dunga tujhe awaz mat nikal"
        val result = classifier.classifyTextContext(transcript)

        assertEquals("HARMFUL", result.label)
        assertTrue("Hinglish threat must be marked harmful", result.isHarmful)
        assertTrue(result.score >= 0.05f)
    }

    @Test
    fun testBenignFalsePositiveGuard() = runBlocking {
        // "help with homework" or "give me money for groceries" should not trigger emergency
        val transcript1 = "can someone please help me with my math homework"
        val result1 = classifier.classifyTextContext(transcript1)
        assertFalse("Homework help should not trigger harmful alert", result1.isHarmful)

        val transcript2 = "i need you to help me carry groceries tomorrow"
        val result2 = classifier.classifyTextContext(transcript2)
        assertFalse("Casual help discussion should not trigger harmful alert", result2.isHarmful)
    }

    @Test
    fun testEmptyOrShortTranscriptReturnsSafeDefault() = runBlocking {
        val result = classifier.classifyTextContext("")
        assertFalse(result.isHarmful)
        assertEquals(0.0f, result.score, 0.001f)
    }

    @Test
    fun testRemoteEndpointWithoutConsentUsesLocalEngine() = runBlocking {
        val remoteClassifier = SafetyTextClassifier()
        remoteClassifier.setRemoteEndpoint(
            url = "https://safety-api.safora.internal/classify",
            userConsentGranted = false // User has NOT given consent
        )
        val result = remoteClassifier.classifyTextContext("let me go please leave me alone")
        assertTrue(result.isHarmful)
        assertTrue("Should fallback to local engine when consent is not given", result.isLocalFallback)
    }
}
