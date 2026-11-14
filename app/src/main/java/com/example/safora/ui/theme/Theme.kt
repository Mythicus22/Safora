package com.example.safora.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = BluePrimary,
    onPrimary = Color.White,
    primaryContainer = CharcoalSurfaceElevated,
    onPrimaryContainer = TextPrimary,
    secondary = CyanAccent,
    onSecondary = Color.Black,
    error = CrimsonEmergency,
    onError = Color.White,
    background = CharcoalDark,
    onBackground = TextPrimary,
    surface = CharcoalSurface,
    onSurface = TextPrimary,
    surfaceVariant = CharcoalSurfaceElevated,
    onSurfaceVariant = TextSecondary,
    outline = CharcoalBorder
)

@Composable
fun SaforaTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
