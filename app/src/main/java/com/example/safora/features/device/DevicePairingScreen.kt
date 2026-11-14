package com.example.safora.features.device

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.safora.core.utils.PermissionHelper
import com.example.safora.data.model.DeviceConnectionState
import com.example.safora.data.model.DiscoveredBleDevice
import com.example.safora.data.repository.SaforaRepository
import com.example.safora.services.ble.BleManager
import com.example.safora.ui.theme.*

@Composable
fun DevicePairingScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val repository = remember { SaforaRepository.getInstance(context) }
    val wearable by repository.wearable.collectAsState()
    val discoveredDevices by repository.discoveredDevices.collectAsState()

    val bleManager = remember { BleManager(context) }
    var showConsoleLogs by remember { mutableStateOf(false) }
    var showBlePermissionDialog by remember { mutableStateOf(false) }

    val blePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val granted = results.values.all { it }
        if (granted) {
            bleManager.startScan()
            repository.startBleScan()
        }
    }

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
                    text = "Wearable Necklace",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            TextButton(onClick = { showConsoleLogs = !showConsoleLogs }) {
                Text(if (showConsoleLogs) "Hide Logs" else "BLE Logs", color = CyanAccent, fontSize = 12.sp)
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Device Connection Card
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
                            text = "BLE MAC: ${wearable.address}",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                    Surface(
                        color = when (wearable.state) {
                            DeviceConnectionState.CONNECTED -> EmeraldSafe.copy(alpha = 0.2f)
                            DeviceConnectionState.CONNECTING -> AmberWarning.copy(alpha = 0.2f)
                            DeviceConnectionState.SCANNING -> BluePrimary.copy(alpha = 0.2f)
                            DeviceConnectionState.DISCONNECTED -> CharcoalDark
                        },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = wearable.state.name,
                            color = when (wearable.state) {
                                DeviceConnectionState.CONNECTED -> EmeraldSafe
                                DeviceConnectionState.CONNECTING -> AmberWarning
                                DeviceConnectionState.SCANNING -> BluePrimary
                                DeviceConnectionState.DISCONNECTED -> TextTertiary
                            },
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

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
                    Text("ESP32-CAM Status", color = TextSecondary, fontSize = 12.sp)
                    Text(
                        if (wearable.isRecording) "RECORDING (MicroSD Segments)" else "STANDBY (OV2640 Ready)",
                        color = if (wearable.isRecording) CrimsonEmergency else EmeraldSafe,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Connect / Scan / Disconnect Button Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (wearable.state == DeviceConnectionState.CONNECTED) {
                        Button(
                            onClick = {
                                bleManager.disconnect()
                                repository.disconnectWearable()
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = CrimsonEmergencyDark),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Disconnect BLE")
                        }
                    } else {
                        Button(
                            onClick = {
                                if (wearable.state == DeviceConnectionState.SCANNING) {
                                    bleManager.stopScan()
                                    repository.stopBleScan()
                                } else {
                                    if (PermissionHelper.hasBlePermission(context)) {
                                        bleManager.startScan()
                                        repository.startBleScan()
                                    } else {
                                        showBlePermissionDialog = true
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = BluePrimary),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(if (wearable.state == DeviceConnectionState.SCANNING) "Stop Scan" else "Scan for BLE")
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            val nextRec = !wearable.isRecording
                            repository.setWearableRecording(nextRec)
                            bleManager.sendCommand(if (nextRec) "START_RECORD" else "STOP_RECORD")
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            if (wearable.isRecording) "Stop Recording" else "Test Camera Capture",
                            color = if (wearable.isRecording) CrimsonEmergency else TextPrimary,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }

        // Real-time Console Log Overlay
        AnimatedVisibility(visible = showConsoleLogs) {
            Column(modifier = Modifier.padding(top = 10.dp)) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp),
                    colors = CardDefaults.cardColors(containerColor = CharcoalDark),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(10.dp)
                    ) {
                        items(wearable.consoleLogs) { log ->
                            Text(
                                text = log,
                                color = EmeraldSafe,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Discovered Devices List (when scanning)
        if (wearable.state == DeviceConnectionState.SCANNING || discoveredDevices.isNotEmpty()) {
            Text(
                text = "DISCOVERED BLE PERIPHERALS",
                color = TextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(discoveredDevices) { device ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { repository.connectToDevice(device) },
                        colors = CardDefaults.cardColors(containerColor = CharcoalSurface),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = device.name,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )
                                    if (device.isSaforaDevice) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Surface(
                                            color = BluePrimary.copy(alpha = 0.2f),
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                text = "SAFORA",
                                                color = BluePrimary,
                                                fontSize = 8.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                                Text(
                                    text = "${device.address} • Signal: ${device.rssi} dBm",
                                    color = TextSecondary,
                                    fontSize = 11.sp
                                )
                            }

                            Button(
                                onClick = { repository.connectToDevice(device) },
                                colors = ButtonDefaults.buttonColors(containerColor = BluePrimary),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                            ) {
                                Text("Pair", fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        } else {
            // BLE GATT Technical details
            Text(
                text = "CONFIGURED HARDWARE BLE GATT PROFILE",
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
                        text = "Primary Service: 7c1e0001-8a4b-4f3c-9d2e-1b6a5c7d0001",
                        color = TextSecondary,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "Write Command Char: 7c1e0002 (START_RECORD / STOP_RECORD)",
                        color = TextSecondary,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "Notify Event Char: 7c1e0004 (MANUAL_SOS, RECORDING_STARTED)",
                        color = TextSecondary,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Wearable Physical Button Trigger Simulation
        Button(
            onClick = {
                repository.confirmSos("ESP32_PHYSICAL_SOS_BUTTON")
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = AmberWarning),
            shape = RoundedCornerShape(10.dp)
        ) {
            Icon(Icons.Default.TouchApp, contentDescription = null, tint = Color.Black)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Simulate Wearable Necklace Physical Button Press", color = Color.Black, fontWeight = FontWeight.Bold)
        }
    }

    if (showBlePermissionDialog) {
        AlertDialog(
            onDismissRequest = { showBlePermissionDialog = false },
            title = { Text("Bluetooth Permission Required", color = TextPrimary) },
            text = {
                Text(
                    "SAFORA needs Bluetooth permissions to detect, pair, and send recording commands to your wearable necklace.",
                    color = TextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showBlePermissionDialog = false
                        val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
                        } else {
                            arrayOf(Manifest.permission.BLUETOOTH, Manifest.permission.BLUETOOTH_ADMIN)
                        }
                        blePermissionLauncher.launch(perms)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BluePrimary)
                ) {
                    Text("Grant Permission")
                }
            },
            dismissButton = {
                TextButton(onClick = { showBlePermissionDialog = false }) {
                    Text("Cancel")
                }
            },
            containerColor = CharcoalSurfaceElevated
        )
    }
}
