package com.example.safora.services.audio.capture

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.*
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

interface AudioStreamListener {
    fun onAudioFrame(pcmData: ShortArray, readSize: Int)
}

/**
 * Shared microphone audio capture engine using a single AudioRecord instance.
 *
 * Captures 16 kHz, 16-bit mono PCM audio and broadcasts identical frames
 * to all registered processing components (YAMNet, VAD, Vosk, rolling RAM cache).
 *
 * Privacy guarantee: Maintains a short rolling RAM buffer (10s) in memory.
 * Audio is NEVER written to persistent storage or uploaded to the cloud
 * unless an emergency incident is confirmed.
 */
class SharedAudioCaptureEngine private constructor(private val context: Context) {

    companion object {
        private const val TAG = "SharedAudioCapture"
        const val SAMPLE_RATE = 16000
        const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        const val ROLLING_BUFFER_DURATION_SEC = 10
        const val ROLLING_BUFFER_CAPACITY_BYTES = SAMPLE_RATE * 2 * ROLLING_BUFFER_DURATION_SEC // 320 KB

        @Volatile
        private var INSTANCE: SharedAudioCaptureEngine? = null

        fun getInstance(context: Context): SharedAudioCaptureEngine {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SharedAudioCaptureEngine(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val isRecording = AtomicBoolean(false)
    private var audioRecord: AudioRecord? = null
    private var captureJob: Job? = null
    private val captureExceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Log.e(TAG, "Unhandled exception in AudioCapture coroutine: ${throwable.message}", throwable)
    }
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob() + captureExceptionHandler)

    private val listeners = CopyOnWriteArrayList<Pair<String, AudioStreamListener>>()

    // In-memory rolling audio buffer (10 seconds, ~320 KB) implemented as a crash-proof circular ring buffer
    private val rollingRamBuffer = ByteArray(ROLLING_BUFFER_CAPACITY_BYTES)
    private var rollingHead = 0
    private var rollingCount = 0
    private val ramBufferLock = Any()

    fun addListener(id: String, listener: AudioStreamListener) {
        listeners.removeAll { it.first == id }
        listeners.add(Pair(id, listener))
        Log.d(TAG, "Audio listener registered: $id (Total: ${listeners.size})")
    }

    fun removeListener(id: String) {
        listeners.removeAll { it.first == id }
        Log.d(TAG, "Audio listener removed: $id (Remaining: ${listeners.size})")
    }

    @SuppressLint("MissingPermission")
    fun startCapture(onError: ((String) -> Unit)? = null): Boolean {
        if (isRecording.get()) {
            Log.d(TAG, "Audio capture already running.")
            return true
        }

        val minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        if (minBufferSize == AudioRecord.ERROR || minBufferSize == AudioRecord.ERROR_BAD_VALUE) {
            val msg = "AudioRecord minBufferSize calculation failed"
            Log.e(TAG, msg)
            onError?.invoke(msg)
            return false
        }

        val bufferSize = (minBufferSize * 2).coerceAtLeast(SAMPLE_RATE / 4) // At least 250ms chunks

        try {
            // 1. Try standard MIC source first (universal across all Android devices)
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            )

            // 2. Fallback to VOICE_RECOGNITION if MIC failed
            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                audioRecord?.release()
                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.VOICE_RECOGNITION,
                    SAMPLE_RATE,
                    CHANNEL_CONFIG,
                    AUDIO_FORMAT,
                    bufferSize
                )
            }

