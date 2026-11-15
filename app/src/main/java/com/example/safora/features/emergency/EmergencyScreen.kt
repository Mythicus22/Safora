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
    val context = LocalContext.current
    val repository = remember { SaforaRepository.getInstance(context) }
    val incident by repository.currentIncident.collectAsState()
    val wearable by repository.wearable.collectAsState()
    val contacts by repository.contacts.collectAsState()
    val location by repository.currentLocation.collectAsState()

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
                    text = if (incident.state == IncidentState.VERIFYING) "STAGE 1: VERIFYING DISTRESS..." else "STAGE 2: CONFIRMED EMERGENCY SOS",
                    color = if (incident.state == IncidentState.VERIFYING) AmberWarning else CrimsonEmergency,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // =========================================================================
        // STAGE 1: VERIFYING 5-SECOND COUNTDOWN WITH "I'M SAFE" BUTTON
        // =========================================================================
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
                        text = "Auto-triggering cellular SOS alerts & calls in $countdownSeconds s",
                        color = TextTertiary,
                        fontSize = 11.sp
                    )
                    Spacer(modifier = Modifier.height(20.dp))

                    // Big Primary "I'M SAFE / CANCEL SOS" Button
                    Button(
                        onClick = {
                            repository.cancelIncident()
                            onDismissToDashboard()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldSafe),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = "Safe",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "I'M SAFE — CANCEL SOS ($countdownSeconds s)",
                            color = Color.White,
                            fontWeight = FontWeight.Black,
                            fontSize = 15.sp,
                            letterSpacing = 0.5.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Secondary Immediate SOS Confirmation (skip 5s wait)
                    TextButton(
                        onClick = {
                            repository.confirmSos("USER_CONFIRMED_IMMEDIATE")
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Send SOS Immediately (Bypass 5s Wait)",
                            color = CrimsonEmergency,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        } else {
            // =========================================================================
            // STAGE 2: CONFIRMED EMERGENCY SOS ACTIVE
            // =========================================================================
            Text(
                text = "EMERGENCY DISPATCH IN PROGRESS",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black,
                color = CrimsonEmergency
            )
            Text(
                text = "Incident ID: ${incident.id.ifBlank { "ACTIVE" }} • Trigger: ${incident.triggerSource}",
                color = TextSecondary,
                fontSize = 11.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            // PROMINENT "I AM SAFE" CANCELLATION BUTTON
            Button(
                onClick = {
                    repository.resolveIncident()
                    onDismissToDashboard()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp),
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldSafe),
                shape = RoundedCornerShape(14.dp),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp)
            ) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(26.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "I AM SAFE — CANCEL & END SOS",
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = 15.sp,
                    letterSpacing = 0.5.sp
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

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
                    .height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = CrimsonEmergency),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Phone, contentDescription = null, tint = Color.White)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "CALL 112 (NATIONAL EMERGENCY)",
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = 13.sp
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Quick Alert Share Button
            OutlinedButton(
                onClick = {
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(
                            Intent.EXTRA_TEXT,
                            "EMERGENCY ALERT! I need immediate help. My current GPS location is: https://maps.google.com/?q=${location.first},${location.second} (Incident ID: ${incident.id})"
                        )
                    }
                    context.startActivity(Intent.createChooser(shareIntent, "Share Emergency Location"))
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.Share, contentDescription = null, tint = BluePrimary, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Share Emergency SMS / WhatsApp Broadcast", color = BluePrimary, fontSize = 12.sp)
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
                        Text("Live GPS Location", color = TextSecondary, fontSize = 12.sp)
                        Text(
                            "${"%.5f".format(location.first)}, ${"%.5f".format(location.second)}",
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
                        Text("Cellular SMS Alerts", color = TextSecondary, fontSize = 12.sp)
                        Text(
                            "Dispatched to Contacts & 112",
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

            Spacer(modifier = Modifier.height(16.dp))

            // Action: View Evidence
            OutlinedButton(
                onClick = onNavigateToEvidence,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("View Encrypted Evidence Segments", color = CyanAccent, fontSize = 12.sp)
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
    }
}
