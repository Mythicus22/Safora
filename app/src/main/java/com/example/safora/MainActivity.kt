package com.example.safora

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.safora.core.navigation.AppNavHost
import com.example.safora.core.utils.PermissionHelper
import com.example.safora.services.audio.AudioMonitoringService
import com.example.safora.services.audio.model.ModelDownloadManager
import com.example.safora.ui.theme.SaforaTheme

class MainActivity : ComponentActivity() {

    private val modelDownloadManager by lazy { ModelDownloadManager.getInstance(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

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
        // 1. If models not downloaded -> post/keep notification
        // 2. If models downloaded and mic permission granted -> keep mic on always
        modelDownloadManager.checkModelStatusAndNotify(this)
        if (modelDownloadManager.areAllDownloaded() && PermissionHelper.hasAudioPermission(this)) {
            AudioMonitoringService.start(this)
        }
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
