package com.example.safora

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.safora.core.navigation.AppNavHost
import com.example.safora.ui.theme.SaforaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SaforaTheme {
                AppNavHost()
            }
        }
    }
}
