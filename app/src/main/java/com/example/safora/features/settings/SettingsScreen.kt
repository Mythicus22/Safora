package com.example.safora.features.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.safora.core.utils.PermissionHelper
import com.example.safora.data.model.User
import com.example.safora.data.repository.SaforaRepository
import com.example.safora.services.audio.AudioMonitoringService
import com.example.safora.services.audio.model.ModelDownloadItem
import com.example.safora.services.audio.model.ModelDownloadManager
import com.example.safora.services.audio.model.ModelDownloadStatus
import com.example.safora.services.audio.model.SafetyModelType
import com.example.safora.ui.theme.*

@Composable
fun SettingsScreen(
    onNavigateToPairing: () -> Unit,
    onNavigateToContacts: () -> Unit
) {
    val context = LocalContext.current
    val repository = remember { SaforaRepository.getInstance(context) }
    val modelManager = remember { ModelDownloadManager.getInstance(context) }

    val user by repository.user.collectAsState()
    val isDemoMode by repository.isDemoMode.collectAsState()
    val isAudioMonitoring by repository.isAudioMonitoring.collectAsState()

    val modelsState by modelManager.modelsState.collectAsState()
    val areAllDownloaded by modelManager.areAllModelsDownloaded.collectAsState()

    var showEditProfileDialog by remember { mutableStateOf(false) }
    var editName by remember { mutableStateOf(user.name) }
    var editPhone by remember { mutableStateOf(user.phone) }
    var editBlood by remember { mutableStateOf(user.bloodGroup) }

    // Check actual Android runtime permissions
    val hasLocation = PermissionHelper.hasLocationPermission(context)
    val hasAudio = PermissionHelper.hasAudioPermission(context)
    val hasBle = PermissionHelper.hasBlePermission(context)
    val hasContacts = PermissionHelper.hasContactsPermission(context)
    val hasNotifications = PermissionHelper.hasNotificationPermission(context)

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted && areAllDownloaded) {
            AudioMonitoringService.start(context)
        }
    }

    // Auto-start continuous microphone monitoring when models are downloaded & mic permission is granted
    LaunchedEffect(areAllDownloaded, hasAudio) {
        if (areAllDownloaded && hasAudio) {
            AudioMonitoringService.start(context)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CharcoalDark)
            .padding(horizontal = 20.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "PROFILE & SETTINGS",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Black,
            color = Color.White
        )
        Text(
            text = "System permissions, on-device AI models and emergency identity",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary
        )

        Spacer(modifier = Modifier.height(18.dp))

        // Profile Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CharcoalSurfaceElevated),
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = user.name.ifBlank { "User Profile" },
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = TextPrimary
                        )
                        Text(
                            text = "${user.phone.ifBlank { "No Phone Set" }} • Blood: ${user.bloodGroup}",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }

                    TextButton(onClick = {
                        editName = user.name
                        editPhone = user.phone
                        editBlood = user.bloodGroup
                        showEditProfileDialog = true
                    }) {
                        Text("Edit", color = BluePrimary)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // =========================================================================
        // ON-DEVICE SAFETY AI MODELS (DOWNLOAD & LIFECYCLE MANAGEMENT)
        // =========================================================================
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "ON-DEVICE SAFETY AI MODELS",
                    color = TextSecondary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Required for offline acoustic scream, voice & threat detection",
                    color = TextTertiary,
                    fontSize = 10.sp
                )
            }
            if (!areAllDownloaded) {
                TextButton(
                    onClick = { modelManager.downloadAllModels() },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text("Download All", color = CyanAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Master Model Status & Mic Always-On Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (areAllDownloaded) EmeraldSafe.copy(alpha = 0.08f) else AmberWarning.copy(alpha = 0.08f)
            ),
            border = BorderStroke(
                1.dp,
                if (areAllDownloaded) EmeraldSafe.copy(alpha = 0.4f) else AmberWarning.copy(alpha = 0.4f)
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (areAllDownloaded) Icons.Default.CheckCircle else Icons.Default.Warning,
                            contentDescription = null,
                            tint = if (areAllDownloaded) EmeraldSafe else AmberWarning,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = if (areAllDownloaded) "All AI Models Downloaded & Active" else "Safety Models Missing",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = TextPrimary
                            )
                            Text(
                                text = if (areAllDownloaded) {
                                    if (hasAudio) "Microphone Active (Continuous 16 kHz Shield)" else "Microphone permission required"
                                } else {
                                    "Tap 'Download All' to enable offline detection"
                                },
                                fontSize = 11.sp,
                                color = if (areAllDownloaded) EmeraldSafe else AmberWarning
                            )
                        }
                    }

                    if (areAllDownloaded && !hasAudio) {
                        Button(
                            onClick = { audioPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO) },
                            colors = ButtonDefaults.buttonColors(containerColor = BluePrimary),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text("Grant Mic", fontSize = 10.sp)
                        }
                    } else if (!areAllDownloaded) {
                        Button(
                            onClick = { modelManager.downloadAllModels() },
                            colors = ButtonDefaults.buttonColors(containerColor = CyanAccent),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text("Download All", fontSize = 10.sp, color = CharcoalDark, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                if (areAllDownloaded && hasAudio) {
                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider(color = EmeraldSafe.copy(alpha = 0.2f))
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (isAudioMonitoring) EmeraldSafe else AmberWarning)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isAudioMonitoring) {
                                "Microphone is ON always in background • YAMNet, VAD & Vosk active"
                            } else {
                                "Microphone standby • Starting background acoustic monitor..."
                            },
                            fontSize = 10.sp,
                            color = TextSecondary
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Individual Model Cards
        modelsState.forEach { item ->
            ModelDownloadCard(
                item = item,
                onDownload = { modelManager.downloadModel(item.type) },
                onDelete = { modelManager.deleteModel(item.type) }
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Android System Permissions Section
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "ANDROID SYSTEM PERMISSIONS",
                color = TextSecondary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
            TextButton(
                onClick = {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                    }
                    context.startActivity(intent)
                }
            ) {
                Text("Open App Settings", color = CyanAccent, fontSize = 11.sp)
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CharcoalSurface),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                PermissionStatusRow("GPS Location (FusedLocation)", hasLocation)
                HorizontalDivider(color = CharcoalBorder, modifier = Modifier.padding(vertical = 8.dp))
                PermissionStatusRow("Microphone (Acoustic Shield)", hasAudio)
                HorizontalDivider(color = CharcoalBorder, modifier = Modifier.padding(vertical = 8.dp))
                PermissionStatusRow("Bluetooth LE (Wearable Link)", hasBle)
                HorizontalDivider(color = CharcoalBorder, modifier = Modifier.padding(vertical = 8.dp))
                PermissionStatusRow("Read Contacts (Address Book)", hasContacts)
                HorizontalDivider(color = CharcoalBorder, modifier = Modifier.padding(vertical = 8.dp))
                PermissionStatusRow("Push Notifications", hasNotifications)
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Safety Management Shortcuts
        Text(
            text = "SAFETY SHORTCUTS",
            color = TextSecondary,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(6.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CharcoalSurface),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column {
                ListItem(
                    headlineContent = { Text("Wearable Pairing & BLE", color = TextPrimary) },
                    supportingContent = { Text("ESP32-CAM necklace link & live GATT terminal", color = TextSecondary) },
                    trailingContent = {
                        IconButton(onClick = onNavigateToPairing) {
                            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextSecondary)
                        }
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
                HorizontalDivider(color = CharcoalBorder)
                ListItem(
                    headlineContent = { Text("Emergency Contacts", color = TextPrimary) },
                    supportingContent = { Text("Configure recipients for SOS alerts & live GPS", color = TextSecondary) },
                    trailingContent = {
                        IconButton(onClick = onNavigateToContacts) {
                            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextSecondary)
                        }
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Demo Mode Switch
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CharcoalSurface),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Hackathon Demo Mode", fontWeight = FontWeight.Bold, color = TextPrimary)
                    Text("Shows on-screen simulation triggers across screens", color = TextSecondary, fontSize = 11.sp)
                }
                Switch(
                    checked = isDemoMode,
                    onCheckedChange = { repository.updateDemoMode(it) }
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Reset Demo Data
        OutlinedButton(
            onClick = {
                repository.updateUser(User(name = "", phone = "", isOnboarded = false))
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp)
        ) {
            Text("Reset Demo Profile to Default", color = TextTertiary)
        }

        Spacer(modifier = Modifier.height(90.dp))
    }

    if (showEditProfileDialog) {
        AlertDialog(
            onDismissRequest = { showEditProfileDialog = false },
            title = { Text("Edit User Profile", color = TextPrimary) },
            text = {
                Column {
                    OutlinedTextField(
                        value = editName,
                        onValueChange = { editName = it },
                        label = { Text("Name") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = editPhone,
                        onValueChange = { editPhone = it },
                        label = { Text("Phone") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = editBlood,
                        onValueChange = { editBlood = it },
                        label = { Text("Blood Group") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        repository.updateUser(
                            user.copy(
                                name = editName,
                                phone = editPhone,
                                bloodGroup = editBlood,
                                isOnboarded = true
                            )
                        )
                        showEditProfileDialog = false
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditProfileDialog = false }) {
                    Text("Cancel")
                }
            },
            containerColor = CharcoalSurfaceElevated
        )
    }
}

@Composable
fun ModelDownloadCard(
    item: ModelDownloadItem,
    onDownload: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CharcoalSurface),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header Row: Model Title + Size Tag
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.type.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = TextPrimary,
                    modifier = Modifier.weight(1f)
                )
                Surface(
                    color = CharcoalSurfaceElevated,
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = item.type.sizeText,
                        color = CyanAccent,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = item.type.subtitle,
                color = TextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = item.type.description,
                color = TextTertiary,
                fontSize = 10.sp,
                lineHeight = 14.sp
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Download Progress Bar when DOWNLOADING
            if (item.status == ModelDownloadStatus.DOWNLOADING) {
                LinearProgressIndicator(
                    progress = { item.progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = CyanAccent,
                    trackColor = CharcoalBorder
                )
                Spacer(modifier = Modifier.height(6.dp))
            }

            // Action & Status Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Status Pill
                when (item.status) {
                    ModelDownloadStatus.DOWNLOADED -> {
                        Surface(
                            color = EmeraldSafe.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = EmeraldSafe,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "DOWNLOADED & READY",
                                    color = EmeraldSafe,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    ModelDownloadStatus.DOWNLOADING -> {
                        Surface(
                            color = AmberWarning.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "DOWNLOADING ${(item.progress * 100).toInt()}%",
                                color = AmberWarning,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                    ModelDownloadStatus.NOT_DOWNLOADED -> {
                        Surface(
                            color = CrimsonEmergency.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "NOT DOWNLOADED",
                                color = CrimsonEmergency,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                    ModelDownloadStatus.ERROR -> {
                        Surface(
                            color = CrimsonEmergency.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "ERROR",
                                color = CrimsonEmergency,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                // Action Button
                when (item.status) {
                    ModelDownloadStatus.DOWNLOADED -> {
                        TextButton(
                            onClick = onDelete,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text("Delete", color = TextTertiary, fontSize = 11.sp)
                        }
                    }
                    ModelDownloadStatus.DOWNLOADING -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = CyanAccent,
                            strokeWidth = 2.dp
                        )
                    }
                    ModelDownloadStatus.NOT_DOWNLOADED -> {
                        Button(
                            onClick = onDownload,
                            colors = ButtonDefaults.buttonColors(containerColor = BluePrimary),
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Text("Download", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    ModelDownloadStatus.ERROR -> {
                        Button(
                            onClick = onDownload,
                            colors = ButtonDefaults.buttonColors(containerColor = CrimsonEmergency),
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Text("Retry", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PermissionStatusRow(permissionName: String, isGranted: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = permissionName, color = TextPrimary, fontSize = 12.sp)
        Surface(
            color = if (isGranted) EmeraldSafe.copy(alpha = 0.2f) else CrimsonEmergency.copy(alpha = 0.2f),
            shape = RoundedCornerShape(4.dp)
        ) {
            Text(
                text = if (isGranted) "GRANTED" else "REQUIRED",
                color = if (isGranted) EmeraldSafe else CrimsonEmergency,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
    }
}
