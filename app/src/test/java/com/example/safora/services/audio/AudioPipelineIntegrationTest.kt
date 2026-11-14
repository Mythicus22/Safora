package com.example.safora.services.audio

import com.example.safora.data.model.RiskDecisionResult
import com.example.safora.data.model.RiskLevel
import com.example.safora.data.model.SafetyClassificationResult
import com.example.safora.data.model.YamnetInferenceResult
import com.example.safora.services.audio.classifier.SafetyTextClassifier
import com.example.safora.services.audio.decision.RiskDecisionEngine
import com.example.safora.services.audio.decision.RiskDecisionListener
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class AudioPipelineIntegrationTest {

    private lateinit var riskEngine: RiskDecisionEngine
    private lateinit var textClassifier: SafetyTextClassifier
    private var lastEvaluatedDecision: RiskDecisionResult? = null
    private var emergencyTriggerCount = 0
    private var lastTriggerSource = ""

    @Before
    fun setUp() {
        riskEngine = RiskDecisionEngine(
            screamFastTrackThreshold = 0.72f,
            moderateAcousticThreshold = 0.50f,
            textHarmfulThreshold = 0.05f,
            emergencyCooldownMs = 60000L
        )
        emergencyTriggerCount = 0
        lastTriggerSource = ""

        riskEngine.addListener(object : RiskDecisionListener {
            override fun onRiskEvaluated(decision: RiskDecisionResult) {
                lastEvaluatedDecision = decision
            }

            override fun onEmergencyTriggerRequired(triggerSource: String, reason: String) {
                emergencyTriggerCount++
                lastTriggerSource = triggerSource
            }
        })

        textClassifier = SafetyTextClassifier()
    }

    @Test
    fun testIndependentScreamFastTrackBypassesSpeech() {
        // High confidence scream detection without any speech recognized
        val acousticResult = YamnetInferenceResult(
            dominantClass = "Screaming",
            dominantConfidence = 0.88f,
            screamConfidence = 0.88f,
            isDistressAcoustic = true,
            inferenceTimeMs = 30L
        )

        riskEngine.onAcousticUpdate(acousticResult)

        assertNotNull(lastEvaluatedDecision)
        assertEquals("Fast-track scream must immediately produce HIGH risk", RiskLevel.HIGH, lastEvaluatedDecision?.riskLevel)
        assertEquals(1, emergencyTriggerCount)
        assertEquals("AI_ACOUSTIC_SCREAM", lastTriggerSource)
    }

    @Test
    fun testMultiModalFusionAcousticPlusHarmfulSpeechTriggersEmergency() = runBlocking {
        // Shouting / Distress acoustic event with moderate confidence (0.55)
        val acousticResult = YamnetInferenceResult(
            dominantClass = "Shout",
            dominantConfidence = 0.55f,
            screamConfidence = 0.55f,
            isDistressAcoustic = true,
            inferenceTimeMs = 28L
        )
        riskEngine.onAcousticUpdate(acousticResult)

        // Moderate scream alone is only MEDIUM
        assertEquals(RiskLevel.MEDIUM, lastEvaluatedDecision?.riskLevel)
        assertEquals(0, emergencyTriggerCount)

        // Abusive / threatening speech detected
        val transcript = "get in the car right now or i will hurt you"
        val safetyResult = textClassifier.classifyTextContext(transcript)
        assertTrue("Transcript should be marked harmful", safetyResult.isHarmful)

        // Feed to decision engine
        riskEngine.onConversationUpdate(safetyResult)

        assertEquals("Multi-modal combination of shout + threat must trigger HIGH risk", RiskLevel.HIGH, lastEvaluatedDecision?.riskLevel)
        assertEquals(1, emergencyTriggerCount)
        assertEquals("AI_MULTIMODAL_FUSION", lastTriggerSource)
    }

    @Test
    fun testSuspiciousConversationWithoutAcousticDistressTriggersMediumCheckIn() = runBlocking {
        // Normal quiet ambient acoustic
        val acousticResult = YamnetInferenceResult(
            dominantClass = "Silence",
            dominantConfidence = 0.90f,
            screamConfidence = 0.0f,
            isDistressAcoustic = false,
            inferenceTimeMs = 15L
        )
        riskEngine.onAcousticUpdate(acousticResult)

        // Threatening phrase spoken quietly
        val transcript = "don't make any noise just give me your phone"
        val safetyResult = textClassifier.classifyTextContext(transcript)
        assertTrue(safetyResult.isHarmful)

        riskEngine.onConversationUpdate(safetyResult)

        assertEquals("Suspicious conversation alone must trigger MEDIUM risk for discreet check-in", RiskLevel.MEDIUM, lastEvaluatedDecision?.riskLevel)
        assertEquals(0, emergencyTriggerCount)
    }

    @Test
    fun testCooldownSuppressesDuplicateEmergencyTriggerWithinWindow() {
        val screamResult = YamnetInferenceResult(
            dominantClass = "Screaming",
            dominantConfidence = 0.95f,
            screamConfidence = 0.95f,
            isDistressAcoustic = true,
            inferenceTimeMs = 30L
        )

        // First trigger -> HIGH & Emergency triggered
        riskEngine.onAcousticUpdate(screamResult)
        assertEquals(1, emergencyTriggerCount)

        // Second trigger within 60s cooldown -> Suppressed
        riskEngine.onAcousticUpdate(screamResult)
        assertEquals("Subsequent emergency within cooldown must be suppressed", 1, emergencyTriggerCount)
    }

    @Test
    fun testResetCooldownAllowsImmediateSubsequentTrigger() {
        val screamResult = YamnetInferenceResult(
            dominantClass = "Screaming",
            dominantConfidence = 0.95f,
            screamConfidence = 0.95f,
            isDistressAcoustic = true,
            inferenceTimeMs = 30L
        )

        riskEngine.onAcousticUpdate(screamResult)
        assertEquals(1, emergencyTriggerCount)

        // Reset cooldown (e.g. user acknowledged or cleared incident)
        riskEngine.resetCooldown()

        // Immediate next trigger must now succeed
        riskEngine.onAcousticUpdate(screamResult)
        assertEquals(2, emergencyTriggerCount)
    }
}
