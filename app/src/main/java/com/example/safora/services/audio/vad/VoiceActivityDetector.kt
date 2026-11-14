package com.example.safora.services.audio.vad

import android.util.Log
import com.example.safora.services.audio.capture.AudioStreamListener
import kotlin.math.log10
import kotlin.math.sqrt

interface VadListener {
    fun onSpeechStateChanged(isSpeechActive: Boolean, rmsDbfs: Float)
    fun onSpeechAudioFrame(pcmData: ShortArray, readSize: Int)
    fun onAudioEnergyTelemetry(rmsDbfs: Float, noiseFloorDbfs: Float, isSpeech: Boolean) {}
}

/**
 * Energy and Zero-Crossing based Voice Activity Detector (VAD).
 *
 * Efficiently detects human speech frames to gate downstream Vosk speech recognition,
 * preventing continuous CPU & battery consumption during silence or background ambient noise.
 */
class VoiceActivityDetector(
    private var energyThresholdDbfs: Float = -42.0f,
    private val hangoverDurationMs: Long = 600L
) : AudioStreamListener {

    companion object {
        private const val TAG = "VoiceActivityDetector"
    }

    private var noiseFloorDbfs = -55.0f
    private var isSpeechActive = false
    private var lastSpeechTimestampMs = 0L

    private val listeners = mutableListOf<VadListener>()

    fun addListener(listener: VadListener) {
        synchronized(listeners) {
            listeners.add(listener)
        }
    }

    fun removeListener(listener: VadListener) {
        synchronized(listeners) {
            listeners.remove(listener)
        }
    }

    fun setSensitivity(level: String) {
        energyThresholdDbfs = when (level.lowercase()) {
            "high" -> -48.0f // More sensitive (picks up whisper/quiet speech)
            "low" -> -36.0f  // Less sensitive (requires louder speech)
            else -> -42.0f   // Medium default
        }
        Log.d(TAG, "VAD energy threshold updated: $energyThresholdDbfs dBFS ($level)")
    }

    override fun onAudioFrame(pcmData: ShortArray, readSize: Int) {
        if (readSize <= 0) return

        // 1. Calculate RMS energy
        var sumSquares = 0.0
        var zeroCrossings = 0
        for (i in 0 until readSize) {
            val s = pcmData[i].toDouble()
            sumSquares += s * s
            if (i > 0 && ((pcmData[i] >= 0 && pcmData[i - 1] < 0) || (pcmData[i] < 0 && pcmData[i - 1] >= 0))) {
                zeroCrossings++
            }
        }

        val rms = sqrt(sumSquares / readSize)
        val normalizedRms = (rms / 32768.0).coerceAtLeast(1e-5)
        val currentDbfs = (20.0 * log10(normalizedRms)).toFloat()
        val zcrRate = zeroCrossings.toFloat() / readSize

        val now = System.currentTimeMillis()

        // 2. Decision: Voice Activity vs Ambient Background
        // Human speech typically features energy above threshold and moderate ZCR (0.04 to 0.45)
        val isVoiceEnergy = currentDbfs > energyThresholdDbfs && currentDbfs > (noiseFloorDbfs + 6.0f)
        val isLikelySpeech = isVoiceEnergy && zcrRate in 0.02f..0.50f

        if (isLikelySpeech) {
            lastSpeechTimestampMs = now
            if (!isSpeechActive) {
                isSpeechActive = true
                dispatchStateChange(true, currentDbfs)
            }
        } else {
            // Apply hangover duration before shutting off speech gate
            if (isSpeechActive && (now - lastSpeechTimestampMs > hangoverDurationMs)) {
                isSpeechActive = false
                dispatchStateChange(false, currentDbfs)
            } else if (!isSpeechActive) {
                // Slowly adapt noise floor during calm background
                noiseFloorDbfs = (noiseFloorDbfs * 0.95f + currentDbfs * 0.05f).coerceIn(-75.0f, -30.0f)
            }
        }

        // Dispatch continuous energy telemetry for real-time live debugging VU meters
        synchronized(listeners) {
            for (listener in listeners) {
                try {
                    listener.onAudioEnergyTelemetry(currentDbfs, noiseFloorDbfs, isSpeechActive)
                } catch (_: Exception) {}
            }
        }

        // If speech is currently active (or within hangover), forward frame to speech recognizer
        if (isSpeechActive) {
            synchronized(listeners) {
                for (listener in listeners) {
                    try {
                        listener.onSpeechAudioFrame(pcmData, readSize)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error dispatching speech frame: ${e.message}")
                    }
                }
            }
        }
    }

    private fun dispatchStateChange(active: Boolean, dbfs: Float) {
        synchronized(listeners) {
            for (listener in listeners) {
                try {
                    listener.onSpeechStateChanged(active, dbfs)
                } catch (e: Exception) {
                    Log.e(TAG, "Error dispatching state change: ${e.message}")
                }
            }
        }
    }

    fun isSpeechCurrentlyActive(): Boolean = isSpeechActive
    fun getCurrentNoiseFloorDbfs(): Float = noiseFloorDbfs
}
