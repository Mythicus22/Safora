package com.example.safora.features.evidence

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.safora.data.model.EvidenceSegment
import com.example.safora.data.repository.SaforaRepository
import com.example.safora.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun EvidenceScreen() {
    val context = LocalContext.current
    val repository = remember { SaforaRepository.getInstance(context) }
    val evidenceList by repository.evidenceList.collectAsState()
    val incident by repository.currentIncident.collectAsState()
    val contacts by repository.contacts.collectAsState()
    val esp32Host by repository.esp32WifiHost.collectAsState()

    var hostInput by remember { mutableStateOf(esp32Host) }
    var selectedSegment by remember { mutableStateOf<EvidenceSegment?>(null) }
    var showThresholdDialog by remember { mutableStateOf(false) }

    val emergencyContacts = contacts.filter { it.isEmergencyContact }
    val approvedCount = emergencyContacts.count { it.hasApprovedAccess }
    val threshold = incident.thresholdRequired
    val isDecryptionUnlocked = approvedCount >= threshold && threshold > 0

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CharcoalDark)
            .padding(horizontal = 20.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // Title Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "EVIDENCE VAULT",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                    color = Color.White
                )
                Text(
                    text = "Backblaze B2 • AES-256-GCM • 50% Threshold Access",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }

            // Sync All Button
            if (evidenceList.isNotEmpty()) {
                IconButton(onClick = { repository.syncAllEvidence() }) {
                    Icon(Icons.Default.CloudSync, contentDescription = "Sync All", tint = BluePrimary)
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 50% Trusted Contact Approval Status Card (Shamir's Secret Sharing)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showThresholdDialog = true },
            colors = CardDefaults.cardColors(containerColor = CharcoalSurfaceElevated),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (isDecryptionUnlocked) Icons.Default.LockOpen else Icons.Default.Lock,
                            contentDescription = null,
                            tint = if (isDecryptionUnlocked) EmeraldSafe else AmberWarning,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "50% TRUSTED CONTACT ACCESS CONTROL",
                                color = if (isDecryptionUnlocked) EmeraldSafe else AmberWarning,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (isDecryptionUnlocked) {
                                    "Threshold Met ($approvedCount/$threshold Approvals) • Decryption Authorized"
                                } else {
                                    "$approvedCount of $threshold Approvals Required to Decrypt • Zero Admin Access"
                                },
                                color = TextPrimary,
                                fontSize = 11.sp
                            )
                        }
                    }

                    TextButton(onClick = { showThresholdDialog = true }) {
                        Text(if (isDecryptionUnlocked) "Authorized" else "Manage", color = CyanAccent, fontSize = 11.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // ESP32 Wi-Fi SoftAP Configuration Bar
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CharcoalSurface),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "ESP32-CAM Wi-Fi SoftAP Receiver Endpoint",
                    color = CyanAccent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = hostInput,
                        onValueChange = { hostInput = it },
                        label = { Text("SoftAP IP (e.g. 192.168.4.1)") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = BluePrimary,
                            unfocusedBorderColor = CharcoalBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { repository.setEsp32WifiHost(hostInput) },
                        colors = ButtonDefaults.buttonColors(containerColor = BluePrimary),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Save")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Ingest Evidence Action
        Button(
            onClick = {
                val newSegNum = evidenceList.size + 1
                val newSeg = EvidenceSegment(
                    id = "ev_seg_$newSegNum",
                    incidentId = if (incident.id.isNotBlank()) incident.id else "INC-8921",
                    segmentNumber = newSegNum,
                    timestamp = System.currentTimeMillis(),
                    fileSizeKb = (395..460).random(),
                    isEncrypted = true,
                    isUploaded = false,
                    sha256Hash = UUID.randomUUID().toString().replace("-", "") + "fa90"
                )
                repository.addEvidenceSegment(newSeg)
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = CharcoalSurfaceElevated),
            shape = RoundedCornerShape(10.dp)
        ) {
            Icon(Icons.Default.Wifi, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Simulate Ingesting Segment & Autonomous B2 Sync", color = TextPrimary, fontSize = 12.sp)
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (evidenceList.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.LockClock,
                        contentDescription = null,
                        tint = TextTertiary,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "No Evidence Recorded",
                        color = TextSecondary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Text(
                        text = "When an SOS incident is triggered, 5-second camera\nsegments are encrypted with AES-256-GCM and stored locally / in B2.",
                        color = TextTertiary,
                        fontSize = 11.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        } else {
            // Evidence segments timeline list
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 90.dp)
            ) {
                items(evidenceList) { segment ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedSegment = segment },
                        colors = CardDefaults.cardColors(containerColor = CharcoalSurface),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(CharcoalSurfaceElevated),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.Videocam,
                                        contentDescription = null,
                                        tint = CrimsonEmergency
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "Segment #${segment.segmentNumber}",
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )
                                    Text(
                                        text = "${segment.fileSizeKb} KB • ${segment.durationSeconds}s • ${SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(segment.timestamp))}",
                                        color = TextSecondary,
                                        fontSize = 11.sp
                                    )
                                }
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Surface(
                                    color = EmeraldSafe.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = "AES-256 GCM",
                                        color = EmeraldSafe,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = if (segment.isUploaded) "Backblaze B2 ✓" else "Stored on Phone",
                                    color = if (segment.isUploaded) CyanAccent else TextTertiary,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Shamir's Secret Sharing Access Control Dialog
    if (showThresholdDialog) {
        AlertDialog(
            onDismissRequest = { showThresholdDialog = false },
            title = {
                Text(
                    text = "Contact Evidence Approval (Shamir)",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(
                        text = "To guarantee privacy, ciphertext stored in Backblaze B2 cannot be decrypted by backend administrators. Decryption requires approval from at least 50% ($threshold of ${emergencyContacts.size}) trusted contacts holding Shamir key shares.",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    if (emergencyContacts.isEmpty()) {
                        Text(
                            text = "No emergency contacts registered yet. Add contacts to establish key shares.",
                            color = AmberWarning,
                            fontSize = 11.sp
                        )
                    } else {
                        emergencyContacts.forEach { contact ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(contact.name, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    Text(
                                        text = if (contact.keyShareHex.isNotBlank()) "Share: ${contact.keyShareHex.take(12)}..." else "Share pending SOS",
                                        color = TextTertiary,
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                                Checkbox(
                                    checked = contact.hasApprovedAccess,
                                    onCheckedChange = {
                                        repository.toggleContactApproval(contact.id)
                                    }
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { showThresholdDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = BluePrimary)
                ) {
                    Text("Done")
                }
            },
            containerColor = CharcoalSurfaceElevated
        )
    }

    // Segment Detail / Preview Dialog
    selectedSegment?.let { segment ->
        AlertDialog(
            onDismissRequest = { selectedSegment = null },
            title = {
                Text(
                    text = "Evidence Segment #${segment.segmentNumber}",
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            },
            text = {
                Column {
                    // Preview Frame Box
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Black),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val gridColor = Color.DarkGray.copy(alpha = 0.5f)
                            drawLine(gridColor, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), strokeWidth = 1f)
                            drawLine(gridColor, Offset(size.width / 2, 0f), Offset(size.width / 2, size.height), strokeWidth = 1f)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = if (isDecryptionUnlocked) Icons.Default.CameraAlt else Icons.Default.Lock,
                                contentDescription = null,
                                tint = if (isDecryptionUnlocked) CyanAccent else AmberWarning,
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (isDecryptionUnlocked) "DECRYPTED ESP32 JPEG FRAME" else "CIPHERTEXT ENCRYPTED (AES-256)",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (isDecryptionUnlocked) segment.resolution else "Locked: Requires 50% contact approvals",
                                color = if (isDecryptionUnlocked) TextSecondary else AmberWarning,
                                fontSize = 9.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Duration: ${segment.durationSeconds} seconds (~10 JPEG frames)", color = TextPrimary, fontSize = 12.sp)
                    Text("Size: ${segment.fileSizeKb} KB", color = TextSecondary, fontSize = 11.sp)
                    Text("Cipher: AES-256-GCM (Authenticated)", color = EmeraldSafe, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text(
                        text = if (segment.isUploaded) "Storage: Backblaze B2 (Cloud Synced)" else "Storage: Queued on device",
                        color = if (segment.isUploaded) CyanAccent else TextTertiary,
                        fontSize = 11.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Integrity SHA-256 Hash:", color = TextSecondary, fontSize = 10.sp)
                    Text(
                        segment.sha256Hash,
                        color = TextPrimary,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val updated = segment.copy(isUploaded = true)
                        repository.addEvidenceSegment(updated)
                        selectedSegment = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BluePrimary)
                ) {
                    Text("Upload to Backblaze B2")
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedSegment = null }) {
                    Text("Close")
                }
            },
            containerColor = CharcoalSurfaceElevated
        )
    }
}
