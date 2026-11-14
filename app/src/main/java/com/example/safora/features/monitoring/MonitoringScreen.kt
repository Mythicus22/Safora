package com.example.safora.features.monitoring

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.safora.core.utils.PermissionHelper
import com.example.safora.data.model.AudioDetectionResult
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

    var showMicDialog by remember { mutableStateOf(false) }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            AudioMonitoringService.start(context)
        }
    }

    // Pulsing waveform bars animation
    val infiniteTransition = rememberInfiniteTransition(label = "audio_bars")
    val barHeightMultiplier by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bars"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CharcoalDark)
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // App bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Live Acoustic Shield",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Simulated Waveform Bars / Mic Pulse
        Box(
            modifier = Modifier
                .size(130.dp)
                .clip(CircleShape)
                .background(
                    if (isMonitoring) EmeraldSafe.copy(alpha = 0.15f) else CharcoalSurfaceElevated
                ),
            contentAlignment = Alignment.Center
        ) {
            if (isMonitoring) {
                // Real-time animated audio visualizer bars
                Row(
                    modifier = Modifier.width(70.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val heights = listOf(24.dp, 40.dp, 55.dp, 34.dp, 20.dp)
                    heights.forEachIndexed { index, defaultHeight ->
                        val animatedHeight = if (isMonitoring) {
                            val factor = if (index % 2 == 0) barHeightMultiplier else (1.3f - barHeightMultiplier)
                            defaultHeight * factor.coerceIn(0.4f, 1.2f)
                        } else 6.dp

                        Box(
                            modifier = Modifier
                                .width(6.dp)
                                .height(animatedHeight)
                                .clip(RoundedCornerShape(3.dp))
                                .background(EmeraldSafe)
                        )
                    }
                }
            } else {
                Icon(
                    imageVector = Icons.Default.MicOff,
                    contentDescription = null,
                    tint = TextTertiary,
                    modifier = Modifier.size(44.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = if (isMonitoring) "LOCAL ON-DEVICE INFERENCE ACTIVE" else "ACOUSTIC SHIELD PAUSED",
            color = if (isMonitoring) EmeraldSafe else TextSecondary,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp
        )
        Text(
            text = "Foreground Microphone Service • Zero Audio Leaves Device",
            color = TextTertiary,
            fontSize = 11.sp
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Sensitivity Switcher Pill
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(CharcoalSurfaceElevated)
                .padding(4.dp),
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

        Spacer(modifier = Modifier.height(18.dp))

        // Classification Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CharcoalSurfaceElevated),
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "CURRENT AUDIO STREAM CLASSIFICATION",
                    color = TextSecondary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = lastDetection.detectedClass,
                            color = when (lastDetection.riskLevel) {
                                com.example.safora.data.model.RiskLevel.HIGH -> CrimsonEmergency
                                com.example.safora.data.model.RiskLevel.MEDIUM -> AmberWarning
                                com.example.safora.data.model.RiskLevel.LOW -> EmeraldSafe
                            },
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
                    }
                    Surface(
                        color = when (lastDetection.riskLevel) {
                            com.example.safora.data.model.RiskLevel.HIGH -> CrimsonEmergency.copy(alpha = 0.2f)
                            com.example.safora.data.model.RiskLevel.MEDIUM -> AmberWarning.copy(alpha = 0.2f)
                            com.example.safora.data.model.RiskLevel.LOW -> EmeraldSafe.copy(alpha = 0.2f)
                        },
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "${lastDetection.riskLevel} RISK",
                            color = when (lastDetection.riskLevel) {
                                com.example.safora.data.model.RiskLevel.HIGH -> CrimsonEmergency
                                com.example.safora.data.model.RiskLevel.MEDIUM -> AmberWarning
                                com.example.safora.data.model.RiskLevel.LOW -> EmeraldSafe
                            },
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { lastDetection.confidence },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = when (lastDetection.riskLevel) {
                        com.example.safora.data.model.RiskLevel.HIGH -> CrimsonEmergency
                        com.example.safora.data.model.RiskLevel.MEDIUM -> AmberWarning
                        com.example.safora.data.model.RiskLevel.LOW -> EmeraldSafe
                    },
                    trackColor = CharcoalBorder
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Audio Detection Test Controls
        Text(
            text = "SIMULATE CLASSIFICATION SAMPLES",
            color = TextSecondary,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.Start)
        )
        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = {
                    repository.updateAudioDetection(
                        AudioDetectionResult(
                            detectedClass = "Scream / Severe Acoustic Distress",
                            confidence = 0.96f,
                            timestamp = System.currentTimeMillis(),
                            riskLevel = com.example.safora.data.model.RiskLevel.HIGH,
                            details = "Fast-track scream escalation (>72%)"
                        )
                    )
                    repository.confirmSos("AI_ACOUSTIC_SCREAM")
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Scream (96%)", fontSize = 11.sp, color = CrimsonEmergency)
            }

            OutlinedButton(
                onClick = {
                    repository.updateAudioDetection(
                        AudioDetectionResult(
                            detectedClass = "Model 4 Harmful Threat Context",
                            confidence = 0.88f,
                            timestamp = System.currentTimeMillis(),
                            riskLevel = com.example.safora.data.model.RiskLevel.HIGH,
                            details = "\"Get in the car, don't scream\" (Score: 88% > 5%)"
                        )
                    )
                    repository.confirmSos("AI_CONVERSATION_CLASSIFIER")
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Threat (88%)", fontSize = 11.sp, color = AmberWarning)
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = {
                    repository.updateAudioDetection(
                        AudioDetectionResult(
                            detectedClass = "Normal Speech / Ambient Calm",
                            confidence = 0.98f,
                            timestamp = System.currentTimeMillis(),
                            riskLevel = com.example.safora.data.model.RiskLevel.LOW,
                            details = "Benign baseline conversation"
                        )
                    )
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Normal (98%)", fontSize = 11.sp, color = EmeraldSafe)
            }

            OutlinedButton(
                onClick = {
                    repository.updateAudioDetection(
                        AudioDetectionResult(
                            detectedClass = "Sudden High-G Acoustic Impact",
                            confidence = 0.91f,
                            timestamp = System.currentTimeMillis(),
                            riskLevel = com.example.safora.data.model.RiskLevel.HIGH,
                            details = "Blunt physical impact detected"
                        )
                    )
                    repository.confirmSos("AI_PHYSICAL_IMPACT")
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Impact (91%)", fontSize = 11.sp, color = AmberWarning)
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Audio history log
        Text(
            text = "RECENT AUDIO INFERENCES",
            color = TextSecondary,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.Start)
        )
        Spacer(modifier = Modifier.height(6.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(audioHistory) { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(CharcoalSurface)
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = item.detectedClass,
                        color = if (item.detectedClass.contains("Scream")) CrimsonEmergency else TextPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(item.timestamp)),
                        color = TextTertiary,
                        fontSize = 10.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Button(
            onClick = {
                if (isMonitoring) {
                    AudioMonitoringService.stop(context)
                } else {
                    if (PermissionHelper.hasAudioPermission(context)) {
                        AudioMonitoringService.start(context)
                    } else {
                        showMicDialog = true
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isMonitoring) CrimsonEmergencyDark else EmeraldSafeDark
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                if (isMonitoring) "Pause Safety Shield" else "Activate Safety Shield",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
        }
    }

    if (showMicDialog) {
        AlertDialog(
            onDismissRequest = { showMicDialog = false },
            title = { Text("Microphone Access Required", color = TextPrimary) },
            text = {
                Text(
                    "SAFORA requires microphone permission to monitor ambient audio locally on your device for distress screams. Audio is never recorded to disk or sent to the cloud.",
                    color = TextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showMicDialog = false
                        micPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BluePrimary)
                ) {
                    Text("Grant Permission")
                }
            },
            dismissButton = {
                TextButton(onClick = { showMicDialog = false }) {
                    Text("Cancel")
                }
            },
            containerColor = CharcoalSurfaceElevated
        )
    }
}
