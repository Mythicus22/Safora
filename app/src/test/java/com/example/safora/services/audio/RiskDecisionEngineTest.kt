package com.example.safora.services.audio

import com.example.safora.data.model.RiskDecisionResult
import com.example.safora.data.model.RiskLevel
import com.example.safora.data.model.SafetyClassificationResult
import com.example.safora.data.model.YamnetInferenceResult
import com.example.safora.services.audio.decision.RiskDecisionEngine
import com.example.safora.services.audio.decision.RiskDecisionListener
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class RiskDecisionEngineTest {

    private lateinit var decisionEngine: RiskDecisionEngine
    private var lastDecision: RiskDecisionResult? = null
    private var emergencyTriggerCount = 0
    private var lastTriggerSource = ""

    @Before
    fun setUp() {
        decisionEngine = RiskDecisionEngine(
            screamFastTrackThreshold = 0.72f,
            moderateAcousticThreshold = 0.50f,
            textHarmfulThreshold = 0.05f,
            emergencyCooldownMs = 1000L
        )
        emergencyTriggerCount = 0
        lastTriggerSource = ""

        decisionEngine.addListener(object : RiskDecisionListener {
            override fun onRiskEvaluated(decision: RiskDecisionResult) {
                lastDecision = decision
            }

            override fun onEmergencyTriggerRequired(triggerSource: String, reason: String) {
                emergencyTriggerCount++
                lastTriggerSource = triggerSource
            }
        })
    }

    @Test
    fun testNormalSpeechRemainsLowRisk() {
        decisionEngine.onAcousticUpdate(
            YamnetInferenceResult("Speech", 0.95f, 0.05f, false, 25L)
        )
        decisionEngine.onConversationUpdate(
            SafetyClassificationResult("BENIGN", 0.01f, 0.05f, false)
        )

        assertNotNull(lastDecision)
        assertEquals(RiskLevel.LOW, lastDecision?.riskLevel)
        assertEquals(0, emergencyTriggerCount)
    }

    @Test
    fun testBackgroundNoiseFloorCalm() {
        decisionEngine.onAcousticUpdate(
            YamnetInferenceResult("Silence / Ambient", 0.99f, 0.02f, false, 15L)
        )
        assertEquals(RiskLevel.LOW, lastDecision?.riskLevel)
        assertEquals(0, emergencyTriggerCount)
    }

    @Test
    fun testScreamFastTrackEscalation() {
        // Severe scream (0.85 > 0.72) must trigger HIGH emergency independently without speech
        decisionEngine.onAcousticUpdate(
            YamnetInferenceResult("Screaming", 0.88f, 0.85f, true, 30L)
        )

        assertNotNull(lastDecision)
        assertEquals(RiskLevel.HIGH, lastDecision?.riskLevel)
        assertEquals(1, emergencyTriggerCount)
        assertEquals("AI_ACOUSTIC_SCREAM", lastTriggerSource)
    }

    @Test
    fun testMultiModalFusionEmergency() {
        // Moderate scream (0.55 >= 0.50) alone is only MEDIUM
        decisionEngine.onAcousticUpdate(
            YamnetInferenceResult("Yell", 0.60f, 0.55f, true, 28L)
        )
        assertEquals(RiskLevel.MEDIUM, lastDecision?.riskLevel)
        assertEquals(0, emergencyTriggerCount)

        // Correlated with harmful conversation (0.88 >= 0.05) -> Escalates to HIGH emergency!
        decisionEngine.onConversationUpdate(
            SafetyClassificationResult("HARMFUL", 0.88f, 0.05f, true)
        )

        assertEquals(RiskLevel.HIGH, lastDecision?.riskLevel)
        assertEquals(1, emergencyTriggerCount)
        assertEquals("AI_MULTIMODAL_FUSION", lastTriggerSource)
    }

    @Test
    fun testModerateScreamMediumCheckIn() {
        decisionEngine.onAcousticUpdate(
            YamnetInferenceResult("Crying, sobbing", 0.58f, 0.52f, true, 25L)
        )
        assertEquals(RiskLevel.MEDIUM, lastDecision?.riskLevel)
        assertEquals(0, emergencyTriggerCount)
    }

    @Test
    fun testDuplicateTriggerCooldown() {
        // First emergency trigger
        decisionEngine.onAcousticUpdate(
            YamnetInferenceResult("Screaming", 0.90f, 0.90f, true, 30L)
        )
        assertEquals(1, emergencyTriggerCount)

        // Immediate subsequent scream within cooldown period must not re-trigger emergency dispatch
        decisionEngine.onAcousticUpdate(
            YamnetInferenceResult("Screaming", 0.92f, 0.92f, true, 30L)
        )
        assertEquals(1, emergencyTriggerCount) // Still 1!
    }
}
