package com.example.safora

import android.app.Application
import org.osmdroid.config.Configuration
import java.io.File

class SaforaApp : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // Fix OSMDroid "Access Blocked" / HTTP 403 on OpenStreetMap tile servers:
        // OpenStreetMap requires a distinct User-Agent policy identifying the application and package.
        val config = Configuration.getInstance()
        config.userAgentValue = "SAFORA-PersonalSafety-App/1.0 (Android; Package: ${packageName}; Contact: support@safora.app)"
        
        // Set up dedicated tile cache directory inside internal storage
        val osmCacheDir = File(cacheDir, "osmdroid")
        if (!osmCacheDir.exists()) {
            osmCacheDir.mkdirs()
        }
        config.osmdroidTileCache = osmCacheDir
        config.osmdroidBasePath = osmCacheDir

        // Initialize repository singleton
        com.example.safora.data.repository.SaforaRepository.instance =
            com.example.safora.data.repository.SaforaRepository.getInstance(this)
    }
}
