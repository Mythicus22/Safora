package com.example.safora.features.settings

import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.safora.data.model.User
import com.example.safora.data.repository.SaforaRepository
import com.example.safora.ui.theme.*

@Composable
fun SettingsScreen(
    onNavigateToPairing: () -> Unit,
    onNavigateToContacts: () -> Unit
) {
    val repository = SaforaRepository.instance
    val user by repository.user.collectAsState()
    val isDemoMode by repository.isDemoMode.collectAsState()
    val isMonitoring by repository.isAudioMonitoring.collectAsState()

    var showEditProfileDialog by remember { mutableStateOf(false) }
    var editName by remember { mutableStateOf(user.name) }
    var editPhone by remember { mutableStateOf(user.phone) }
    var editBlood by remember { mutableStateOf(user.bloodGroup) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CharcoalDark)
            .padding(horizontal = 20.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "PROFILE & PREFERENCES",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Black,
            color = Color.White
        )
        Text(
            text = "System settings, safety triggers and hardware management",
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
                            text = user.name,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = TextPrimary
                        )
                        Text(
                            text = "${user.phone} • Blood: ${user.bloodGroup}",
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

        Spacer(modifier = Modifier.height(16.dp))

        // Device & Safety Controls
        Text(
            text = "SAFETY SHORTCUTS",
            color = TextSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CharcoalSurface),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column {
                ListItem(
                    headlineContent = { Text("Wearable Pairing & BLE", color = TextPrimary) },
                    supportingContent = { Text("Manage ESP32 necklace link & GATT logs", color = TextSecondary) },
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
                    supportingContent = { Text("Recipients for SOS broadcast alerts", color = TextSecondary) },
                    trailingContent = {
                        IconButton(onClick = onNavigateToContacts) {
                            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextSecondary)
                        }
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

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
                repository.updateUser(User())
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp)
        ) {
            Text("Reset Demo Data to Initial State", color = TextTertiary)
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
                                bloodGroup = editBlood
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
