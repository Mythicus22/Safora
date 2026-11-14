package com.example.safora.services.sos

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.safora.core.utils.AutonomousCallManager
import com.example.safora.core.utils.EmergencyDispatcher
import com.example.safora.data.model.IncidentState
import com.example.safora.data.repository.SaforaRepository
import com.example.safora.services.location.SaforaLocationClient
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*

/**
 * Foreground Service responsible for:
 * 1. Immediate autonomous Emergency SMS & Autonomous Sequential Calls on SOS confirmation.
 * 2. Reliable periodic GPS updates every 3 minutes while SOS is active.
 * 3. Graceful termination when SOS is resolved or cancelled.
 */
class EmergencySosService : Service() {

    companion object {
        const val TAG = "EmergencySosService"
        const val CHANNEL_ID = "safora_sos_emergency_channel"
        const val NOTIFICATION_ID = 9001
        const val ACTION_START = "ACTION_START_EMERGENCY_SOS"
        const val ACTION_STOP = "ACTION_STOP_EMERGENCY_SOS"

        fun start(context: Context) {
            val intent = Intent(context, EmergencySosService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, EmergencySosService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.Default + Job())
    private var periodicSmsJob: Job? = null
    private var locationClient: SaforaLocationClient? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        locationClient = SaforaLocationClient(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == ACTION_STOP) {
            stopSosService()
            return START_NOT_STICKY
        }

        val notification = buildForegroundNotification("Active Emergency SOS: GPS Tracking & SMS Active")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        startEmergencyExecution()
        return START_STICKY
    }

    private fun startEmergencyExecution() {
        val repository = SaforaRepository.getInstance(this)
        locationClient?.startLocationUpdates()

        // 1. Initial autonomous call dispatch
        val emergencyContacts = repository.contacts.value.filter { it.isEmergencyContact }
        val phoneNumbers = emergencyContacts.map { it.phone }

        if (phoneNumbers.isNotEmpty()) {
            AutonomousCallManager.executeSequentialCalls(
                context = this,
                phoneNumbers = phoneNumbers,
                maxRetriesPerContact = 1,
                onStatusUpdate = { status ->
                    repository.logEmergencyTimeline(status)
                }
            )
        }

        // 2. Start Periodic GPS SMS Updates (every 3 minutes)
        periodicSmsJob?.cancel()
        periodicSmsJob = serviceScope.launch {
            while (isActive) {
                // Wait 3 minutes (180,000 ms)
                delay(180_000)

                val incident = repository.currentIncident.value
                if (incident.state != IncidentState.CONFIRMED_SOS &&
                    incident.state != IncidentState.ALERTS_SENT &&
                    incident.state != IncidentState.EVIDENCE_SYNCING
                ) {
                    Log.i(TAG, "SOS is no longer active (${incident.state}). Halting periodic SMS.")
                    break
                }

                val user = repository.user.value
                val loc = repository.currentLocation.value
                val contacts = repository.contacts.value.filter { it.isEmergencyContact }

                val timeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
                val periodicMsg = "SAFORA UPDATE [$timeStr]: ${user.name} SOS still active. Updated location: https://maps.google.com/?q=${loc.first},${loc.second} (Incident ID: ${incident.id})."

                contacts.forEach { contact ->
                    EmergencyDispatcher.sendDirectSms(
                        context = this@EmergencySosService,
                        phoneNumber = contact.phone,
                        message = periodicMsg,
                        onSuccess = {
                            repository.logEmergencyTimeline("Periodic GPS SMS update delivered to ${contact.name}")
                        },
                        onError = { err ->
                            repository.logEmergencyTimeline("Periodic SMS error for ${contact.name}: $err")
                        }
                    )
                }
            }
        }
    }

    private fun stopSosService() {
        periodicSmsJob?.cancel()
        locationClient?.stopLocationUpdates()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        locationClient?.stopLocationUpdates()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "SAFORA Active Emergency SOS",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Monitors incident dispatch, periodic GPS broadcast and autonomous calling"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildForegroundNotification(content: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("EMERGENCY SOS DISPATCH ACTIVE")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
    }
}
