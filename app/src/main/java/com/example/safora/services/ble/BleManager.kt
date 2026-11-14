package com.example.safora.services.ble

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.safora.data.model.DeviceConnectionState
import com.example.safora.data.model.HardwareErrorState
import com.example.safora.data.model.WearableDevice
import com.example.safora.data.repository.SaforaRepository
import java.nio.charset.StandardCharsets
import java.util.*

class BleManager(private val context: Context) {

    private val repository = SaforaRepository.getInstance(context)
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter = bluetoothManager?.adapter
    private var bluetoothGatt: BluetoothGatt? = null
    private val handler = Handler(Looper.getMainLooper())

    companion object {
        const val TAG = "SaforaBleManager"
        const val TARGET_DEVICE_NAME = "SAFORA_DEVICE"

        // Exact custom 128-bit GATT UUIDs from Hardware Specification
        val SERVICE_UUID: UUID = UUID.fromString("7c1e0001-8a4b-4f3c-9d2e-1b6a5c7d0001")
        val CHAR_COMMAND_UUID: UUID = UUID.fromString("7c1e0002-8a4b-4f3c-9d2e-1b6a5c7d0001")
        val CHAR_STATUS_UUID: UUID = UUID.fromString("7c1e0003-8a4b-4f3c-9d2e-1b6a5c7d0001")
        val CHAR_EVENTS_UUID: UUID = UUID.fromString("7c1e0004-8a4b-4f3c-9d2e-1b6a5c7d0001")
        val CLIENT_CONFIG_DESCRIPTOR: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            result?.device?.let { device ->
                val name = device.name ?: ""
                if (name.contains("SAFORA", ignoreCase = true) || name.contains("ESP32", ignoreCase = true)) {
                    stopScan()
                    connectToDevice(device)
                }
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "BLE scan failed with error code: $errorCode")
            repository.updateWearable(repository.wearable.value.copy(state = DeviceConnectionState.DISCONNECTED))
            repository.logWearableConsole("BLE Scan error code: $errorCode")
        }
    }

    @SuppressLint("MissingPermission")
    fun startScan() {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            Log.w(TAG, "Bluetooth disabled; using simulated BLE scanner")
            repository.logWearableConsole("Bluetooth disabled on phone. Entering simulated scanner.")
            handler.postDelayed({
                repository.updateWearable(
                    WearableDevice(
                        name = "SAFORA_DEVICE",
                        address = "7C:1E:00:1B:6A:01",
                        state = DeviceConnectionState.CONNECTED,
                        batteryLevel = 88,
                        isRecording = false,
                        hardwareError = HardwareErrorState.NONE
                    )
                )
                repository.logWearableConsole("Simulated connection established with SAFORA_DEVICE")
            }, 1000)
            return
        }

        repository.updateWearable(repository.wearable.value.copy(state = DeviceConnectionState.SCANNING))
        try {
            bluetoothAdapter.bluetoothLeScanner?.startScan(scanCallback)
            handler.postDelayed({ stopScan() }, 10000)
        } catch (e: Exception) {
            Log.e(TAG, "Exception starting BLE scan: ${e.message}")
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        try {
            bluetoothAdapter?.bluetoothLeScanner?.stopScan(scanCallback)
        } catch (e: Exception) {
            Log.e(TAG, "Exception stopping BLE scan: ${e.message}")
        }
    }

    @SuppressLint("MissingPermission")
    fun connectToDevice(device: BluetoothDevice) {
        repository.updateWearable(
            repository.wearable.value.copy(
                name = device.name ?: TARGET_DEVICE_NAME,
                address = device.address,
                state = DeviceConnectionState.CONNECTING
            )
        )
        repository.logWearableConsole("Connecting to ${device.name ?: device.address}...")
        bluetoothGatt = device.connectGatt(context, false, gattCallback)
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()
        bluetoothGatt = null
        repository.updateWearable(
            repository.wearable.value.copy(
                state = DeviceConnectionState.DISCONNECTED,
                isRecording = false
            )
        )
        repository.logWearableConsole("BLE connection closed.")
    }

    /**
     * Sends exact specification commands:
     * START_RECORD, STOP_RECORD, GET_STATUS, PING
     */
    @SuppressLint("MissingPermission")
    fun sendCommand(command: String) {
        val gatt = bluetoothGatt
        if (gatt == null) {
            // Simulated handling
            repository.logWearableConsole("Simulated Command TX: $command")
            when (command) {
                "START_RECORD" -> repository.setWearableRecording(true)
                "STOP_RECORD" -> repository.setWearableRecording(false)
                "PING" -> repository.logWearableConsole("ACK: PONG received from ESP32")
                "GET_STATUS" -> repository.logWearableConsole("ACK: DEVICE_STATUS:BATTERY=88;REC=${if (repository.wearable.value.isRecording) 1 else 0}")
            }
            return
        }

        val service = gatt.getService(SERVICE_UUID)
        val charCommand = service?.getCharacteristic(CHAR_COMMAND_UUID)
        if (charCommand != null) {
            charCommand.value = command.toByteArray(StandardCharsets.UTF_8)
            val success = gatt.writeCharacteristic(charCommand)
            repository.logWearableConsole("Command TX: $command (Write Success: $success)")
        } else {
            repository.logWearableConsole("Error: Command characteristic not found on device")
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                repository.updateWearable(
                    repository.wearable.value.copy(
                        state = DeviceConnectionState.CONNECTED,
                        hardwareError = HardwareErrorState.NONE
                    )
                )
                repository.logWearableConsole("GATT Connected! Discovering services...")
                gatt?.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                repository.updateWearable(
                    repository.wearable.value.copy(
                        state = DeviceConnectionState.DISCONNECTED,
                        isRecording = false
                    )
                )
                repository.logWearableConsole("GATT Disconnected.")
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val service = gatt?.getService(SERVICE_UUID)
                val eventsChar = service?.getCharacteristic(CHAR_EVENTS_UUID)
                if (eventsChar != null) {
                    gatt.setCharacteristicNotification(eventsChar, true)
                    val descriptor = eventsChar.getDescriptor(CLIENT_CONFIG_DESCRIPTOR)
                    descriptor?.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    gatt.writeDescriptor(descriptor)
                    repository.logWearableConsole("Subscribed to Event Notifications (Char 7c1e0004)")
                }
            } else {
                repository.logWearableConsole("Service discovery failed with status: $status")
            }
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?
        ) {
            val data = characteristic?.value?.let { String(it, StandardCharsets.UTF_8) } ?: return
            Log.i(TAG, "Received BLE Event: $data")
            repository.logWearableConsole("BLE Event RX: $data")

            when {
                data.contains("MANUAL_SOS") -> {
                    // Physical button pressed on wearable necklace!
                    repository.confirmSos("ESP32_PHYSICAL_SOS_BUTTON")
                }
                data.contains("RECORDING_STARTED") -> {
                    repository.setWearableRecording(true)
                }
                data.contains("RECORDING_STOPPED") -> {
                    repository.setWearableRecording(false)
                }
                data.contains("CAMERA_ERROR") -> {
                    repository.updateWearable(
                        repository.wearable.value.copy(
                            isRecording = false,
                            hardwareError = HardwareErrorState.CAMERA_ERROR
                        )
                    )
                    repository.logEmergencyTimeline("HARDWARE WARNING: ESP32 camera sensor failure (CAMERA_ERROR)")
                }
                data.contains("SD_CARD_ERROR") -> {
                    repository.updateWearable(
                        repository.wearable.value.copy(
                            hardwareError = HardwareErrorState.SD_CARD_ERROR
                        )
                    )
                    repository.logEmergencyTimeline("HARDWARE WARNING: ESP32 microSD card mount/write error (SD_CARD_ERROR)")
                }
                data.contains("STORAGE_FULL") -> {
                    repository.updateWearable(
                        repository.wearable.value.copy(
                            hardwareError = HardwareErrorState.STORAGE_FULL
                        )
                    )
                    repository.logEmergencyTimeline("HARDWARE WARNING: ESP32 microSD card storage full (STORAGE_FULL)")
                }
                data.startsWith("DEVICE_STATUS") -> {
                    try {
                        val parts = data.removePrefix("DEVICE_STATUS:").split(";")
                        var batt = repository.wearable.value.batteryLevel
                        var rec = repository.wearable.value.isRecording
                        parts.forEach { part ->
                            val kv = part.split("=")
                            if (kv.size == 2) {
                                if (kv[0] == "BATTERY") batt = kv[1].toIntOrNull() ?: batt
                                if (kv[0] == "REC") rec = kv[1] == "1"
                            }
                        }
                        repository.updateWearable(
                            repository.wearable.value.copy(
                                batteryLevel = batt,
                                isRecording = rec,
                                lastPingMs = System.currentTimeMillis()
                            )
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing status: ${e.message}")
                    }
                }
            }
        }
    }
}
