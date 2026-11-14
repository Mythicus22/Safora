package com.example.safora.features.contacts

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import com.example.safora.data.model.Contact
import com.example.safora.data.repository.SaforaRepository
import com.example.safora.ui.theme.*

@Composable
fun ContactsScreen(
    onBack: () -> Unit
) {
    val repository = SaforaRepository.instance
    val context = LocalContext.current
    val contacts by repository.contacts.collectAsState()

    var showAddDialog by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var newPhone by remember { mutableStateOf("") }
    var newRelation by remember { mutableStateOf("Family") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CharcoalDark)
            .padding(20.dp)
    ) {
        // App bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Trusted Contacts",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            IconButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add Contact", tint = BluePrimary)
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = "During an SOS trigger, live GPS location and alerts are automatically sent to configured Emergency Contacts.",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary
        )

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(contacts) { contact ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = CharcoalSurface),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (contact.isEmergencyContact) CrimsonEmergency.copy(alpha = 0.2f) else CharcoalSurfaceElevated
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Person,
                                    contentDescription = null,
                                    tint = if (contact.isEmergencyContact) CrimsonEmergency else TextSecondary
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = contact.name,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )
                                    if (contact.isEmergencyContact) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Surface(
                                            color = CrimsonEmergency.copy(alpha = 0.2f),
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                text = "SOS RECIPIENT",
                                                color = CrimsonEmergency,
                                                fontSize = 8.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                                Text(
                                    text = "${contact.phone} • ${contact.relationship}",
                                    color = TextSecondary,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Test Call Button
                            IconButton(
                                onClick = {
                                    val intent = Intent(Intent.ACTION_DIAL).apply {
                                        data = Uri.parse("tel:${contact.phone.filter { it.isDigit() || it == '+' }}")
                                    }
                                    context.startActivity(intent)
                                }
                            ) {
                                Icon(Icons.Default.Phone, contentDescription = "Call", tint = EmeraldSafe, modifier = Modifier.size(18.dp))
                            }

                            // Toggle Emergency Status
                            IconButton(onClick = { repository.toggleEmergencyContact(contact.id) }) {
                                Icon(
                                    imageVector = if (contact.isEmergencyContact) Icons.Default.NotificationsActive else Icons.Default.NotificationsOff,
                                    contentDescription = "Toggle SOS",
                                    tint = if (contact.isEmergencyContact) CrimsonEmergency else TextTertiary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            // Remove
                            IconButton(onClick = { repository.removeContact(contact.id) }) {
                                Icon(
                                    Icons.Default.DeleteOutline,
                                    contentDescription = "Remove",
                                    tint = TextTertiary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Add Trusted Contact", color = TextPrimary) },
            text = {
                Column {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("Contact Name") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newPhone,
                        onValueChange = { newPhone = it },
                        label = { Text("Phone Number") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newRelation,
                        onValueChange = { newRelation = it },
                        label = { Text("Relationship (e.g. Sibling, Friend)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newName.isNotBlank() && newPhone.isNotBlank()) {
                            repository.addContact(
                                Contact(
                                    id = "c_${System.currentTimeMillis()}",
                                    name = newName,
                                    phone = newPhone,
                                    relationship = newRelation,
                                    isEmergencyContact = true
                                )
                            )
                            newName = ""
                            newPhone = ""
                            showAddDialog = false
                        }
                    }
                ) {
                    Text("Save Contact")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("Cancel")
                }
            },
            containerColor = CharcoalSurfaceElevated
        )
    }
}