            // 3. Fallback to DEFAULT
            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                audioRecord?.release()
                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.DEFAULT,
                    SAMPLE_RATE,
                    CHANNEL_CONFIG,
                    AUDIO_FORMAT,
                    bufferSize
                )
            }

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                val msg = "AudioRecord failed to initialize (State: ${audioRecord?.state})"
                Log.e(TAG, msg)
                onError?.invoke(msg)
                audioRecord?.release()
                audioRecord = null
                return false
            }

            audioRecord?.startRecording()
            isRecording.set(true)
            Log.i(TAG, "Microphone capture started successfully @ 16 kHz Mono PCM")

            captureJob = scope.launch {
                val frameChunk = ShortArray(1024) // 64ms chunk @ 16kHz

                try {
                    while (isActive && isRecording.get()) {
                        val readSamples = audioRecord?.read(frameChunk, 0, frameChunk.size) ?: -1
                        if (readSamples > 0) {
                            // 1. Append to Rolling RAM Buffer for emergency evidence snapshot (Crash-proof circular ring)
                            synchronized(ramBufferLock) {
                                for (i in 0 until readSamples) {
                                    val sample = frameChunk[i]
                                    rollingRamBuffer[rollingHead] = (sample.toInt() and 0xFF).toByte()
                                    rollingHead = (rollingHead + 1) % ROLLING_BUFFER_CAPACITY_BYTES
                                    rollingRamBuffer[rollingHead] = ((sample.toInt() shr 8) and 0xFF).toByte()
                                    rollingHead = (rollingHead + 1) % ROLLING_BUFFER_CAPACITY_BYTES

                                    if (rollingCount < ROLLING_BUFFER_CAPACITY_BYTES) {
                                        rollingCount = (rollingCount + 2).coerceAtMost(ROLLING_BUFFER_CAPACITY_BYTES)
                                    }
                                }
                            }

                            // 2. Broadcast frame to all registered listeners (YAMNet, VAD, Vosk)
                            for ((_, listener) in listeners) {
                                try {
                                    listener.onAudioFrame(frameChunk, readSamples)
                                } catch (e: Exception) {
                                    Log.e(TAG, "Error in audio listener: ${e.message}")
                                }
                            }
                        } else if (readSamples < 0) {
                            Log.w(TAG, "AudioRecord read error code: $readSamples")
                            delay(20)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Loop exception in audio capture: ${e.message}", e)
                }
            }

            return true
        } catch (e: Exception) {
            val msg = "Exception launching AudioRecord: ${e.message}"
            Log.e(TAG, msg, e)
            onError?.invoke(msg)
            stopCapture()
            return false
        }
    }

    fun stopCapture() {
        if (!isRecording.getAndSet(false)) return
        Log.i(TAG, "Stopping audio capture...")

        captureJob?.cancel()
        captureJob = null

        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping AudioRecord: ${e.message}")
        } finally {
            audioRecord = null
        }
    }

    fun isCapturing(): Boolean = isRecording.get()

    /**
     * Snapshots the last 10 seconds of raw audio from RAM and writes a standard WAV file.
     * Only invoked upon confirmed emergency incident creation.
     */
    fun exportRollingAudioSnapshot(outputFile: File): Boolean {
        return try {
            val pcmBytes: ByteArray
            synchronized(ramBufferLock) {
                if (rollingCount <= 0) {
                    Log.w(TAG, "Rolling RAM buffer is empty; cannot snapshot audio")
                    return false
                }
                pcmBytes = ByteArray(rollingCount)
                val start = if (rollingCount < ROLLING_BUFFER_CAPACITY_BYTES) 0 else rollingHead
                for (i in 0 until rollingCount) {
                    pcmBytes[i] = rollingRamBuffer[(start + i) % ROLLING_BUFFER_CAPACITY_BYTES]
                }
            }

            FileOutputStream(outputFile).use { fos ->
                writeWavHeader(fos, pcmBytes.size, SAMPLE_RATE, 1, 16)
                fos.write(pcmBytes)
            }
            Log.i(TAG, "Saved emergency audio RAM snapshot: ${outputFile.name} (${outputFile.length() / 1024} KB)")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to export audio RAM snapshot: ${e.message}", e)
            false
        }
    }

    private fun writeWavHeader(
        out: FileOutputStream,
        totalAudioLen: Int,
        sampleRate: Int,
        channels: Int,
        bitsPerSample: Int
    ) {
        val totalDataLen = totalAudioLen + 36
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val header = ByteArray(44)

        header[0] = 'R'.code.toByte()
        header[1] = 'I'.code.toByte()
        header[2] = 'F'.code.toByte()
        header[3] = 'F'.code.toByte()
        header[4] = (totalDataLen and 0xff).toByte()
        header[5] = ((totalDataLen shr 8) and 0xff).toByte()
        header[6] = ((totalDataLen shr 16) and 0xff).toByte()
        header[7] = ((totalDataLen shr 24) and 0xff).toByte()
        header[8] = 'W'.code.toByte()
        header[9] = 'A'.code.toByte()
        header[10] = 'V'.code.toByte()
        header[11] = 'E'.code.toByte()
        header[12] = 'f'.code.toByte()
        header[13] = 'm'.code.toByte()
        header[14] = 't'.code.toByte()
        header[15] = ' '.code.toByte()
        header[16] = 16
        header[17] = 0
        header[18] = 0
        header[19] = 0
        header[20] = 1 // PCM
        header[21] = 0
        header[22] = channels.toByte()
        header[23] = 0
        header[24] = (sampleRate and 0xff).toByte()
        header[25] = ((sampleRate shr 8) and 0xff).toByte()
        header[26] = ((sampleRate shr 16) and 0xff).toByte()
        header[27] = ((sampleRate shr 24) and 0xff).toByte()
        header[28] = (byteRate and 0xff).toByte()
        header[29] = ((byteRate shr 8) and 0xff).toByte()
        header[30] = ((byteRate shr 16) and 0xff).toByte()
        header[31] = ((byteRate shr 24) and 0xff).toByte()
        header[32] = (channels * bitsPerSample / 8).toByte()
        header[33] = 0
        header[34] = bitsPerSample.toByte()
        header[35] = 0
        header[36] = 'd'.code.toByte()
        header[37] = 'a'.code.toByte()
        header[38] = 't'.code.toByte()
        header[39] = 'a'.code.toByte()
        header[40] = (totalAudioLen and 0xff).toByte()
        header[41] = ((totalAudioLen shr 8) and 0xff).toByte()
        header[42] = ((totalAudioLen shr 16) and 0xff).toByte()
        header[43] = ((totalAudioLen shr 24) and 0xff).toByte()

        out.write(header, 0, 44)
    }
}
