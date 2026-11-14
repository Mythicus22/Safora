package com.example.safora.data.repository

import android.content.Context
import com.example.safora.core.crypto.EvidenceCipher
import com.example.safora.core.utils.EmergencyDispatcher
import com.example.safora.data.local.MockDataProvider
import com.example.safora.data.local.PreferencesManager
import com.example.safora.data.model.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class SaforaRepository(private val context: Context) {
    private val scope = CoroutineScope(Dispatchers.IO)
    private val prefsManager = PreferencesManager(context)

    // Current user state
    private val _user = MutableStateFlow(User(name = "", phone = "", isOnboarded = false))
    val user: StateFlow<User> = _user.asStateFlow()

    // Wearable device state
    private val _wearable = MutableStateFlow(WearableDevice())
    val wearable: StateFlow<WearableDevice> = _wearable.asStateFlow()

    // Discovered BLE devices
    private val _discoveredDevices = MutableStateFlow<List<DiscoveredBleDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<DiscoveredBleDevice>> = _discoveredDevices.asStateFlow()

    // Incident state
    private val _currentIncident = MutableStateFlow(Incident())
    val currentIncident: StateFlow<Incident> = _currentIncident.asStateFlow()

    // Audio monitoring status
    private val _isAudioMonitoring = MutableStateFlow(false)
    val isAudioMonitoring: StateFlow<Boolean> = _isAudioMonitoring.asStateFlow()

    private val _audioSensitivity = MutableStateFlow("Medium")
    val audioSensitivity: StateFlow<String> = _audioSensitivity.asStateFlow()

    private val _lastAudioDetection = MutableStateFlow(
        AudioDetectionResult(
            detectedClass = "Acoustic Shield Ready",
            confidence = 1.0f,
            timestamp = System.currentTimeMillis()
        )
    )
    val lastAudioDetection: StateFlow<AudioDetectionResult> = _lastAudioDetection.asStateFlow()

    private val _audioHistory = MutableStateFlow<List<AudioDetectionResult>>(emptyList())
    val audioHistory: StateFlow<List<AudioDetectionResult>> = _audioHistory.asStateFlow()

    // Location state (latitude, longitude, accuracyMeters, isRealGps)
    private val _currentLocation = MutableStateFlow(Pair(28.6139, 77.2090))
    val currentLocation: StateFlow<Pair<Double, Double>> = _currentLocation.asStateFlow()

    private val _isRealLocationLocked = MutableStateFlow(false)
    val isRealLocationLocked: StateFlow<Boolean> = _isRealLocationLocked.asStateFlow()

    // Contacts
    private val _contacts = MutableStateFlow<List<Contact>>(emptyList())
    val contacts: StateFlow<List<Contact>> = _contacts.asStateFlow()

    // Nearby Responders
    private val _responders = MutableStateFlow(MockDataProvider.getInitialResponders())
    val responders: StateFlow<List<Responder>> = _responders.asStateFlow()

    // Evidence Segments
    private val _evidenceList = MutableStateFlow<List<EvidenceSegment>>(emptyList())
    val evidenceList: StateFlow<List<EvidenceSegment>> = _evidenceList.asStateFlow()

    // Wi-Fi Config
    private val _esp32WifiHost = MutableStateFlow("192.168.4.1")
    val esp32WifiHost: StateFlow<String> = _esp32WifiHost.asStateFlow()

    // Demo Mode toggle
    private val _isDemoMode = MutableStateFlow(true)
    val isDemoMode: StateFlow<Boolean> = _isDemoMode.asStateFlow()

    init {
        // Load persistent DataStore data on launch
        scope.launch {
            _user.value = prefsManager.userFlow.first()
            _contacts.value = prefsManager.contactsFlow.first()
            _isDemoMode.value = prefsManager.demoModeFlow.first()
            _esp32WifiHost.value = prefsManager.wifiHostFlow.first()
        }
    }

    fun updateDemoMode(enabled: Boolean) {
        _isDemoMode.value = enabled
        scope.launch { prefsManager.setDemoMode(enabled) }
    }

    fun setAudioSensitivity(level: String) {
        _audioSensitivity.value = level
    }

    fun updateUser(updated: User) {
        _user.value = updated
        scope.launch { prefsManager.saveUser(updated) }
    }

    fun updateLocation(lat: Double, lng: Double, isRealGps: Boolean = true) {
        _currentLocation.value = Pair(lat, lng)
        _isRealLocationLocked.value = isRealGps
        _responders.value = MockDataProvider.getInitialResponders(lat, lng)
    }

    fun updateWearable(updated: WearableDevice) {
        _wearable.value = updated
    }

    fun logWearableConsole(message: String) {
        val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val newLogs = listOf("[$time] $message") + _wearable.value.consoleLogs.take(19)
        _wearable.value = _wearable.value.copy(consoleLogs = newLogs)
    }

    fun setWearableRecording(isRecording: Boolean) {
        _wearable.value = _wearable.value.copy(isRecording = isRecording)
        logWearableConsole(if (isRecording) "COMMAND ACK: RECORDING STARTED (OV2640 Active)" else "COMMAND ACK: RECORDING STOPPED")
    }

    fun startBleScan() {
        _wearable.value = _wearable.value.copy(state = DeviceConnectionState.SCANNING)
        logWearableConsole("BLE Scan started on channel 37/38/39...")
        _discoveredDevices.value = MockDataProvider.getMockNearbyBleDevices()
    }

    fun stopBleScan() {
        if (_wearable.value.state == DeviceConnectionState.SCANNING) {
            _wearable.value = _wearable.value.copy(state = DeviceConnectionState.DISCONNECTED)
            logWearableConsole("BLE Scan completed.")
        }
    }

    fun connectToDevice(device: DiscoveredBleDevice) {
        _wearable.value = _wearable.value.copy(
            name = device.name,
            address = device.address,
            state = DeviceConnectionState.CONNECTING
        )
        logWearableConsole("Attempting GATT connection to ${device.address}...")

        _wearable.value = _wearable.value.copy(
            name = device.name,
            address = device.address,
            state = DeviceConnectionState.CONNECTED,
            batteryLevel = 88,
            lastPingMs = System.currentTimeMillis()
        )
        logWearableConsole("GATT connected! Service 7c1e0001 discovered. Notifications active.")
    }

    fun disconnectWearable() {
        _wearable.value = _wearable.value.copy(
            state = DeviceConnectionState.DISCONNECTED,
            isRecording = false
        )
        logWearableConsole("BLE GATT disconnected.")
    }

    fun setAudioMonitoring(active: Boolean) {
        _isAudioMonitoring.value = active
    }

    fun updateAudioDetection(result: AudioDetectionResult) {
        _lastAudioDetection.value = result
        _audioHistory.value = (listOf(result) + _audioHistory.value).take(10)
    }

    fun triggerPossibleDistress(detectedClass: String = "Scream / Distress Call") {
        if (_currentIncident.value.state == IncidentState.IDLE || _currentIncident.value.state == IncidentState.CANCELLED || _currentIncident.value.state == IncidentState.RESOLVED) {
            val incId = "INC-${System.currentTimeMillis().toString().takeLast(6)}"
            val timeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            _currentIncident.value = Incident(
                id = incId,
                timestamp = System.currentTimeMillis(),
                latitude = _currentLocation.value.first,
                longitude = _currentLocation.value.second,
                state = IncidentState.VERIFYING,
                triggerSource = "VOICE_ML: $detectedClass",
                timeline = listOf("[$timeStr] Acoustic distress pattern detected ($detectedClass)")
            )
        }
    }

    fun confirmSos(triggerSource: String = "MANUAL_BUTTON") {
        val incId = if (_currentIncident.value.id.isNotEmpty() && _currentIncident.value.state == IncidentState.VERIFYING) {
            _currentIncident.value.id
        } else {
            "INC-${System.currentTimeMillis().toString().takeLast(6)}"
        }

        val timeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val initialTimeline = if (_currentIncident.value.state == IncidentState.VERIFYING) {
            _currentIncident.value.timeline + listOf(
                "[$timeStr] Emergency SOS CONFIRMED via $triggerSource",
                "[$timeStr] Wearable camera START_RECORD command sent over BLE",
                "[$timeStr] Live GPS coordinates broadcasted to emergency contacts",
                "[$timeStr] Prepared National Emergency 112 hotline link"
            )
        } else {
            listOf(
                "[$timeStr] Emergency SOS INSTANTLY TRIGGERED via $triggerSource",
                "[$timeStr] Wearable camera START_RECORD command sent over BLE",
                "[$timeStr] Live GPS coordinates broadcasted to emergency contacts",
                "[$timeStr] Prepared National Emergency 112 hotline link"
            )
        }

        _currentIncident.value = Incident(
            id = incId,
            timestamp = System.currentTimeMillis(),
            latitude = _currentLocation.value.first,
            longitude = _currentLocation.value.second,
            state = IncidentState.CONFIRMED_SOS,
            triggerSource = triggerSource,
            alertsSentCount = _contacts.value.count { it.isEmergencyContact },
            timeline = initialTimeline
        )

        // Automatically trigger recording command on wearable
        setWearableRecording(true)

        // Dispatch REAL cellular SMS to all configured Emergency Contacts
        val emergencyContacts = _contacts.value.filter { it.isEmergencyContact }
        val smsText = EmergencyDispatcher.buildEmergencySmsText(
            userName = _user.value.name.ifBlank { "User" },
            latitude = _currentLocation.value.first,
            longitude = _currentLocation.value.second,
            incidentId = incId,
            bloodGroup = _user.value.bloodGroup
        )

        emergencyContacts.forEach { contact ->
            EmergencyDispatcher.sendDirectSms(
                context = context,
                phoneNumber = contact.phone,
                message = smsText,
                onSuccess = {
                    val logTime = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
                    _currentIncident.value = _currentIncident.value.copy(
                        timeline = _currentIncident.value.timeline + listOf("[$logTime] Direct SMS delivered to ${contact.name}")
                    )
                },
                onError = { err ->
                    val logTime = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
                    _currentIncident.value = _currentIncident.value.copy(
                        timeline = _currentIncident.value.timeline + listOf("[$logTime] SMS failed for ${contact.name}: $err")
                    )
                }
            )
        }
    }

    fun cancelIncident() {
        val timeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        _currentIncident.value = _currentIncident.value.copy(
            state = IncidentState.CANCELLED,
            timeline = _currentIncident.value.timeline + listOf("[$timeStr] Incident cancelled by user (Marked Safe)")
        )
        setWearableRecording(false)
    }

    fun resolveIncident() {
        val timeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        _currentIncident.value = _currentIncident.value.copy(
            state = IncidentState.RESOLVED,
            timeline = _currentIncident.value.timeline + listOf("[$timeStr] Incident marked RESOLVED. All alerts closed.")
        )
        setWearableRecording(false)
    }

    fun addContact(contact: Contact) {
        val updated = _contacts.value + contact
        _contacts.value = updated
        scope.launch { prefsManager.saveContacts(updated) }
    }

    fun toggleEmergencyContact(contactId: String) {
        val updated = _contacts.value.map {
            if (it.id == contactId) it.copy(isEmergencyContact = !it.isEmergencyContact) else it
        }
        _contacts.value = updated
        scope.launch { prefsManager.saveContacts(updated) }
    }

    fun removeContact(contactId: String) {
        val updated = _contacts.value.filter { it.id != contactId }
        _contacts.value = updated
        scope.launch { prefsManager.saveContacts(updated) }
    }

    fun addEvidenceSegment(segment: EvidenceSegment) {
        _evidenceList.value = listOf(segment) + _evidenceList.value
        _currentIncident.value = _currentIncident.value.copy(
            evidenceSegmentsCount = _evidenceList.value.size
        )
    }

    fun syncAllEvidence() {
        _evidenceList.value = _evidenceList.value.map { it.copy(isUploaded = true) }
    }

    fun setEsp32WifiHost(host: String) {
        _esp32WifiHost.value = host
        scope.launch { prefsManager.setWifiHost(host) }
    }

    companion object {
        @Volatile
        private var INSTANCE: SaforaRepository? = null

        fun getInstance(context: Context): SaforaRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SaforaRepository(context.applicationContext).also { INSTANCE = it }
            }
        }

        // Global accessor initialized via SaforaApp
        lateinit var instance: SaforaRepository
            internal set
    }
}
