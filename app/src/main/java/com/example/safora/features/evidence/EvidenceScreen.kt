package com.example.safora.features.evidence

import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
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
    val repository = SaforaRepository.instance
    val evidenceList by repository.evidenceList.collectAsState()
    val esp32Host by repository.esp32WifiHost.collectAsState()
    var selectedSegment by remember { mutableStateOf<EvidenceSegment?>(null) }
    var hostInput by remember { mutableStateOf(esp32Host) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CharcoalDark)
            .padding(horizontal = 20.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "EVIDENCE VAULT",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Black,
            color = Color.White
        )
        Text(
            text = "MicroSD Wi-Fi Transfer & AES-256 Authenticated Encryption",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Wi-Fi Endpoint Configuration Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CharcoalSurfaceElevated),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "ESP32-CAM Wi-Fi SoftAP Receiver",
                    color = CyanAccent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = hostInput,
                        onValueChange = { hostInput = it },
                        label = { Text("ESP32 IP / Host") },
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
                        onClick = {
                            repository.setEsp32WifiHost(hostInput)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = BluePrimary),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Save")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Trigger manual Wi-Fi Segment pull (Mock/Test button)
        Button(
            onClick = {
                val newSegNum = evidenceList.size + 1
                val newSeg = EvidenceSegment(
                    id = "ev_seg_$newSegNum",
                    incidentId = "INC-8921",
                    segmentNumber = newSegNum,
                    timestamp = System.currentTimeMillis(),
                    fileSizeKb = (380..450).random(),
                    isEncrypted = true,
                    isUploaded = false
                )
                repository.addEvidenceSegment(newSeg)
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = CharcoalSurfaceElevated),
            shape = RoundedCornerShape(10.dp)
        ) {
            Icon(Icons.Default.Wifi, contentDescription = null, tint = CyanAccent)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Simulate Ingesting Segment From ESP32-CAM", color = TextPrimary, fontSize = 12.sp)
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Evidence segments list
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 90.dp)
        ) {
            items(evidenceList) { segment ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
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
                                    .size(42.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(CharcoalSurfaceElevated),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.VideoCameraBack,
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
                                    text = "${segment.fileSizeKb} KB • ${SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(segment.timestamp))}",
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
                                    text = "AES-GCM",
                                    color = EmeraldSafe,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (segment.isUploaded) "Cloud Synced" else "Stored Locally",
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
