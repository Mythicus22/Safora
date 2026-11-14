package com.example.safora.features.evidence

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.example.safora.services.wifi.Esp32SoftApManager
import com.example.safora.services.wifi.SoftApConnectionState
import com.example.safora.services.wifi.WifiEvidenceFetcher
import com.example.safora.ui.theme.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun EvidenceScreen() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val repository = remember { SaforaRepository.getInstance(context) }
    val evidenceList by repository.evidenceList.collectAsState()
    val incident by repository.currentIncident.collectAsState()
    val contacts by repository.contacts.collectAsState()
    val esp32Host by repository.esp32WifiHost.collectAsState()

    val softApManager = remember { Esp32SoftApManager(context) }
    val softApState by softApManager.connectionState.collectAsState()
    val softApStatusText by softApManager.statusMessage.collectAsState()
    val evidenceFetcher = remember { WifiEvidenceFetcher(context, context.cacheDir, softApManager) }

    var hostInput by remember { mutableStateOf(esp32Host) }
    var selectedSegment by remember { mutableStateOf<EvidenceSegment?>(null) }
    var showThresholdDialog by remember { mutableStateOf(false) }
    var isDownloading by remember { mutableStateOf(false) }

    val emergencyContacts = contacts.filter { it.isEmergencyContact }
    val approvedCount = emergencyContacts.count { it.hasApprovedAccess }
    val threshold = incident.thresholdRequired
    val isDecryptionUnlocked = approvedCount >= threshold && threshold > 0

    DisposableEffect(Unit) {
        onDispose {
            softApManager.disconnect()
        }
    }

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
                    text = "WifiNetworkSpecifier • 5s ZIP Segments • B2 Cloud Sync",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }

            if (evidenceList.isNotEmpty()) {
                IconButton(onClick = { repository.syncAllEvidence() }) {
                    Icon(Icons.Default.CloudSync, contentDescription = "Sync All", tint = BluePrimary)
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // WifiNetworkSpecifier SoftAP Binding Status Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CharcoalSurfaceElevated),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "ESP32-CAM Wi-Fi SoftAP Network Binding",
                            color = CyanAccent,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = softApStatusText,
                            color = when (softApState) {
                                SoftApConnectionState.CONNECTED -> EmeraldSafe
                                SoftApConnectionState.CONNECTING -> AmberWarning
                                SoftApConnectionState.FAILED -> CrimsonEmergency
                                SoftApConnectionState.DISCONNECTED -> TextSecondary
                            },
                            fontSize = 10.sp
                        )
                    }

                    Button(
                        onClick = {
                            if (softApState == SoftApConnectionState.CONNECTED) {
                                softApManager.disconnect()
                            } else {
                                softApManager.connectToEsp32SoftAp(
                                    ssid = Esp32SoftApManager.DEFAULT_SSID,
                                    passphrase = Esp32SoftApManager.DEFAULT_PASSWORD
                                )
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (softApState == SoftApConnectionState.CONNECTED) CrimsonEmergencyDark else BluePrimary
                        ),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(if (softApState == SoftApConnectionState.CONNECTED) "Unbind" else "Bind Wi-Fi", fontSize = 11.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 50% Trusted Contact Approval Status Card (Shamir's Secret Sharing)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showThresholdDialog = true },
            colors = CardDefaults.cardColors(containerColor = CharcoalSurface),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isDecryptionUnlocked) Icons.Default.LockOpen else Icons.Default.Lock,
                        contentDescription = null,
                        tint = if (isDecryptionUnlocked) EmeraldSafe else AmberWarning,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "50% CONTACT ACCESS CONTROL (SHAMIR)",
                            color = if (isDecryptionUnlocked) EmeraldSafe else AmberWarning,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (isDecryptionUnlocked) {
                                "Threshold Met ($approvedCount/$threshold Approvals) • Decryption Authorized"
                            } else {
                                "$approvedCount of $threshold Approvals • Backend Admins Cannot Access"
                            },
                            color = TextPrimary,
                            fontSize = 10.sp
                        )
                    }
                }

                TextButton(onClick = { showThresholdDialog = true }) {
                    Text(if (isDecryptionUnlocked) "Authorized" else "Manage", color = CyanAccent, fontSize = 11.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Ingest ZIP Segment Action with Retry and Network Binding
        Button(
            onClick = {
                val nextSegIndex = evidenceList.size + 1
                val incId = if (incident.id.isNotBlank()) incident.id else "INC-8921"
                isDownloading = true
                coroutineScope.launch {
                    evidenceFetcher.fetchZipSegmentWithRetry(incId, nextSegIndex)
                    isDownloading = false
                }
            },
            enabled = !isDownloading,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = CharcoalSurfaceElevated),
            shape = RoundedCornerShape(10.dp)
        ) {
            Icon(Icons.Default.Download, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (isDownloading) "Downloading 5s ZIP Segment..." else "Download 5s ZIP Segment (with Retry)",
                color = TextPrimary,
                fontSize = 12.sp
            )
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
                        text = "5-second ZIP segments (JPEGs + manifest.json) from ESP32\nwill be decrypted and synchronized autonomously.",
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
                                        Icons.Default.FolderZip,
                                        contentDescription = null,
                                        tint = CrimsonEmergency
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "ZIP Segment #${segment.segmentNumber}",
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )
                                    Text(
                                        text = "${segment.fileSizeKb} KB • ${segment.resolution} • ${SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(segment.timestamp))}",
                                        color = TextSecondary,
                                        fontSize = 10.sp
                                    )
                                }
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Surface(
                                    color = EmeraldSafe.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = "AES-GCM",
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
                    text = "ZIP Segment #${segment.segmentNumber} Details",
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
                                text = if (isDecryptionUnlocked) "DECRYPTED ZIP FRAMES & MANIFEST" else "ENCRYPTED ZIP CIPHERTEXT (AES-256-GCM)",
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
                    Text("Content: 5s ZIP archive (manifest.json + JPEG frames)", color = TextPrimary, fontSize = 12.sp)
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
