package com.example.safora.features.monitoring

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.safora.core.utils.PermissionHelper
import com.example.safora.data.model.AudioDetectionResult
import com.example.safora.data.model.RiskLevel
import com.example.safora.data.repository.SaforaRepository
import com.example.safora.services.audio.AudioMonitoringService
import com.example.safora.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun MonitoringScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val repository = remember { SaforaRepository.getInstance(context) }
    val isMonitoring by repository.isAudioMonitoring.collectAsState()
    val lastDetection by repository.lastAudioDetection.collectAsState()
    val audioHistory by repository.audioHistory.collectAsState()
    val sensitivity by repository.audioSensitivity.collectAsState()

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            AudioMonitoringService.start(context)
        }
    }

    // Dynamic pulsating waveform animations
    val infiniteTransition = rememberInfiniteTransition(label = "audio_bars")
    val pulseMultiplier by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(450, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bars"
    )

    val radarWaveScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = EaseOutQuad),
            repeatMode = RepeatMode.Restart
        ),
        label = "radar_wave"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CharcoalDark)
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // App Bar Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(CharcoalSurfaceElevated)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "LIVE ACOUSTIC SHIELD",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "YAMNet • VAD • Vosk English • DistilBERT",
                        color = TextSecondary,
                        fontSize = 10.sp
                    )
                }
            }

            // Mic Power Toggle Pill
            Surface(
                modifier = Modifier.clickable {
                    if (isMonitoring) {
                        AudioMonitoringService.stop(context)
                    } else {
                        if (PermissionHelper.hasAudioPermission(context)) {
                            AudioMonitoringService.start(context)
                        } else {
                            micPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                        }
                    }
                },
                color = if (isMonitoring) EmeraldSafe.copy(alpha = 0.15f) else CharcoalSurfaceElevated,
                border = BorderStroke(1.dp, if (isMonitoring) EmeraldSafe else CharcoalBorder),
                shape = RoundedCornerShape(20.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isMonitoring) Icons.Default.Mic else Icons.Default.MicOff,
                        contentDescription = null,
                        tint = if (isMonitoring) EmeraldSafe else TextTertiary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isMonitoring) "ACTIVE" else "START",
                        color = if (isMonitoring) EmeraldSafe else TextPrimary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Centerpiece: Dynamic Acoustic Waveform Radar
        Box(
            modifier = Modifier.size(150.dp),
            contentAlignment = Alignment.Center
        ) {
            if (isMonitoring) {
                // Outer subtle radar pulse wave
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .scale(radarWaveScale)
                        .clip(CircleShape)
                        .background(EmeraldSafe.copy(alpha = 0.12f))
                )
            }

            // Inner circle container
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            listOf(
                                if (isMonitoring) EmeraldSafe.copy(alpha = 0.2f) else CharcoalSurfaceElevated,
                                CharcoalSurface
                            )
                        )
                    )
                    .border(
                        BorderStroke(
                            2.dp,
                            if (isMonitoring) EmeraldSafe.copy(alpha = 0.6f) else CharcoalBorder
                        ),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isMonitoring) {
                    // Multi-bar animated waveform visualizer
                    Row(
                        modifier = Modifier.width(68.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val baseHeights = listOf(22.dp, 36.dp, 52.dp, 38.dp, 20.dp)
                        baseHeights.forEachIndexed { index, baseHeight ->
                            val scaleFactor = if (index % 2 == 0) pulseMultiplier else (1.3f - pulseMultiplier)
                            val animatedHeight = baseHeight * scaleFactor.coerceIn(0.35f, 1.25f)
                            Box(
                                modifier = Modifier
                                    .width(6.dp)
                                    .height(animatedHeight)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(
                                                EmeraldSafe,
                                                CyanAccent
                                            )
                                        )
                                    )
                            )
                        }
                    }
                } else {
                    Icon(
                        imageVector = Icons.Default.MicOff,
                        contentDescription = null,
                        tint = TextTertiary,
                        modifier = Modifier.size(40.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = if (isMonitoring) "16,000 Hz MONO CONTINUOUS ANALYSIS" else "ACOUSTIC SHIELD PAUSED",
            color = if (isMonitoring) EmeraldSafe else TextTertiary,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            letterSpacing = 0.5.sp
        )
        Text(
            text = "In-memory 10s circular RAM buffer • Zero audio saved during monitoring",
            color = TextTertiary,
            fontSize = 10.sp
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Sensitivity Switcher Pill
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(CharcoalSurfaceElevated)
                .padding(3.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            listOf("Low", "Medium", "High").forEach { level ->
                val isSelected = sensitivity == level
                Surface(
                    color = if (isSelected) BluePrimary else Color.Transparent,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .clickable { repository.setAudioSensitivity(level) }
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "$level Sensitivity",
                        color = if (isSelected) Color.White else TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Current Stream Risk Classification Card
        val riskBorderColor = when (lastDetection.riskLevel) {
            RiskLevel.HIGH -> CrimsonEmergency
            RiskLevel.MEDIUM -> AmberWarning
            RiskLevel.LOW -> EmeraldSafe
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CharcoalSurface),
            border = BorderStroke(1.dp, riskBorderColor.copy(alpha = 0.5f)),
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "REAL-TIME STREAM CLASSIFICATION",
                        color = TextSecondary,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                    Surface(
                        color = riskBorderColor.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "${lastDetection.riskLevel} RISK",
                            color = riskBorderColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = lastDetection.detectedClass,
                    color = riskBorderColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )

                if (lastDetection.details.isNotBlank()) {
                    Text(
                        text = lastDetection.details,
                        color = TextTertiary,
                        fontSize = 11.sp
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                LinearProgressIndicator(
                    progress = { lastDetection.confidence },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = riskBorderColor,
                    trackColor = CharcoalBorder
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Interactive Testing Triggers
        Text(
            text = "TRIGGER TEST SAMPLES (HACKATHON DEMO)",
            color = TextSecondary,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp,
            modifier = Modifier.align(Alignment.Start)
        )
        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Scream Trigger
            OutlinedButton(
                onClick = {
                    repository.updateAudioDetection(
                        AudioDetectionResult(
                            detectedClass = "Scream / Severe Acoustic Distress",
                            confidence = 0.96f,
                            timestamp = System.currentTimeMillis(),
                            riskLevel = RiskLevel.HIGH,
                            details = "Fast-track scream escalation (>72%)"
                        )
                    )
                    repository.confirmSos("AI_ACOUSTIC_SCREAM")
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, CrimsonEmergency.copy(alpha = 0.6f)),
                colors = ButtonDefaults.outlinedButtonColors(containerColor = CrimsonEmergency.copy(alpha = 0.08f))
            ) {
                Text("Scream (96%)", fontSize = 11.sp, color = CrimsonEmergency, fontWeight = FontWeight.Bold)
            }

            // Threat Trigger
            OutlinedButton(
                onClick = {
                    repository.updateAudioDetection(
                        AudioDetectionResult(
                            detectedClass = "Model 4 Harmful Threat Context",
                            confidence = 0.88f,
                            timestamp = System.currentTimeMillis(),
                            riskLevel = RiskLevel.HIGH,
                            details = "\"Get in the car, don't scream\" (Score: 88% > 5%)"
                        )
                    )
                    repository.confirmSos("AI_CONVERSATION_CLASSIFIER")
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, AmberWarning.copy(alpha = 0.6f)),
                colors = ButtonDefaults.outlinedButtonColors(containerColor = AmberWarning.copy(alpha = 0.08f))
            ) {
                Text("Threat (88%)", fontSize = 11.sp, color = AmberWarning, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Calm Speech Trigger
            OutlinedButton(
                onClick = {
                    repository.updateAudioDetection(
                        AudioDetectionResult(
                            detectedClass = "Normal Speech / Ambient Calm",
                            confidence = 0.98f,
                            timestamp = System.currentTimeMillis(),
                            riskLevel = RiskLevel.LOW,
                            details = "Benign baseline conversation"
                        )
                    )
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, CharcoalBorder),
                colors = ButtonDefaults.outlinedButtonColors(containerColor = CharcoalSurface)
            ) {
                Text("Calm Speech", fontSize = 11.sp, color = EmeraldSafe)
            }

            // Impact Trigger
            OutlinedButton(
                onClick = {
                    repository.updateAudioDetection(
                        AudioDetectionResult(
                            detectedClass = "Physical Collision / Blunt Impact",
                            confidence = 0.75f,
                            timestamp = System.currentTimeMillis(),
                            riskLevel = RiskLevel.MEDIUM,
                            details = "Sudden high-energy acoustic impulse"
                        )
                    )
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, CharcoalBorder),
                colors = ButtonDefaults.outlinedButtonColors(containerColor = CharcoalSurface)
            ) {
                Text("Impact (75%)", fontSize = 11.sp, color = TextPrimary)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Live Audio History Log
        Text(
            text = "ACOUSTIC EVENT LOG",
            color = TextSecondary,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp,
            modifier = Modifier.align(Alignment.Start)
        )
        Spacer(modifier = Modifier.height(6.dp))

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            items(audioHistory) { event ->
                val timeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(event.timestamp))
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp),
                    colors = CardDefaults.cardColors(containerColor = CharcoalSurface),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = event.detectedClass,
                                color = TextPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            if (event.details.isNotBlank()) {
                                Text(event.details, color = TextTertiary, fontSize = 10.sp)
                            }
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "${(event.confidence * 100).toInt()}%",
                                color = when (event.riskLevel) {
                                    RiskLevel.HIGH -> CrimsonEmergency
                                    RiskLevel.MEDIUM -> AmberWarning
                                    RiskLevel.LOW -> EmeraldSafe
                                },
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                            Text(timeStr, color = TextTertiary, fontSize = 9.sp)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
    }
}
