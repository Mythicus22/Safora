package com.example.safora.features.emergency

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.font.FontFamily
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

    var showCancelDialog by remember { mutableStateOf(false) }
    var countdownSeconds by remember { mutableStateOf(5) }

    LaunchedEffect(incident.state) {
        if (incident.state == IncidentState.VERIFYING) {
            countdownSeconds = 5
            while (countdownSeconds > 0) {
                delay(1000)
                countdownSeconds -= 1
            }
            if (incident.state == IncidentState.VERIFYING) {
                repository.confirmSos("AUTOMATIC_VERIFIED_DISTRESS")
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(if (incident.state == IncidentState.VERIFYING) CharcoalDark else CharcoalDark)
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
                    text = if (incident.state == IncidentState.VERIFYING) "STAGE 1: VERIFYING DISTRESS..." else "STAGE 2: CONFIRMED EMERGENCY SOS",
                    color = if (incident.state == IncidentState.VERIFYING) AmberWarning else CrimsonEmergency,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // STAGE 1: VERIFYING COUNTDOWN
        if (incident.state == IncidentState.VERIFYING) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CharcoalSurfaceElevated),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "POSSIBLE DISTRESS DETECTED",
                        color = AmberWarning,
                        fontWeight = FontWeight.Black,
                        fontSize = 17.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = incident.triggerSource,
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    // Countdown Ring Box
                    Box(
                        modifier = Modifier
                            .size(110.dp)
                            .clip(CircleShape)
                            .background(AmberWarning.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "$countdownSeconds",
                            color = Color.White,
                            fontWeight = FontWeight.Black,
                            fontSize = 50.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "Auto-triggering SOS & wearable camera in $countdownSeconds s",
                        color = TextTertiary,
                        fontSize = 11.sp
                    )
                    Spacer(modifier = Modifier.height(20.dp))

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
                            Text("False Alarm / I'm Safe", color = EmeraldSafe, fontSize = 11.sp)
                        }

                        Button(
                            onClick = {
                                repository.confirmSos("USER_CONFIRMED_IMMEDIATE")
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
            // STAGE 2: CONFIRMED EMERGENCY SOS
            Text(
                text = "EMERGENCY DISPATCH IN PROGRESS",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black,
                color = CrimsonEmergency
            )
            Text(
                text = "Incident ID: ${incident.id} • Trigger: ${incident.triggerSource}",
                color = TextSecondary,
                fontSize = 11.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Action: Direct Call 112
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

            Spacer(modifier = Modifier.height(14.dp))

            // Quick Alert Share Button
            OutlinedButton(
                onClick = {
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(
                            Intent.EXTRA_TEXT,
                            "EMERGENCY! I need immediate help. My current SAFORA GPS location is: https://maps.google.com/?q=${location.first},${location.second} (Incident ID: ${incident.id})"
                        )
                    }
                    context.startActivity(Intent.createChooser(shareIntent, "Share Emergency Location"))
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.Share, contentDescription = null, tint = BluePrimary, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Share Live GPS & Incident Link via SMS / WhatsApp", color = BluePrimary, fontSize = 12.sp)
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Dispatch Metrics Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CharcoalSurfaceElevated),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "INCIDENT DISPATCH COORDINATION",
                        color = TextSecondary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("GPS Location", color = TextSecondary, fontSize = 12.sp)
                        Text(
                            "${"%.4f".format(location.first)}, ${"%.4f".format(location.second)} (Accuracy: 5m)",
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("ESP32 Wearable Camera", color = TextSecondary, fontSize = 12.sp)
                        Text(
                            if (wearable.isRecording) "RECORDING MicroSD SEGMENTS" else "STANDBY",
                            color = if (wearable.isRecording) CrimsonEmergency else TextTertiary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Emergency Contacts Alerted", color = TextSecondary, fontSize = 12.sp)
                        Text(
                            "${contacts.count { it.isEmergencyContact }} Contacts (${contacts.filter { it.isEmergencyContact }.joinToString { it.name.split(" ")[0] }})",
                            color = EmeraldSafe,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Evidence Encryption", color = TextSecondary, fontSize = 12.sp)
                        Text(
                            "AES-256 GCM Authenticated",
                            color = CyanAccent,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Live Timeline Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CharcoalSurface),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "INCIDENT AUDIT LOG",
                        color = TextSecondary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    incident.timeline.forEach { entry ->
                        Text(
                            text = entry,
                            color = TextPrimary,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Actions Row
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
                    onClick = { showCancelDialog = true },
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

    if (showCancelDialog) {
        AlertDialog(
            onDismissRequest = { showCancelDialog = false },
            title = { Text("Resolve Active Emergency?", color = TextPrimary) },
            text = {
                Text(
                    "This will stop wearable recording, notify responders that you are safe, and archive the incident.",
                    color = TextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        repository.resolveIncident()
                        showCancelDialog = false
                        onDismissToDashboard()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldSafeDark)
                ) {
                    Text("I Am Safe (Resolve)")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCancelDialog = false }) {
                    Text("Keep Active")
                }
            },
            containerColor = CharcoalSurfaceElevated
        )
    }
}
