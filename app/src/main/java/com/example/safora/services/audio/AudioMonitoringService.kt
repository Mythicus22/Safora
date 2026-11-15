package com.example.safora.services.audio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.safora.data.repository.SaforaRepository

/**
 * Foreground Audio Monitoring Service.
 *
 * Hosts the multi-stage AI pipeline (Microphone capture, YAMNet TFLite, VAD,
 * Vosk English STT, Model 4 Conversation Safety, and Risk Decision Engine).
 */
class AudioMonitoringService : Service() {

    companion object {
        private const val TAG = "AudioMonitoringService"
        const val CHANNEL_ID = "safora_audio_monitoring_channel"
        const val NOTIFICATION_ID = 2001
        const val ACTION_START = "ACTION_START_MONITORING"
        const val ACTION_STOP = "ACTION_STOP_MONITORING"

        fun start(context: Context) {
            val intent = Intent(context, AudioMonitoringService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, AudioMonitoringService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    private val repository by lazy { SaforaRepository.getInstance(applicationContext) }
    private var activeEngine: AudioDetectionEngine? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == ACTION_STOP) {
            Log.i(TAG, "Received ACTION_STOP. Shutting down audio monitoring pipeline.")
            stopPipeline()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        val notification = buildNotification()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed startForeground with MICROPHONE type: ${e.message}. Retrying default.")
            try {
                startForeground(NOTIFICATION_ID, notification)
            } catch (fatal: Exception) {
                Log.e(TAG, "Cannot start foreground service: ${fatal.message}")
                stopSelf()
                return START_NOT_STICKY
            }
        }

        try {
            startPipeline()
        } catch (e: Exception) {
            Log.e(TAG, "Error starting audio pipeline: ${e.message}", e)
        }
        return START_STICKY
    }

    private fun startPipeline() {
        if (activeEngine != null) return

        Log.i(TAG, "Starting Audio Detection Pipeline with RealAudioPipelineEngine (Microphone, YAMNet, VAD, Vosk, Model 4)")

        // Always run the real production audio capture pipeline so microphone is live and listening
        val engine = RealAudioPipelineEngine(this).apply {
            startAnalysis { result ->
                repository.updateAudioDetection(result)
            }
        }
        activeEngine = engine
        repository.setActiveAudioEngine(engine)

        repository.setAudioMonitoring(true)
        repository.logEmergencyTimeline("Acoustic Monitoring Service started with YAMNet & Vosk English.")
    }

    private fun stopPipeline() {
        activeEngine?.stopAnalysis()
        activeEngine = null
        repository.setActiveAudioEngine(null)
        repository.setAudioMonitoring(false)
        repository.logEmergencyTimeline("Acoustic Monitoring Service stopped.")
    }

    override fun onDestroy() {
        super.onDestroy()
        stopPipeline()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "SAFORA Safety Shield (Live Listening)",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifies when SAFORA is actively listening for distress screams & emergency speech."
                setShowBadge(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val openIntent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = if (openIntent != null) {
            android.app.PendingIntent.getActivity(
                this,
                0,
                openIntent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )
        } else null

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("🎙️ SAFORA Is Listening for Audio")
            .setContentText("Acoustic scream & speech safety shield is actively running.")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .apply {
                if (pendingIntent != null) setContentIntent(pendingIntent)
            }
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }
}
