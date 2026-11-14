package com.example.safora.features.device

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
import com.example.safora.data.model.DeviceConnectionState
import com.example.safora.data.repository.SaforaRepository
import com.example.safora.ui.theme.*

@Composable
fun DevicePairingScreen(
    onBack: () -> Unit
) {
    val repository = SaforaRepository.instance
    val wearable by repository.wearable.collectAsState()
    var isScanning by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CharcoalDark)
            .padding(20.dp)
    ) {
        // App bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "SAFORA Wearable Setup",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Current status card
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
                            text = wearable.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "BLE: ${wearable.address}",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(
                                if (wearable.state == DeviceConnectionState.CONNECTED) EmeraldSafe else TextTertiary
                            )
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Connection State", color = TextSecondary, fontSize = 12.sp)
                    Text(
                        text = wearable.state.name,
                        color = if (wearable.state == DeviceConnectionState.CONNECTED) EmeraldSafe else TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Battery Level", color = TextSecondary, fontSize = 12.sp)
                    Text("${wearable.batteryLevel}%", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }

                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Camera Recording State", color = TextSecondary, fontSize = 12.sp)
                    Text(
                        if (wearable.isRecording) "RECORDING" else "STANDBY",
                        color = if (wearable.isRecording) CrimsonEmergency else TextTertiary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            val nextState = if (wearable.state == DeviceConnectionState.CONNECTED) {
                                DeviceConnectionState.DISCONNECTED
                            } else {
                                DeviceConnectionState.CONNECTED
                            }
                            repository.updateWearable(wearable.copy(state = nextState))
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (wearable.state == DeviceConnectionState.CONNECTED) CrimsonEmergencyDark else BluePrimary
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(if (wearable.state == DeviceConnectionState.CONNECTED) "Disconnect" else "Connect BLE")
                    }

                    OutlinedButton(
                        onClick = {
                            repository.setWearableRecording(!wearable.isRecording)
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            if (wearable.isRecording) "Stop Capture" else "Test Start Cam",
                            color = if (wearable.isRecording) CrimsonEmergency else TextPrimary
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // BLE Technical Configuration Box
        Text(
            text = "HARDWARE GATT SPECIFICATION",
            color = TextSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CharcoalSurface),
            shape = RoundedCornerShape(10.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "Service UUID: 7c1e0001-8a4b-4f3c-9d2e-1b6a5c7d0001",
                    color = TextSecondary,
                    fontSize = 10.sp
                )
                Text(
                    text = "Command Char: 7c1e0002 (START_RECORD / STOP_RECORD)",
                    color = TextSecondary,
                    fontSize = 10.sp
                )
                Text(
                    text = "Status Char: 7c1e0003 | Events Char: 7c1e0004 (MANUAL_SOS)",
                    color = TextSecondary,
                    fontSize = 10.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Wearable Physical Button Trigger Simulation
        Button(
            onClick = {
                repository.confirmSos("ESP32_PHYSICAL_SOS_BUTTON")
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = AmberWarning),
            shape = RoundedCornerShape(10.dp)
        ) {
            Text("Simulate Wearable Necklace Physical SOS Press", color = Color.Black, fontWeight = FontWeight.Bold)
        }
    }
}
