package com.example.safora.features.settings

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.safora.core.utils.PermissionHelper
import com.example.safora.data.model.User
import com.example.safora.data.repository.SaforaRepository
import com.example.safora.ui.theme.*

@Composable
fun SettingsScreen(
    onNavigateToPairing: () -> Unit,
    onNavigateToContacts: () -> Unit
) {
    val context = LocalContext.current
    val repository = remember { SaforaRepository.getInstance(context) }
    val user by repository.user.collectAsState()
    val isDemoMode by repository.isDemoMode.collectAsState()

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
            text = "System permissions, wearable link and emergency identity",
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

