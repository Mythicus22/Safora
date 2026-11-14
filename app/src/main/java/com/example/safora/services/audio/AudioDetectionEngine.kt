package com.example.safora.services.audio

import com.example.safora.data.model.AudioDetectionResult
import kotlinx.coroutines.delay

interface AudioDetectionEngine {
    fun startAnalysis(onDetection: (AudioDetectionResult) -> Unit)
    fun stopAnalysis()
}

class AudioSimulationEngine : AudioDetectionEngine {
    private var isRunning = false
    private var callback: ((AudioDetectionResult) -> Unit)? = null

    override fun startAnalysis(onDetection: (AudioDetectionResult) -> Unit) {
        isRunning = true
        callback = onDetection
    }

    override fun stopAnalysis() {
        isRunning = false
        callback = null
    }

    // Direct simulation triggers for testing & demo presentation
    fun simulateDistressTrigger() {
        callback?.invoke(
            AudioDetectionResult(
                detectedClass = "Scream / Distress Call",
                confidence = 0.94f,
                timestamp = System.currentTimeMillis()
            )
        )
    }

    fun simulateNormalSpeech() {
        callback?.invoke(
            AudioDetectionResult(
                detectedClass = "Normal Speech / Ambient",
                confidence = 0.98f,
                timestamp = System.currentTimeMillis()
            )
        )
    }

    fun simulatePhysicalImpact() {
        callback?.invoke(
            AudioDetectionResult(
                detectedClass = "Sudden High-G Impact / Thud",
                confidence = 0.89f,
                timestamp = System.currentTimeMillis()
            )
        )
    }
}
