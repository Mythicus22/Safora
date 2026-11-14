package com.example.safora.services.audio

import com.example.safora.services.audio.vad.VoiceActivityDetector
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import kotlin.math.sin

class VoiceActivityDetectorTest {

    private lateinit var vad: VoiceActivityDetector

    @Before
    fun setUp() {
        vad = VoiceActivityDetector(
            energyThresholdDbfs = -42.0f,
            hangoverDurationMs = 600L
        )
    }

    @Test
    fun testSilenceRejection() {
        // Feed 100ms of pure silence (all zeros)
        val silence = ShortArray(1600) { 0 }
        vad.onAudioFrame(silence, silence.size)

        assertFalse("Pure silence must not activate speech state", vad.isSpeechCurrentlyActive())
    }

    @Test
    fun testLowAmbientNoiseRejection() {
        // Very low amplitude noise (around -55 dBFS)
        val lowNoise = ShortArray(1600) { ((it % 5) - 2).toShort() }
        vad.onAudioFrame(lowNoise, lowNoise.size)

        assertFalse("Low ambient noise must not activate speech state", vad.isSpeechCurrentlyActive())
    }

    @Test
    fun testSpeechLikeToneDetection() {
        // Generate 300Hz sine wave (speech fundamental) at good amplitude (-18 dBFS)
        // 4000 amplitude gives ~ -18 dBFS
        val sampleRate = 16000
        val freq = 300.0
        val speechTone = ShortArray(1600) { i ->
            (4000.0 * sin(2.0 * Math.PI * freq * i / sampleRate)).toInt().toShort()
        }

        vad.onAudioFrame(speechTone, speechTone.size)
        assertTrue("Speech-like periodic tone in speech frequency band must activate VAD", vad.isSpeechCurrentlyActive())
    }

    @Test
    fun testHangoverMaintainsSpeechThroughShortPauses() {
        // 1. Feed speech frame to activate speech
        val speechTone = ShortArray(1600) { i ->
            (5000.0 * sin(2.0 * Math.PI * 400.0 * i / 16000)).toInt().toShort()
        }
        vad.onAudioFrame(speechTone, speechTone.size)
        assertTrue("Speech should be active", vad.isSpeechCurrentlyActive())

        // 2. Feed an immediate silent frame (representing an inter-word pause)
        val silence = ShortArray(1600) { 0 }
        vad.onAudioFrame(silence, silence.size)

        // Since hangoverDurationMs is 600ms, speech state must remain active
        assertTrue("VAD hangover timer must keep speech active during brief pauses", vad.isSpeechCurrentlyActive())
    }
}
