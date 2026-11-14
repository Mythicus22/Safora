package com.example.safora.features.monitoring

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
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
import com.example.safora.data.model.AudioDetectionResult
import com.example.safora.data.repository.SaforaRepository
import com.example.safora.services.audio.AudioMonitoringService
import com.example.safora.ui.theme.*

@Composable
fun MonitoringScreen(
    onBack: () -> Unit
) {
    val repository = SaforaRepository.instance
    val context = LocalContext.current
    val isMonitoring by repository.isAudioMonitoring.collectAsState()
    val lastDetection by repository.lastAudioDetection.collectAsState()

    val pulse = rememberInfiniteTransition(label = "mic_pulse").animateFloat(
        initialValue = 0.95f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = EaseInOutQuad),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
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
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Live Acoustic Shield",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }

        Spacer(modifier = Modifier.height(30.dp))

        // Mic Visualizer Indicator
        Box(
            modifier = Modifier
                .size(140.dp)
                .clip(CircleShape)
                .background(
                    if (isMonitoring) EmeraldSafe.copy(alpha = 0.15f) else CharcoalSurfaceElevated
                ),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .clip(CircleShape)
                    .background(
                        if (isMonitoring) EmeraldSafe.copy(alpha = 0.35f) else CharcoalBorder
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isMonitoring) Icons.Default.Mic else Icons.Default.MicOff,
                    contentDescription = null,
                    tint = if (isMonitoring) EmeraldSafe else TextTertiary,
                    modifier = Modifier.size(48.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = if (isMonitoring) "LOCAL ML MODEL ACTIVE" else "MONITORING STOPPED",
            color = if (isMonitoring) EmeraldSafe else TextSecondary,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp
        )
        Text(
            text = "Audio analyzed on-device only. Zero audio sent to cloud.",
            color = TextTertiary,
            fontSize = 11.sp
        )

        Spacer(modifier = Modifier.height(28.dp))

        // Last Detection Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CharcoalSurfaceElevated),
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "LATEST ACOUSTIC CLASSIFICATION",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Detected Class", color = TextSecondary, fontSize = 13.sp)
                    Text(
                        text = lastDetection.detectedClass,
                        color = if (lastDetection.detectedClass.contains("Scream")) CrimsonEmergency else EmeraldSafe,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Model Confidence", color = TextSecondary, fontSize = 13.sp)
                    Text(
                        text = "${(lastDetection.confidence * 100).toInt()}%",
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Simulation Triggers for live testing
        Text(
            text = "AUDIO DETECTION TEST SUITE",
            color = TextSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = {
                    repository.updateAudioDetection(
                        AudioDetectionResult("Scream / Distress Call", 0.95f, System.currentTimeMillis())
                    )
                    repository.triggerPossibleDistress("Scream / Distress Call")
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Scream Trigger", fontSize = 11.sp, color = CrimsonEmergency)
            }

            OutlinedButton(
                onClick = {
                    repository.updateAudioDetection(
                        AudioDetectionResult("High Impact Thud", 0.88f, System.currentTimeMillis())
                    )
                    repository.triggerPossibleDistress("High Impact Thud")
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Impact Trigger", fontSize = 11.sp, color = AmberWarning)
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = {
                if (isMonitoring) {
                    AudioMonitoringService.stop(context)
                } else {
                    AudioMonitoringService.start(context)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isMonitoring) CrimsonEmergencyDark else EmeraldSafeDark
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                if (isMonitoring) "Pause Audio Shield" else "Resume Audio Shield",
                fontWeight = FontWeight.Bold
            )
        }
    }
}
