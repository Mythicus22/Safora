package com.example.safora.features.dashboard

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
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
import com.example.safora.core.utils.PermissionHelper
import com.example.safora.data.model.DeviceConnectionState
import com.example.safora.data.model.IncidentState
import com.example.safora.data.repository.SaforaRepository
import com.example.safora.services.audio.AudioMonitoringService
import com.example.safora.services.location.SaforaLocationClient
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
    val context = LocalContext.current
    val repository = remember { SaforaRepository.getInstance(context) }

    val user by repository.user.collectAsState()
    val wearable by repository.wearable.collectAsState()
    val incident by repository.currentIncident.collectAsState()
    val isMonitoring by repository.isAudioMonitoring.collectAsState()
    val location by repository.currentLocation.collectAsState()
    val isRealGps by repository.isRealLocationLocked.collectAsState()
    val contacts by repository.contacts.collectAsState()
    val isDemoMode by repository.isDemoMode.collectAsState()

    var showMicPermissionDialog by remember { mutableStateOf(false) }

    // Launcher for on-demand audio recording permission
    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            AudioMonitoringService.start(context)
        }
    }

    // Auto-query live GPS on dashboard launch if permission granted
    LaunchedEffect(Unit) {
        if (PermissionHelper.hasLocationPermission(context)) {
            val locationClient = SaforaLocationClient(context)
            locationClient.requestCurrentLocation()
        }
    }

    // Auto navigate to emergency screen if incident enters verifying or active emergency
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

        // Top Header
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
                                text = "DEMO MODE",
                                color = AmberWarning,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Text(
                    text = if (user.name.isNotBlank()) "Protected: ${user.name}" else "System Active",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }

            // Emergency Contacts pill button
            Surface(
                color = CharcoalSurfaceElevated,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.clickable { onNavigateToContacts() }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.People,
                        contentDescription = "Contacts",
                        tint = CrimsonEmergency,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "${contacts.count { it.isEmergencyContact }} Contacts",
                        color = TextPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Active Emergency Banner (if returning from resolved/cancelled or if active)
        if (incident.state == IncidentState.CONFIRMED_SOS || incident.state == IncidentState.VERIFYING) {
            Spacer(modifier = Modifier.height(14.dp))
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigateToEmergency() },
                color = CrimsonEmergencyDark,
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(Color.White)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "ACTIVE EMERGENCY IN PROGRESS",
                                color = Color.White,
                                fontWeight = FontWeight.Black,
                                fontSize = 12.sp
                            )
                            Text(
                                text = "Incident ${incident.id} • Tap to view emergency controls",
                                color = Color.White.copy(alpha = 0.85f),
                                fontSize = 11.sp
                            )
                        }
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Large SOS Button Section
        LargeSosButton(
            isPulsing = true,
            onClick = {
                repository.confirmSos("MANUAL_APP_BUTTON")
                onNavigateToEmergency()
            }
        )

        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "ONE-TOUCH INSTANT SOS DISPATCH",
            color = CrimsonEmergency,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp
        )
        Text(
            text = "Dispatches SMS to trusted contacts, starts camera & dials 112",
            color = TextTertiary,
            fontSize = 10.sp
        )

        Spacer(modifier = Modifier.height(22.dp))

        // Wearable Necklace Card
        StatusCard(
            title = "WEARABLE NECKLACE (ESP32-CAM)",
            statusText = when (wearable.state) {
                DeviceConnectionState.CONNECTED -> "${wearable.name} (${wearable.batteryLevel}% Battery - BLE Connected)"
                DeviceConnectionState.CONNECTING -> "Connecting to ESP32 BLE GATT..."
                DeviceConnectionState.SCANNING -> "Scanning for SAFORA BLE..."
                DeviceConnectionState.DISCONNECTED -> "Wearable Standby / Disconnected"
            },
            isActive = wearable.state == DeviceConnectionState.CONNECTED,
            actionText = if (wearable.state == DeviceConnectionState.CONNECTED) "MANAGE" else "CONNECT",
            onActionClick = onNavigateToPairing
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Audio Monitoring Status Card
        StatusCard(
            title = "LOCAL ACOUSTIC SAFETY SHIELD",
            statusText = if (isMonitoring) "Actively Analyzing Local Audio for Distress" else "Acoustic Shield Paused",
            isActive = isMonitoring,
            actionText = if (isMonitoring) "VIEW SHIELD" else "START",
            onActionClick = {
                if (isMonitoring) {
                    onNavigateToMonitoring()
                } else {
                    if (PermissionHelper.hasAudioPermission(context)) {
                        AudioMonitoringService.start(context)
                    } else {
                        showMicPermissionDialog = true
                    }
                }
            }
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Location & Responder Status Card
        StatusCard(
            title = if (isRealGps) "LIVE PHONE GPS LOCATION" else "GPS LOCATION (ESTIMATED)",
            statusText = "${"%.4f".format(location.first)}, ${"%.4f".format(location.second)} • 3 Responders Nearby",
            isActive = isRealGps,
            actionText = "VIEW MAP",
            onActionClick = onNavigateToMap
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Safety Contacts Summary
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CharcoalSurface),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "TRUSTED EMERGENCY NETWORK",
                        color = TextSecondary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                    TextButton(onClick = onNavigateToContacts) {
                        Text("Manage", color = BluePrimary, fontSize = 11.sp)
                    }
                }
                if (contacts.isEmpty()) {
                    Text(
                        text = "No emergency contacts added yet. Tap Manage to import from your phonebook.",
                        color = AmberWarning,
                        fontSize = 11.sp
                    )
                } else {
                    Text(
                        text = "${contacts.count { it.isEmergencyContact }} Emergency contacts ready to receive cellular SOS alerts.",
                        color = TextPrimary,
                        fontSize = 11.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Demo Simulation Box for Hackathon Presentation
        if (isDemoMode) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CharcoalSurfaceElevated),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "Demo Simulation Quick Controls",
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
                            Text("Simulate Scream", fontSize = 11.sp, color = CrimsonEmergency)
                        }

                        OutlinedButton(
                            onClick = {
                                val nextState = if (wearable.state == DeviceConnectionState.CONNECTED) {
                                    DeviceConnectionState.DISCONNECTED
                                } else {
                                    DeviceConnectionState.CONNECTED
                                }
                                repository.updateWearable(wearable.copy(state = nextState))
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

    // On-Demand Mic Permission Dialog
    if (showMicPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showMicPermissionDialog = false },
            title = { Text("Microphone Access Required", color = TextPrimary) },
            text = {
                Text(
                    "SAFORA runs a local acoustic model on-device to classify distress screams in real-time. Audio is analyzed locally and never uploaded to the cloud.",
                    color = TextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showMicPermissionDialog = false
                        audioPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BluePrimary)
                ) {
                    Text("Grant Permission")
                }
            },
            dismissButton = {
                TextButton(onClick = { showMicPermissionDialog = false }) {
                    Text("Cancel")
                }
            },
            containerColor = CharcoalSurfaceElevated
        )
    }
}
