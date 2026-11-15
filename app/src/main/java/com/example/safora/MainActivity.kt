package com.example.safora

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.safora.core.navigation.AppNavHost
import com.example.safora.core.utils.PermissionHelper
import com.example.safora.data.repository.SaforaRepository
import com.example.safora.services.audio.AudioMonitoringService
import com.example.safora.services.audio.model.ModelDownloadManager
import com.example.safora.services.location.SaforaLocationClient
import com.example.safora.ui.theme.SaforaTheme

class MainActivity : ComponentActivity() {

    private val modelDownloadManager by lazy { ModelDownloadManager.getInstance(this) }
    private var locationClient: SaforaLocationClient? = null
    private var locationSettingsDialog: AlertDialog? = null

    private val runtimePermissionLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results[android.Manifest.permission.RECORD_AUDIO] == true) {
            AudioMonitoringService.start(this)
        }
        checkAndActivateEssentialServices()
        modelDownloadManager.checkModelStatusAndNotify(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Auto-request missing runtime permissions on startup (Microphone, Notifications, GPS, Calls, SMS)
        val missing = PermissionHelper.getMissingPermissions(this)
        if (missing.isNotEmpty()) {
            runtimePermissionLauncher.launch(missing.toTypedArray())
        } else {
            checkAndActivateEssentialServices()
        }

        // Check if models are downloaded whenever app is opened; notify user if missing
        modelDownloadManager.checkModelStatusAndNotify(this)

        val openProfile = intent?.getBooleanExtra(ModelDownloadManager.EXTRA_OPEN_PROFILE, false) ?: false

        setContent {
            SaforaTheme {
                AppNavHost(initialNavigateToProfile = openProfile)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Re-check whenever app comes to foreground:
        // 1. Ensure microphone & location are automatically running
        // 2. Alert user if hardware GPS location is turned off in device settings
        checkAndActivateEssentialServices()
        modelDownloadManager.checkModelStatusAndNotify(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        locationSettingsDialog?.dismiss()
        locationSettingsDialog = null
    }

    /**
     * Automatically activates essential safety services:
     * 1. Continuous microphone acoustic monitoring.
     * 2. Live GPS tracking.
     * 3. Prompts user if system Location is turned off.
     */
    private fun checkAndActivateEssentialServices() {
        val repository = SaforaRepository.getInstance(this)

        // 1. Microphone: Automatically start audio monitoring if permission granted
        if (PermissionHelper.hasAudioPermission(this)) {
            AudioMonitoringService.start(this)
        }

        // 2. Location: Check permission and system hardware toggle
        if (PermissionHelper.hasLocationPermission(this)) {
            val isGpsOn = PermissionHelper.isLocationHardwareEnabled(this)
            repository.setGpsHardwareEnabled(isGpsOn)

            if (isGpsOn) {
                locationSettingsDialog?.dismiss()
                locationClient = locationClient ?: SaforaLocationClient(this)
                locationClient?.startLocationUpdates()
            } else {
                showLocationDisabledDialog()
            }
        }
    }

    private fun showLocationDisabledDialog() {
        if (isFinishing || isDestroyed) return
        if (locationSettingsDialog?.isShowing == true) return

        locationSettingsDialog = AlertDialog.Builder(this)
            .setTitle("📍 Turn On Device Location")
            .setMessage("SAFORA Safety Shield requires Device Location (GPS) to accurately track your real-time position, dispatch live coordinates to 112 emergency services, and alert nearby responders. Please turn on Location in Settings.")
            .setPositiveButton("Turn On Location") { dialog, _ ->
                dialog.dismiss()
                try {
                    startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                } catch (_: Exception) {
                    startActivity(Intent(Settings.ACTION_SETTINGS))
                }
            }
            .setNegativeButton("Later") { dialog, _ ->
                dialog.dismiss()
            }
            .setCancelable(true)
            .create()
        locationSettingsDialog?.show()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val openProfile = intent.getBooleanExtra(ModelDownloadManager.EXTRA_OPEN_PROFILE, false)
        if (openProfile) {
            modelDownloadManager.checkModelStatusAndNotify(this)
        }
    }
}
