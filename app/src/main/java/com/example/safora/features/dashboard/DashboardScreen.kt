package com.example.safora.features.dashboard

import android.content.Context
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
import com.example.safora.data.model.DeviceConnectionState
import com.example.safora.data.model.IncidentState
import com.example.safora.data.repository.SaforaRepository
import com.example.safora.services.audio.AudioMonitoringService
import com.example.safora.ui.components.LargeSosButton
import com.example.safora.ui.components.StatusCard
import com.example.safora.ui.theme.*

@Composable
fun DashboardScreen(
    onNavigateToPairing: () -> Unit,
    onNavigateToMonitoring: () -> Unit,
    onNavigateToEmergency: () -> Unit,
    onNavigateToContacts: () -> Unit,
    onNavigateToMap: () -> Unit
) {
    val repository = SaforaRepository.instance
    val context = LocalContext.current

    val user by repository.user.collectAsState()
    val wearable by repository.wearable.collectAsState()
    val incident by repository.currentIncident.collectAsState()
    val isMonitoring by repository.isAudioMonitoring.collectAsState()
    val location by repository.currentLocation.collectAsState()
    val contacts by repository.contacts.collectAsState()
    val isDemoMode by repository.isDemoMode.collectAsState()

    // Auto navigate to emergency screen if incident is in verifying or active
    LaunchedEffect(incident.state) {
        if (incident.state == IncidentState.VERIFYING ||
            incident.state == IncidentState.CONFIRMED_SOS ||
            incident.state == IncidentState.ALERTS_SENT ||
            incident.state == IncidentState.EVIDENCE_SYNCING
        ) {
            onNavigateToEmergency()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CharcoalDark)
            .padding(horizontal = 20.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "SAFORA",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    if (isDemoMode) {
                        Surface(
                            color = AmberWarning.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "DEMO",
                                color = AmberWarning,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Text(
                    text = "Hello, ${user.name}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }

            IconButton(onClick = onNavigateToContacts) {
                Badge(
                    containerColor = CrimsonEmergency,
                    contentColor = Color.White
                ) {
                    Text("${contacts.count { it.isEmergencyContact }}")
                }
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // SOS Button Section
        LargeSosButton(
            isPulsing = true,
            onClick = {
                repository.confirmSos("MANUAL_APP_BUTTON")
                onNavigateToEmergency()
            }
        )

        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "TAP SOS FOR INSTANT EMERGENCY DISPATCH",
            color = CrimsonEmergency,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Device Connection Pill / Card
        StatusCard(
            title = "WEARABLE NECKLACE",
            statusText = when (wearable.state) {
                DeviceConnectionState.CONNECTED -> "${wearable.name} (${wearable.batteryLevel}% Battery)"
                DeviceConnectionState.CONNECTING -> "Connecting to ESP32-CAM..."
                DeviceConnectionState.SCANNING -> "Scanning for BLE..."
                DeviceConnectionState.DISCONNECTED -> "Disconnected"
            },
            isActive = wearable.state == DeviceConnectionState.CONNECTED,
            actionText = if (wearable.state == DeviceConnectionState.CONNECTED) "MANAGE" else "CONNECT",
            onActionClick = onNavigateToPairing
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Audio Monitoring Status Card
        StatusCard(
            title = "ACOUSTIC SAFETY SHIELD",
            statusText = if (isMonitoring) "Actively Monitoring Local Audio" else "Audio Monitoring Paused",
            isActive = isMonitoring,
            actionText = if (isMonitoring) "VIEW" else "START",
            onActionClick = {
                if (isMonitoring) {
                    onNavigateToMonitoring()
                } else {
                    AudioMonitoringService.start(context)
                }
            }
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Location Card
        StatusCard(
            title = "GPS COORDINATES",
            statusText = "${"%.4f".format(location.first)}, ${"%.4f".format(location.second)} (Live GPS)",
            isActive = true,
            actionText = "MAP",
            onActionClick = onNavigateToMap
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Demo quick-action row for hackathon presentation
        if (isDemoMode) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CharcoalSurface),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "Demo Simulation Triggers",
                        color = AmberWarning,
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
                                repository.triggerPossibleDistress("Scream / Distress Call")
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Trigger Distress", fontSize = 11.sp, color = CrimsonEmergency)
                        }

                        OutlinedButton(
                            onClick = {
                                repository.updateWearable(
                                    wearable.copy(
                                        state = if (wearable.state == DeviceConnectionState.CONNECTED)
                                            DeviceConnectionState.DISCONNECTED
                                        else
                                            DeviceConnectionState.CONNECTED
                                    )
                                )
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                if (wearable.state == DeviceConnectionState.CONNECTED) "Disconnect BLE" else "Connect BLE",
                                fontSize = 11.sp,
                                color = CyanAccent
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(90.dp))
    }
}
