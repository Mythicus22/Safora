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
            text = "System preferences, wearable link and permissions",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Profile Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CharcoalSurfaceElevated),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
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
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Settings items
        Text(
            text = "DEVICE & SAFETY",
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
                    supportingContent = { Text("Manage ESP32 necklace link", color = TextSecondary) },
                    trailingContent = {
                        IconButton(onClick = onNavigateToPairing) {
                            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextSecondary)
                        }
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
                Divider(color = CharcoalBorder)
                ListItem(
                    headlineContent = { Text("Emergency Contacts", color = TextPrimary) },
                    supportingContent = { Text("Configure recipients for SOS alerts", color = TextSecondary) },
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

        // Hackathon Demo Mode Switch
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
                Column {
                    Text("Hackathon Demo Mode", fontWeight = FontWeight.Bold, color = TextPrimary)
                    Text("Enables on-screen simulation controls", color = TextSecondary, fontSize = 11.sp)
                }
                Switch(
                    checked = isDemoMode,
                    onCheckedChange = { repository.updateDemoMode(it) }
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Reset demo data button
        OutlinedButton(
            onClick = {
                repository.updateUser(User())
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp)
        ) {
            Text("Reset Demo Data to Defaults", color = TextTertiary)
        }

        Spacer(modifier = Modifier.height(90.dp))
    }
}
