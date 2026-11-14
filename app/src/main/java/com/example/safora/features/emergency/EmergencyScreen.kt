package com.example.safora.features.emergency

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import com.example.safora.data.model.IncidentState
import com.example.safora.data.repository.SaforaRepository
import com.example.safora.ui.theme.*
import kotlinx.coroutines.delay

@Composable
fun EmergencyScreen(
    onDismissToDashboard: () -> Unit,
    onNavigateToEvidence: () -> Unit
) {
    val repository = SaforaRepository.instance
    val context = LocalContext.current
    val incident by repository.currentIncident.collectAsState()
    val wearable by repository.wearable.collectAsState()
    val contacts by repository.contacts.collectAsState()
    val location by repository.currentLocation.collectAsState()

    // Countdown timer for VERIFYING stage
    var countdownSeconds by remember { mutableStateOf(5) }

    LaunchedEffect(incident.state) {
        if (incident.state == IncidentState.VERIFYING) {
            countdownSeconds = 5
            while (countdownSeconds > 0) {
                delay(1000)
                countdownSeconds -= 1
            }
            // Auto confirm SOS if user didn't cancel
            if (incident.state == IncidentState.VERIFYING) {
                repository.confirmSos("AUTO_VERIFIED_DISTRESS")
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CharcoalDark)
            .padding(20.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(10.dp))

        // State Badge
        Surface(
            color = if (incident.state == IncidentState.VERIFYING) AmberWarning.copy(alpha = 0.2f) else CrimsonEmergency.copy(alpha = 0.25f),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.padding(top = 10.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (incident.state == IncidentState.VERIFYING) AmberWarning else CrimsonEmergency)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (incident.state == IncidentState.VERIFYING) "VERIFYING DISTRESS..." else "CONFIRMED EMERGENCY SOS",
                    color = if (incident.state == IncidentState.VERIFYING) AmberWarning else CrimsonEmergency,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // If in countdown verification stage
        if (incident.state == IncidentState.VERIFYING) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CharcoalSurfaceElevated),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "POSSIBLE DISTRESS DETECTED",
                        color = AmberWarning,
                        fontWeight = FontWeight.Black,
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = incident.triggerSource,
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "$countdownSeconds",
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        fontSize = 54.sp
                    )
                    Text(
                        text = "Confirming emergency unless cancelled",
                        color = TextTertiary,
                        fontSize = 11.sp
                    )
                    Spacer(modifier = Modifier.height(18.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                repository.cancelIncident()
                                onDismissToDashboard()
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("I Am Safe (Cancel)", color = EmeraldSafe)
                        }
                        Button(
                            onClick = {
                                repository.confirmSos("USER_CONFIRMED_PROMPT")
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = CrimsonEmergency),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Confirm SOS", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        } else {
            // Confirmed SOS Active screen
            Text(
                text = "EMERGENCY DISPATCH ACTIVE",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black,
                color = CrimsonEmergency
            )
            Text(
                text = "Incident ID: ${incident.id}",
                color = TextSecondary,
                fontSize = 12.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Action: Direct Call 112 (India National Emergency Number)
            Button(
                onClick = {
                    val intent = Intent(Intent.ACTION_DIAL).apply {
                        data = Uri.parse("tel:112")
                    }
                    context.startActivity(intent)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = CrimsonEmergency),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Default.Phone, contentDescription = null, tint = Color.White)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "CALL 112 (NATIONAL EMERGENCY)",
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = 14.sp
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Incident Metrics
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CharcoalSurfaceElevated),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "INCIDENT DISPATCH STATUS",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("GPS Coordinates", color = TextSecondary, fontSize = 13.sp)
                        Text(
                            "${"%.4f".format(location.first)}, ${"%.4f".format(location.second)}",
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Wearable Camera", color = TextSecondary, fontSize = 13.sp)
                        Text(
                            if (wearable.isRecording) "RECORDING (ESP32-CAM)" else "IDLE",
                            color = if (wearable.isRecording) CrimsonEmergency else TextTertiary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Emergency Contacts Alerted", color = TextSecondary, fontSize = 13.sp)
                        Text(
                            "${contacts.count { it.isEmergencyContact }} Contacts",
                            color = EmeraldSafe,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Evidence Transfer", color = TextSecondary, fontSize = 13.sp)
                        Text(
                            "Wi-Fi Sync (AES-256 GCM)",
                            color = CyanAccent,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Secondary Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onNavigateToEvidence,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("View Evidence", color = CyanAccent, fontSize = 12.sp)
                }

                Button(
                    onClick = {
                        repository.resolveIncident()
                        onDismissToDashboard()
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldSafeDark),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Resolve & End SOS", color = Color.White, fontSize = 12.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
    }
}
