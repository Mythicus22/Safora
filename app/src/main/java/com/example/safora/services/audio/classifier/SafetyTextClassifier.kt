package com.example.safora.services.audio.classifier

import android.content.Context
import android.util.Log
import com.example.safora.data.model.SafetyClassificationResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.math.max

interface SafetyClassifierListener {
    fun onSafetyClassification(result: SafetyClassificationResult)
}

/**
 * Model 4: Suspicious/Abusive Conversation Classifier.
 *
 * Implements integration with the ML developer's "SAFORA Binary Text Safety Baseline"
 * (DistilBERT / Nebulixlabs/FullSafety model).
 *
 * Contract:
 * - Baseline threshold: 0.05 (from safora_metadata.json)
 * - Binary labels: "BENIGN" (0), "HARMFUL" (1)
 * - Max sequence length: 128
 *
 * Privacy & Offline-First Design:
 * - Local inference engine provides real-time offline classification without network access.
 * - If remote inference is enabled and user consent is granted, queries the REST model service
 *   with exponential backoff and automatic offline fallback upon network failure.
 */
class SafetyTextClassifier(
    private val context: Context? = null,
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(4, TimeUnit.SECONDS)
        .build()
) {

    companion object {
        private const val TAG = "SafetyTextClassifier"
        const val BASELINE_THRESHOLD = 0.05f
        const val MAX_INPUT_LENGTH = 128

        // FullSafety Abusive / Threat / Harmful Semantic Lexicon
        private val HARMFUL_HIGH_INTENT = listOf(
            "kill you", "hurt you", "stab", "shoot", "attack",
            "bleed", "break your", "murder", "beat you", "die"
        )

        private val HARMFUL_COERCION_STALKING = listOf(
            "get in the car", "don't scream", "shut up", "follow you",
            "corner you", "let me in", "touch you", "grab her", "take off your",
            "don't make a sound", "don't move", "give me your", "give me the", "hand over"
        )

        private val DISTRESS_EMERGENCY_PLEAS = listOf(
            "leave me alone", "please stop", "let me go", "help me",
            "call the police", "don't do this", "get away from me", "back off"
        )

        private val BENIGN_FALSE_POSITIVE_GUARDS = listOf(
            "homework", "math", "exam", "assignment", "test",
            "groceries", "supermarket", "carry", "lift",
            "movie", "film", "actor", "game", "playing", "comedy"
        )
    }

    private var remoteEndpointUrl: String? = null
    private var isCloudConsentGranted: Boolean = false
    private val listeners = mutableListOf<SafetyClassifierListener>()

    fun addListener(listener: SafetyClassifierListener) {
        synchronized(listeners) { listeners.add(listener) }
    }

    fun removeListener(listener: SafetyClassifierListener) {
        synchronized(listeners) { listeners.remove(listener) }
    }

    fun setRemoteEndpoint(url: String?, userConsentGranted: Boolean) {
        remoteEndpointUrl = url
        isCloudConsentGranted = userConsentGranted
        Log.d(TAG, "Configured classifier remote endpoint: $url (Consent: $userConsentGranted)")
    }

    suspend fun classifyTextContext(transcriptContext: String): SafetyClassificationResult = withContext(Dispatchers.IO) {
        val trimmed = transcriptContext.trim().take(MAX_INPUT_LENGTH * 6) // Character bound
        if (trimmed.isBlank()) {
            return@withContext SafetyClassificationResult(
                label = "BENIGN",
                score = 0.0f,
                threshold = BASELINE_THRESHOLD,
                isHarmful = false,
                isLocalFallback = true
            )
        }

        val startTime = System.currentTimeMillis()

        // 1. Try remote inference if endpoint is configured and consent is granted
        val endpoint = remoteEndpointUrl
        if (!endpoint.isNullOrBlank() && isCloudConsentGranted) {
            try {
                val remoteResult = queryRemoteModel(endpoint, trimmed)
                if (remoteResult != null) {
                    dispatchResult(remoteResult)
                    return@withContext remoteResult
                }
            } catch (e: Exception) {
                Log.w(TAG, "Remote classifier unreachable (${e.message}); using local offline inference")
            }
        }

        // 2. Offline-First Local Inference Engine
        val localResult = runLocalInference(trimmed, startTime)
        dispatchResult(localResult)
        return@withContext localResult
    }

    private fun queryRemoteModel(endpointUrl: String, text: String): SafetyClassificationResult? {
        val jsonPayload = JSONObject().apply {
            put("text", text)
            put("max_length", MAX_INPUT_LENGTH)
        }

        val requestBody = jsonPayload.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url(endpointUrl)
            .post(requestBody)
            .build()

        val startTime = System.currentTimeMillis()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                Log.w(TAG, "Remote inference returned HTTP ${response.code}")
                return null
            }

            val body = response.body?.string() ?: return null
            val respJson = JSONObject(body)

            // Support actual Model 4 format
            val label = respJson.optString("label", "BENIGN")
            val score = respJson.optDouble("score", respJson.optDouble("harmful_probability", 0.0)).toFloat()
            val threshold = respJson.optDouble("threshold", BASELINE_THRESHOLD.toDouble()).toFloat()
            val isHarmful = respJson.optBoolean("is_harmful", score >= threshold || label == "HARMFUL")

            return SafetyClassificationResult(
                label = label,
                score = score,
                threshold = threshold,
                isHarmful = isHarmful,
                isLocalFallback = false,
                latencyMs = System.currentTimeMillis() - startTime,
                timestamp = System.currentTimeMillis()
            )
        }
    }

    /**
     * Local offline safety scoring aligned with Nebulixlabs/FullSafety taxonomy.
     */
    fun runLocalInference(text: String, startTime: Long): SafetyClassificationResult {
        val lower = text.lowercase()

        // Check benign context guards to prevent false triggers (e.g. "help me with my homework")
        for (guard in BENIGN_FALSE_POSITIVE_GUARDS) {
            if (lower.contains(guard)) {
                return SafetyClassificationResult(
                    label = "BENIGN",
                    score = 0.01f,
                    threshold = BASELINE_THRESHOLD,
                    isHarmful = false,
                    isLocalFallback = true,
                    latencyMs = System.currentTimeMillis() - startTime
                )
            }
        }

        var harmfulScore = 0.0f

        // High physical threat patterns
        for (pattern in HARMFUL_HIGH_INTENT) {
            if (lower.contains(pattern)) {
                harmfulScore = max(harmfulScore, 0.88f)
            }
        }

        // Coercion & stalking patterns
        for (pattern in HARMFUL_COERCION_STALKING) {
            if (lower.contains(pattern)) {
                harmfulScore = max(harmfulScore, 0.72f)
            }
        }

        // Panic and distress pleas
        for (pattern in DISTRESS_EMERGENCY_PLEAS) {
            if (lower.contains(pattern)) {
                harmfulScore = max(harmfulScore, 0.65f)
            }
        }

        // Apply Model 4 threshold boundary (0.05)
        val isHarmful = harmfulScore >= BASELINE_THRESHOLD
        val label = if (isHarmful) "HARMFUL" else "BENIGN"

        return SafetyClassificationResult(
            label = label,
            score = harmfulScore,
            threshold = BASELINE_THRESHOLD,
            isHarmful = isHarmful,
            isLocalFallback = true,
            latencyMs = System.currentTimeMillis() - startTime
        )
    }

    private fun dispatchResult(result: SafetyClassificationResult) {
        synchronized(listeners) {
            for (l in listeners) {
                try {
                    l.onSafetyClassification(result)
                } catch (e: Exception) {
                    Log.e(TAG, "Listener error: ${e.message}")
                }
            }
        }
    }
}
