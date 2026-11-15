package com.example.safora.services.audio.decision

import android.util.Log
import com.example.safora.data.model.RiskDecisionResult
import com.example.safora.data.model.RiskLevel
import com.example.safora.data.model.SafetyClassificationResult
import com.example.safora.data.model.YamnetInferenceResult
import java.util.concurrent.CopyOnWriteArrayList

interface RiskDecisionListener {
    fun onRiskEvaluated(decision: RiskDecisionResult)
    fun onEmergencyTriggerRequired(triggerSource: String, reason: String)
}

/**
 * Risk Decision Engine combining multi-stage AI inputs:
 * 1. Continuous YAMNet acoustic scream/distress detection.
 * 2. Model 4 suspicious/abusive text classification.
 * 3. Temporal smoothing and consecutive prediction history.
 *
 * Implements LOW / MEDIUM / HIGH risk levels, false-positive protection,
 * duplicate-trigger cooldowns, and an independent fast-track for severe screams.
 */
class RiskDecisionEngine(
    private var screamFastTrackThreshold: Float = 0.28f,
    private var moderateAcousticThreshold: Float = 0.18f,
    private var textHarmfulThreshold: Float = 0.05f,
    private val emergencyCooldownMs: Long = 45000L // 45 seconds cooldown to prevent duplicate triggers
) {

    companion object {
        private const val TAG = "RiskDecisionEngine"
    }

    private var latestAcousticResult: YamnetInferenceResult? = null
    private var latestTextResult: SafetyClassificationResult? = null

    // Rolling history of recent predictions
    private val recentAcousticHistory = mutableListOf<YamnetInferenceResult>()
    private val recentTextHistory = mutableListOf<SafetyClassificationResult>()
    private val historyLock = Any()

    private var lastEmergencyTriggerTimeMs = 0L
    private val listeners = CopyOnWriteArrayList<RiskDecisionListener>()

    fun addListener(listener: RiskDecisionListener) {
        listeners.add(listener)
    }

    fun removeListener(listener: RiskDecisionListener) {
        listeners.remove(listener)
    }

    fun updateSensitivity(level: String) {
        when (level.lowercase()) {
            "high" -> {
                screamFastTrackThreshold = 0.20f
                moderateAcousticThreshold = 0.14f
            }
            "low" -> {
                screamFastTrackThreshold = 0.40f
                moderateAcousticThreshold = 0.28f
            }
            else -> {
                screamFastTrackThreshold = 0.28f
                moderateAcousticThreshold = 0.18f
            }
        }
        Log.d(TAG, "Risk Decision Engine thresholds updated ($level): FastTrack=$screamFastTrackThreshold, Moderate=$moderateAcousticThreshold")
    }

    /**
     * Called whenever YAMNet finishes a 0.975s inference window.
     */
    fun onAcousticUpdate(result: YamnetInferenceResult) {
        latestAcousticResult = result
        val now = System.currentTimeMillis()

        synchronized(historyLock) {
            recentAcousticHistory.add(result)
            recentAcousticHistory.removeAll { (now - it.timestamp) > 20000L } // 20s window
        }

        // Check for FAST ACOUSTIC ESCALATION (Scream / Crying alone can trigger HIGH risk immediately)
        if (result.screamConfidence >= screamFastTrackThreshold || (result.isDistressAcoustic && result.dominantConfidence >= screamFastTrackThreshold)) {
            evaluateDecision(
                forcedHigh = true,
                forcedReason = "Severe distress voice detected (${result.dominantClass}, ${(result.screamConfidence * 100).toInt()}%)"
            )
            return
        }

        evaluateDecision()
    }

    /**
     * Called whenever Model 4 produces a safety score on recent transcript context.
     */
    fun onConversationUpdate(result: SafetyClassificationResult) {
        latestTextResult = result
        val now = System.currentTimeMillis()

        synchronized(historyLock) {
            recentTextHistory.add(result)
            recentTextHistory.removeAll { (now - it.timestamp) > 30000L } // 30s window
        }

        evaluateDecision()
    }

    private fun evaluateDecision(forcedHigh: Boolean = false, forcedReason: String? = null) {
        val now = System.currentTimeMillis()
        val acoustic = latestAcousticResult
        val text = latestTextResult

        val screamScore = acoustic?.screamConfidence ?: 0.0f
        val textScore = text?.score ?: 0.0f

        var level = RiskLevel.LOW
        var reason = "Normal ambient environment."
        var compositeScore = 0.0f
        var triggerEmergency = false

        if (forcedHigh) {
            level = RiskLevel.HIGH
            compositeScore = screamScore
            reason = forcedReason ?: "Fast emergency escalation triggered."
            triggerEmergency = true
        } else {
            // Count consecutive harmful text events within rolling window
            val consecutiveHarmfulCount: Int
            synchronized(historyLock) {
                consecutiveHarmfulCount = recentTextHistory.count { it.isHarmful }
            }

            // Decision Logic
            when {
                // Rule 1: Multi-Modal Fusion (Moderate Acoustic Scream + Harmful Language)
                screamScore >= moderateAcousticThreshold && (text?.isHarmful == true) -> {
                    level = RiskLevel.HIGH
                    compositeScore = (screamScore * 0.6f + textScore * 0.4f).coerceIn(0.0f, 1.0f)
                    reason = "Multi-modal distress confirmed: Acoustic distress (${(screamScore * 100).toInt()}%) correlated with harmful text (${(textScore * 100).toInt()}%)"
                    triggerEmergency = true
                }

                // Rule 1.5: Sustained Acoustic Distress (2 consecutive frames of crying / screaming)
                screamScore >= moderateAcousticThreshold && recentAcousticHistory.takeLast(2).count { it.screamConfidence >= moderateAcousticThreshold || it.isDistressAcoustic } >= 2 -> {
                    level = RiskLevel.HIGH
                    compositeScore = screamScore
                    reason = "Sustained acoustic distress detected (${acoustic?.dominantClass ?: "Distress voices"})"
                    triggerEmergency = true
                }

                // Rule 2: Repeated Persistent Threat Context (3 consecutive harmful classifications)
                consecutiveHarmfulCount >= 3 && textScore >= 0.65f -> {
                    level = RiskLevel.HIGH
                    compositeScore = textScore
                    reason = "Persistent hostile/coercive conversation detected across consecutive speech utterances."
                    triggerEmergency = true
                }

                // Rule 3: Medium Risk (Elevated Scream OR Single Harmful Sentence) -> Verification check-in
                screamScore in moderateAcousticThreshold..screamFastTrackThreshold -> {
                    level = RiskLevel.MEDIUM
                    compositeScore = screamScore
                    reason = "Elevated acoustic disturbance detected. Initiating user safety check-in."
                }
                text?.isHarmful == true && textScore >= 0.50f -> {
                    level = RiskLevel.MEDIUM
                    compositeScore = textScore
                    reason = "Potentially threatening language detected. Initiating user safety check-in."
                }

                // Rule 4: Low Risk (Normal benign speech or low noise)
                else -> {
                    level = RiskLevel.LOW
                    compositeScore = (screamScore * 0.5f + textScore * 0.5f).coerceIn(0.0f, 0.49f)
                    reason = "Passive monitoring active. No verified threats detected."
                }
            }
        }

        // Apply Cooldown Protection to prevent duplicate cascading triggers
        if (triggerEmergency) {
            val cooldownElapsed = now - lastEmergencyTriggerTimeMs
            if (lastEmergencyTriggerTimeMs > now || cooldownElapsed < emergencyCooldownMs) {
                Log.d(TAG, "Emergency trigger suppressed due to active cooldown")
                triggerEmergency = false
            } else {
                lastEmergencyTriggerTimeMs = now
            }
        }

        val decisionResult = RiskDecisionResult(
            riskLevel = level,
            compositeScore = compositeScore,
            reason = reason,
            yamnetScore = screamScore,
            conversationScore = textScore,
            timestamp = now,
            triggeredEmergency = triggerEmergency
        )

        // Dispatch decision to listeners
        for (listener in listeners) {
            try {
                listener.onRiskEvaluated(decisionResult)
                if (triggerEmergency) {
                    listener.onEmergencyTriggerRequired(
                        triggerSource = if (screamScore >= screamFastTrackThreshold || forcedHigh) "AI_ACOUSTIC_SCREAM" else "AI_MULTIMODAL_FUSION",
                        reason = reason
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error notifying decision listener: ${e.message}")
            }
        }
    }

    fun resetCooldown() {
        lastEmergencyTriggerTimeMs = 0L
    }

    /**
     * Clears rolling prediction buffers and enforces a grace cooldown period
     * when the user explicitly cancels or marks safe.
     */
    fun cancelAndReset(cooldownMs: Long = 30000L) {
        synchronized(historyLock) {
            recentAcousticHistory.clear()
            recentTextHistory.clear()
        }
        latestAcousticResult = null
        latestTextResult = null
        lastEmergencyTriggerTimeMs = System.currentTimeMillis() + cooldownMs
        Log.i(TAG, "Risk Decision Engine cancelled and reset with ${cooldownMs / 1000}s cooldown.")
    }
}
