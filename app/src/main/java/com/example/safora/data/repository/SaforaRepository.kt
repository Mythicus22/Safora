package com.example.safora.data.repository

import android.content.Context
import com.example.safora.core.crypto.EvidenceCipher
import com.example.safora.core.crypto.ShamirSecretSharing
import com.example.safora.core.utils.AutonomousCallManager
import com.example.safora.core.utils.EmergencyDispatcher
import com.example.safora.data.local.MockDataProvider
import com.example.safora.data.local.PreferencesManager
import com.example.safora.data.model.*
import com.example.safora.services.audio.AudioDetectionEngine
import com.example.safora.services.cloud.BackblazeEvidenceUploader
import com.example.safora.services.sos.EmergencySosService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

import kotlinx.coroutines.SupervisorJob
import org.json.JSONArray
import org.json.JSONObject

class SaforaRepository(private val context: Context) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val prefsManager = PreferencesManager(context)
    private val b2Uploader = BackblazeEvidenceUploader(context)
    private val incidentPrefs = context.getSharedPreferences("safora_incident_state", Context.MODE_PRIVATE)

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

    // GPS Hardware state
    private val _isGpsHardwareEnabled = MutableStateFlow(true)
    val isGpsHardwareEnabled: StateFlow<Boolean> = _isGpsHardwareEnabled.asStateFlow()

    fun setGpsHardwareEnabled(enabled: Boolean) {
        _isGpsHardwareEnabled.value = enabled
    }

    private fun saveActiveIncident(incident: Incident) {
        try {
            val json = JSONObject().apply {
                put("id", incident.id)
                put("timestamp", incident.timestamp)
                put("latitude", incident.latitude)
                put("longitude", incident.longitude)
                put("state", incident.state.name)
                put("triggerSource", incident.triggerSource)
                put("evidenceSegmentsCount", incident.evidenceSegmentsCount)
                put("alertsSentCount", incident.alertsSentCount)
                put("timeline", JSONArray(incident.timeline))
                put("encryptionKeyHex", incident.encryptionKeyHex)
                put("thresholdRequired", incident.thresholdRequired)
                put("totalSharesCount", incident.totalSharesCount)
            }.toString()
            incidentPrefs.edit().putString("active_incident_json", json).apply()
        } catch (e: Exception) {
            android.util.Log.e("SaforaRepository", "Failed to persist active incident: ${e.message}")
        }
    }

    private fun clearActiveIncident() {
        incidentPrefs.edit().clear().apply()
    }

    private fun loadActiveIncident(): Incident? {
        val json = incidentPrefs.getString("active_incident_json", null) ?: return null
        return try {
            val obj = JSONObject(json)
            val timelineList = mutableListOf<String>()
            val timelineArray = obj.optJSONArray("timeline")
            if (timelineArray != null) {
                for (i in 0 until timelineArray.length()) {
                    timelineList.add(timelineArray.getString(i))
                }
            }
            Incident(
                id = obj.optString("id", ""),
                timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                latitude = obj.optDouble("latitude", 28.6139),
                longitude = obj.optDouble("longitude", 77.2090),
                state = try { IncidentState.valueOf(obj.optString("state", "IDLE")) } catch (_: Exception) { IncidentState.IDLE },
                triggerSource = obj.optString("triggerSource", "MANUAL"),
                evidenceSegmentsCount = obj.optInt("evidenceSegmentsCount", 0),
                alertsSentCount = obj.optInt("alertsSentCount", 0),
                timeline = timelineList,
                encryptionKeyHex = obj.optString("encryptionKeyHex", ""),
                thresholdRequired = obj.optInt("thresholdRequired", 1),
                totalSharesCount = obj.optInt("totalSharesCount", 1)
            )
        } catch (e: Exception) {
            null
        }
    }

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

    private val _audioDiagnostics = MutableStateFlow(AudioPipelineDiagnostics())
    val audioDiagnostics: StateFlow<AudioPipelineDiagnostics> = _audioDiagnostics.asStateFlow()

    fun updateAudioDiagnostics(diagnostics: AudioPipelineDiagnostics) {
        _audioDiagnostics.value = diagnostics
    }

    fun updateAudioDiagnostics(transform: (AudioPipelineDiagnostics) -> AudioPipelineDiagnostics) {
        _audioDiagnostics.value = transform(_audioDiagnostics.value)
    }

    private var activeAudioEngine: AudioDetectionEngine? = null

    fun setActiveAudioEngine(engine: AudioDetectionEngine?) {
        activeAudioEngine = engine
    }

    // Location state
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

    // Current Incident Master Encryption Key (32 bytes AES-256)
    private var incidentKeyBytes: ByteArray? = null

    init {
        // Synchronously restore active incident if process was recreated or app was reopened during active SOS
        val saved = loadActiveIncident()
        if (saved != null && (saved.state == IncidentState.VERIFYING ||
                              saved.state == IncidentState.CONFIRMED_SOS ||
                              saved.state == IncidentState.ALERTS_SENT ||
                              saved.state == IncidentState.EVIDENCE_SYNCING)
        ) {
            _currentIncident.value = saved
            android.util.Log.i("SaforaRepository", "Restored persistent active SOS incident: ${saved.id} (${saved.state})")
        }

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

    fun logEmergencyTimeline(entry: String) {
        val timeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val formatted = if (entry.startsWith("[")) entry else "[$timeStr] $entry"
        _currentIncident.value = _currentIncident.value.copy(
            timeline = _currentIncident.value.timeline + listOf(formatted)
        )
        if (_currentIncident.value.state == IncidentState.VERIFYING ||
            _currentIncident.value.state == IncidentState.CONFIRMED_SOS ||
            _currentIncident.value.state == IncidentState.ALERTS_SENT ||
            _currentIncident.value.state == IncidentState.EVIDENCE_SYNCING
        ) {
            saveActiveIncident(_currentIncident.value)
        }
    }

    fun triggerPossibleDistress(detectedClass: String = "Scream / Distress Call") {
        if (_currentIncident.value.state == IncidentState.IDLE || _currentIncident.value.state == IncidentState.CANCELLED || _currentIncident.value.state == IncidentState.RESOLVED) {
            val incId = "INC-${System.currentTimeMillis().toString().takeLast(6)}"
            val timeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            val newIncident = Incident(
                id = incId,
                timestamp = System.currentTimeMillis(),
                latitude = _currentLocation.value.first,
                longitude = _currentLocation.value.second,
                state = IncidentState.VERIFYING,
                triggerSource = "VOICE_ML: $detectedClass",
                timeline = listOf("[$timeStr] Acoustic distress pattern detected ($detectedClass)")
            )
            _currentIncident.value = newIncident
            saveActiveIncident(newIncident)
        }
    }

    fun confirmSos(triggerSource: String = "MANUAL_BUTTON") {
        val incId = if (_currentIncident.value.id.isNotEmpty() && _currentIncident.value.state == IncidentState.VERIFYING) {
            _currentIncident.value.id
        } else {
            "INC-${System.currentTimeMillis().toString().takeLast(6)}"
        }

        // 1. Generate unique 256-bit AES incident key
        val masterKey = EvidenceCipher.generate256BitKey()
        incidentKeyBytes = masterKey
        val masterKeyHex = masterKey.joinToString("") { "%02x".format(it) }

        // 2. Compute Shamir's Secret Sharing: 50% threshold of trusted emergency contacts
        val emergencyContacts = _contacts.value.filter { it.isEmergencyContact }
        val n = emergencyContacts.size.coerceAtLeast(1)
        // 50% threshold: ceil(n * 0.5)
        val k = ((n + 1) / 2).coerceAtLeast(1)

        val keyShares = ShamirSecretSharing.split(masterKey, threshold = k, totalShares = n)

        // Assign authenticated key share to each contact
        val updatedContacts = _contacts.value.map { c ->
            val idx = emergencyContacts.indexOfFirst { it.id == c.id }
            if (idx != -1 && idx < keyShares.size) {
                c.copy(keyShareHex = keyShares[idx].toHex(), hasApprovedAccess = false)
            } else {
                c.copy(hasApprovedAccess = false)
            }
        }
        _contacts.value = updatedContacts
        scope.launch { prefsManager.saveContacts(updatedContacts) }

        val timeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val initialTimeline = if (_currentIncident.value.state == IncidentState.VERIFYING) {
            _currentIncident.value.timeline + listOf(
                "[$timeStr] Emergency SOS CONFIRMED via $triggerSource",
                "[$timeStr] Generated 256-bit AES master key & Shamir $k-of-$n threshold shares",
                "[$timeStr] Wearable camera START_RECORD command sent over BLE",
                "[$timeStr] Immediate cellular SMS broadcast dispatched",
                "[$timeStr] Initiated autonomous emergency calling sequence",
                "[$timeStr] Started 3-minute periodic GPS location update service"
            )
        } else {
            listOf(
                "[$timeStr] Emergency SOS INSTANTLY TRIGGERED via $triggerSource",
                "[$timeStr] Generated 256-bit AES master key & Shamir $k-of-$n threshold shares",
                "[$timeStr] Wearable camera START_RECORD command sent over BLE",
                "[$timeStr] Immediate cellular SMS broadcast dispatched",
                "[$timeStr] Initiated autonomous emergency calling sequence",
                "[$timeStr] Started 3-minute periodic GPS location update service"
            )
        }

        val confirmedIncident = Incident(
            id = incId,
            timestamp = System.currentTimeMillis(),
            latitude = _currentLocation.value.first,
            longitude = _currentLocation.value.second,
            state = IncidentState.CONFIRMED_SOS,
            triggerSource = triggerSource,
            alertsSentCount = emergencyContacts.size,
            timeline = initialTimeline,
            encryptionKeyHex = masterKeyHex,
            thresholdRequired = k,
            totalSharesCount = n
        )
        _currentIncident.value = confirmedIncident
        saveActiveIncident(confirmedIncident)

        // Automatically trigger recording command on wearable
        setWearableRecording(true)

        // Dispatch immediate autonomous cellular SMS to all configured Emergency Contacts
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
                    logEmergencyTimeline("Direct cellular SMS delivered to ${contact.name}")
                },
                onError = { err ->
                    logEmergencyTimeline("SMS failed for ${contact.name}: $err")
                }
            )
        }

        // Also dispatch to 112 National Police / Emergency with clear formal notification
        val police112Msg = EmergencyDispatcher.build112PoliceEmergencySmsText(
            userName = _user.value.name.ifBlank { "Citizen in Distress" },
            userPhone = _user.value.phone.ifBlank { "Unknown" },
            latitude = _currentLocation.value.first,
            longitude = _currentLocation.value.second,
            incidentId = incId,
            bloodGroup = _user.value.bloodGroup
        )
        EmergencyDispatcher.sendDirectSms(
            context = context,
            phoneNumber = "112",
            message = police112Msg,
            onSuccess = {
                logEmergencyTimeline("Emergency SMS delivered to 112 National Emergency Helpline")
            },
            onError = { err ->
                logEmergencyTimeline("112 SMS dispatch notice: $err")
            }
        )

        // Start Emergency Foreground Service (autonomous sequential calls & 3-min periodic GPS updates)
        EmergencySosService.start(context)
    }

    fun cancelIncident() {
        val timeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        _currentIncident.value = Incident(
            id = "",
            state = IncidentState.IDLE,
            timeline = listOf("[$timeStr] Incident cancelled by user (Marked Safe)")
        )
        clearActiveIncident()
        updateAudioDiagnostics { diag ->
            diag.copy(
                currentRiskLevel = RiskLevel.LOW,
                riskDecisionReason = "User marked Safe. Continuous monitoring active.",
                lastUpdatedTimestamp = System.currentTimeMillis()
            )
        }
        setWearableRecording(false)
        AutonomousCallManager.stopAllCalls(context)
        EmergencySosService.stop(context)
        activeAudioEngine?.onUserMarkedSafe()
    }

    fun resolveIncident() {
        val timeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        _currentIncident.value = Incident(
            id = "",
            state = IncidentState.IDLE,
            timeline = listOf("[$timeStr] Incident marked RESOLVED. All alerts closed.")
        )
        clearActiveIncident()
        updateAudioDiagnostics { diag ->
            diag.copy(
                currentRiskLevel = RiskLevel.LOW,
                riskDecisionReason = "Incident resolved by user. Continuous monitoring active.",
                lastUpdatedTimestamp = System.currentTimeMillis()
            )
        }
        setWearableRecording(false)
        AutonomousCallManager.stopAllCalls(context)
        EmergencySosService.stop(context)
        activeAudioEngine?.onUserMarkedSafe()
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

    /**
     * Approves access for a specific contact in Shamir's threshold scheme.
     * When >= 50% contacts approve, reconstructs the AES key to decrypt evidence.
     */
    fun toggleContactApproval(contactId: String): Boolean {
        val updated = _contacts.value.map {
            if (it.id == contactId) it.copy(hasApprovedAccess = !it.hasApprovedAccess) else it
        }
        _contacts.value = updated
        scope.launch { prefsManager.saveContacts(updated) }

        val approvedShares = updated.filter { it.isEmergencyContact && it.hasApprovedAccess && it.keyShareHex.isNotBlank() }
            .map { ShamirSecretSharing.Share.fromHex(it.keyShareHex) }

        val k = _currentIncident.value.thresholdRequired
        return if (approvedShares.size >= k) {
            try {
                val reconstructedKey = ShamirSecretSharing.reconstruct(approvedShares, k)
                incidentKeyBytes = reconstructedKey
                logEmergencyTimeline("Threshold reached (${approvedShares.size}/$k approvals). Reconstructed AES master key for evidence decryption.")
                true
            } catch (e: Exception) {
                false
            }
        } else {
            false
        }
    }

    fun addEvidenceSegment(segment: EvidenceSegment) {
        val updatedList = listOf(segment) + _evidenceList.value
        _evidenceList.value = updatedList
        _currentIncident.value = _currentIncident.value.copy(
            evidenceSegmentsCount = updatedList.size
        )

        // Autonomous Backblaze B2 Upload when internet connectivity is present
        scope.launch {
            if (b2Uploader.isInternetAvailable()) {
                logEmergencyTimeline("Internet connected. Autonomously uploading segment #${segment.segmentNumber} to Backblaze B2...")
                val result = b2Uploader.uploadEncryptedSegment(segment)
                result.onSuccess { cloudUrl ->
                    _evidenceList.value = _evidenceList.value.map {
                        if (it.id == segment.id) it.copy(isUploaded = true, cloudUrl = cloudUrl) else it
                    }
                    logEmergencyTimeline("Segment #${segment.segmentNumber} successfully stored in Backblaze B2 (Encrypted).")
                }.onFailure { err ->
                    logEmergencyTimeline("Backblaze upload error on #${segment.segmentNumber}: ${err.message}. Kept locally.")
                }
            } else {
                logEmergencyTimeline("No internet connection. Segment #${segment.segmentNumber} queued locally in encrypted vault.")
            }
        }
    }

    fun syncAllEvidence() {
        scope.launch {
            val pending = _evidenceList.value.filter { !it.isUploaded }
            for (seg in pending) {
                if (b2Uploader.isInternetAvailable()) {
                    val res = b2Uploader.uploadEncryptedSegment(seg)
                    res.onSuccess { cloudUrl ->
                        _evidenceList.value = _evidenceList.value.map {
                            if (it.id == seg.id) it.copy(isUploaded = true, cloudUrl = cloudUrl) else it
                        }
                    }
                }
            }
        }
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

        lateinit var instance: SaforaRepository
            internal set
    }
}
