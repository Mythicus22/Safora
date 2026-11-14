package com.example.safora

import android.app.Application
import org.osmdroid.config.Configuration

class SaforaApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Initialize OSMDroid cache configuration with app user agent
        Configuration.getInstance().userAgentValue = packageName
    }
}
