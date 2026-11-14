package com.example.safora.features.dashboard

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.safora.core.utils.PermissionHelper
import com.example.safora.data.model.DeviceConnectionState
import com.example.safora.data.model.IncidentState
import com.example.safora.data.repository.SaforaRepository
import com.example.safora.services.audio.AudioMonitoringService
import com.example.safora.services.audio.model.ModelDownloadManager
import com.example.safora.services.location.SaforaLocationClient
import com.example.safora.ui.theme.*

@Composable
fun DashboardScreen(
    onNavigateToPairing: () -> Unit,
    onNavigateToMonitoring: () -> Unit,
    onNavigateToEmergency: () -> Unit,
    onNavigateToContacts: () -> Unit,
    onNavigateToMap: () -> Unit,
    onNavigateToProfile: () -> Unit = {}
) {
    val context = LocalContext.current
    val repository = remember { SaforaRepository.getInstance(context) }
    val modelManager = remember { ModelDownloadManager.getInstance(context) }

    val user by repository.user.collectAsState()
    val wearable by repository.wearable.collectAsState()
    val incident by repository.currentIncident.collectAsState()
    val isMonitoring by repository.isAudioMonitoring.collectAsState()
    val location by repository.currentLocation.collectAsState()
    val isRealGps by repository.isRealLocationLocked.collectAsState()
    val contacts by repository.contacts.collectAsState()
    val isDemoMode by repository.isDemoMode.collectAsState()
    val areAllModelsDownloaded by modelManager.areAllModelsDownloaded.collectAsState()

    // Auto-query live GPS on dashboard launch and auto-start mic monitoring if models are ready
    LaunchedEffect(Unit) {
        if (PermissionHelper.hasLocationPermission(context)) {
            val locationClient = SaforaLocationClient(context)
            locationClient.requestCurrentLocation()
        }
        if (modelManager.areAllDownloaded() && PermissionHelper.hasAudioPermission(context)) {
            AudioMonitoringService.start(context)
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

    // Concentric pulse animation for SOS button
    val infiniteTransition = rememberInfiniteTransition(label = "sos_pulse")
    val pulseScale1 by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = EaseOutQuad),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse_scale_1"
    )
    val pulseAlpha1 by infiniteTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = EaseOutQuad),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse_alpha_1"
    )

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
                        letterSpacing = 2.sp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        color = EmeraldSafe.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(EmeraldSafe)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "SHIELD LIVE",
                                color = EmeraldSafe,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                Text(
                    text = if (user.name.isNotBlank()) "Protected: ${user.name}" else "Guardian System Armed",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }

            // Quick Profile / Settings Icon
            IconButton(
                onClick = onNavigateToProfile,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(CharcoalSurfaceElevated)
            ) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = "Settings",
                    tint = TextPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // Active Emergency Banner (if ongoing)
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

        // Missing Models Alert Banner
        if (!areAllModelsDownloaded) {
            Spacer(modifier = Modifier.height(12.dp))
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigateToProfile() },
                colors = CardDefaults.cardColors(containerColor = AmberWarning.copy(alpha = 0.12f)),
                border = BorderStroke(1.dp, AmberWarning.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Warning",
                        tint = AmberWarning,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Safety AI Models Required",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = AmberWarning
                        )
                        Text(
                            text = "Download on-device models to enable offline scream & threat shield.",
                            fontSize = 10.sp,
                            color = TextSecondary
                        )
                    }
                    Text(
                        text = "DOWNLOAD",
                        color = CyanAccent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // =========================================================================
        // COCKPIT TELEMETRY SYMBOLS (Replaces bulky cards with interactive HUD glyphs)
        // =========================================================================
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Symbol 1: Wearable Necklace (ESP32-CAM)
            TelemetrySymbolCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.Watch,
                label = "WEARABLE",
                value = when (wearable.state) {
                    DeviceConnectionState.CONNECTED -> "${wearable.batteryLevel}%"
                    DeviceConnectionState.CONNECTING -> "LINKING"
                    DeviceConnectionState.SCANNING -> "SCAN"
                    DeviceConnectionState.DISCONNECTED -> "OFFLINE"
                },
                accentColor = when (wearable.state) {
                    DeviceConnectionState.CONNECTED -> EmeraldSafe
                    DeviceConnectionState.CONNECTING -> CyanAccent
                    DeviceConnectionState.SCANNING -> AmberWarning
                    DeviceConnectionState.DISCONNECTED -> TextTertiary
                },
                onClick = onNavigateToPairing
            )

            // Symbol 2: Acoustic Shield (Direct Jump to Live Monitoring!)
            TelemetrySymbolCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.GraphicEq,
                label = "LIVE SHIELD",
                value = if (isMonitoring) "ACTIVE" else "STANDBY",
                accentColor = if (isMonitoring) EmeraldSafe else AmberWarning,
                onClick = onNavigateToMonitoring
            )

            // Symbol 3: GPS Satellite Fix
            TelemetrySymbolCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.GpsFixed,
                label = "LOCATION",
                value = if (isRealGps) "LOCKED" else "ACQUIRING",
                accentColor = if (isRealGps) BluePrimary else AmberWarning,
                onClick = onNavigateToMap
            )

            // Symbol 4: Emergency Contacts
            TelemetrySymbolCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.Group,
                label = "CONTACTS",
                value = "${contacts.count { it.isEmergencyContact }} READY",
                accentColor = if (contacts.any { it.isEmergencyContact }) EmeraldSafe else CrimsonEmergency,
                onClick = onNavigateToContacts
            )
        }

        Spacer(modifier = Modifier.height(30.dp))

        // =========================================================================
        // CENTERPIECE: TACTICAL PULSING SOS EMERGENCY BUTTON
        // =========================================================================
        Box(
            modifier = Modifier
                .size(240.dp)
                .padding(10.dp),
            contentAlignment = Alignment.Center
        ) {
            // Outer radar pulse wave 1
            Box(
                modifier = Modifier
                    .size(200.dp)
                    .scale(pulseScale1)
                    .clip(CircleShape)
                    .background(CrimsonEmergency.copy(alpha = pulseAlpha1))
            )

            // Mid beacon ring
            Box(
                modifier = Modifier
                    .size(190.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            listOf(
                                CrimsonEmergency.copy(alpha = 0.35f),
                                Color.Transparent
                            )
                        )
                    )
            )

            // Core SOS Push Disc
            Surface(
                modifier = Modifier
                    .size(150.dp)
                    .clip(CircleShape)
                    .clickable {
                        repository.confirmSos("MANUAL_APP_BUTTON")
                        onNavigateToEmergency()
                    },
                shape = CircleShape,
                color = CrimsonEmergency,
                shadowElevation = 14.dp,
                border = BorderStroke(3.dp, Color.White.copy(alpha = 0.35f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color(0xFFFF5252),
                                    Color(0xFFD32F2F),
                                    Color(0xFF8E0000)
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "SOS",
                            tint = Color.White,
                            modifier = Modifier.size(38.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "SOS",
                            color = Color.White,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 2.sp
                        )
                        Text(
                            text = "ONE-TOUCH",
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "INSTANT EMERGENCY ESCALATION",
            color = CrimsonEmergency,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
        Text(
            text = "Dispatches SMS with GPS to contacts • Starts necklace camera • Dials 112",
            color = TextTertiary,
            fontSize = 10.sp
        )

        Spacer(modifier = Modifier.height(24.dp))

        // =========================================================================
        // QUICK ACCESS TACTICAL SHORTCUTS
        // =========================================================================
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Open Live Monitor
            OutlinedButton(
                onClick = onNavigateToMonitoring,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, CharcoalBorder),
                colors = ButtonDefaults.outlinedButtonColors(containerColor = CharcoalSurface)
            ) {
                Icon(
                    imageVector = Icons.Default.GraphicEq,
                    contentDescription = null,
                    tint = EmeraldSafe,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Live Monitor", color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }

            // Open Safety Map
            OutlinedButton(
                onClick = onNavigateToMap,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, CharcoalBorder),
                colors = ButtonDefaults.outlinedButtonColors(containerColor = CharcoalSurface)
            ) {
                Icon(
                    imageVector = Icons.Default.Map,
                    contentDescription = null,
                    tint = BluePrimary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Safety Map", color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(28.dp))
    }
}

@Composable
fun TelemetrySymbolCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String,
    value: String,
    accentColor: Color,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = CharcoalSurface),
        border = BorderStroke(1.dp, CharcoalBorder),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = accentColor,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = label,
                color = TextTertiary,
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                color = TextPrimary,
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }
    }
}
