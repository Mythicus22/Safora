package com.example.safora.data.repository

import com.example.safora.core.crypto.EvidenceCipher
import com.example.safora.data.local.MockDataProvider
import com.example.safora.data.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

class SaforaRepository {
    // Current user state
    private val _user = MutableStateFlow(User())
    val user: StateFlow<User> = _user.asStateFlow()

    // Wearable device state
    private val _wearable = MutableStateFlow(WearableDevice())
    val wearable: StateFlow<WearableDevice> = _wearable.asStateFlow()

    // Incident state
    private val _currentIncident = MutableStateFlow(Incident())
    val currentIncident: StateFlow<Incident> = _currentIncident.asStateFlow()

    // Audio monitoring status
    private val _isAudioMonitoring = MutableStateFlow(false)
    val isAudioMonitoring: StateFlow<Boolean> = _isAudioMonitoring.asStateFlow()

    private val _lastAudioDetection = MutableStateFlow(
        AudioDetectionResult(detectedClass = "Normal Speech / Ambient", confidence = 0.96f, timestamp = System.currentTimeMillis())
    )
    val lastAudioDetection: StateFlow<AudioDetectionResult> = _lastAudioDetection.asStateFlow()

    // Location state
    private val _currentLocation = MutableStateFlow(Pair(28.6139, 77.2090)) // Default New Delhi coordinates
    val currentLocation: StateFlow<Pair<Double, Double>> = _currentLocation.asStateFlow()

    // Contacts
    private val _contacts = MutableStateFlow(MockDataProvider.getInitialContacts())
    val contacts: StateFlow<List<Contact>> = _contacts.asStateFlow()

    // Nearby Responders
    private val _responders = MutableStateFlow(MockDataProvider.getInitialResponders())
    val responders: StateFlow<List<Responder>> = _responders.asStateFlow()

    // Evidence Segments
    private val _evidenceList = MutableStateFlow(MockDataProvider.getSampleEvidence())
    val evidenceList: StateFlow<List<EvidenceSegment>> = _evidenceList.asStateFlow()

    // Wi-Fi Config
    private val _esp32WifiHost = MutableStateFlow("192.168.4.1")
    val esp32WifiHost: StateFlow<String> = _esp32WifiHost.asStateFlow()

    // Demo Mode toggle
    private val _isDemoMode = MutableStateFlow(true)
    val isDemoMode: StateFlow<Boolean> = _isDemoMode.asStateFlow()

    fun updateDemoMode(enabled: Boolean) {
        _isDemoMode.value = enabled
    }

    fun updateUser(updated: User) {
        _user.value = updated
    }

    fun updateLocation(lat: Double, lng: Double) {
        _currentLocation.value = Pair(lat, lng)
        _responders.value = MockDataProvider.getInitialResponders(lat, lng)
    }

    fun updateWearable(updated: WearableDevice) {
        _wearable.value = updated
    }

    fun setWearableRecording(isRecording: Boolean) {
        _wearable.value = _wearable.value.copy(isRecording = isRecording)
    }

    fun setAudioMonitoring(active: Boolean) {
        _isAudioMonitoring.value = active
    }

    fun updateAudioDetection(result: AudioDetectionResult) {
        _lastAudioDetection.value = result
    }

    fun triggerPossibleDistress(detectedClass: String = "Scream / Distress Call") {
        if (_currentIncident.value.state == IncidentState.IDLE || _currentIncident.value.state == IncidentState.CANCELLED) {
            val incId = "INC-${System.currentTimeMillis().toString().takeLast(6)}"
            _currentIncident.value = Incident(
                id = incId,
                timestamp = System.currentTimeMillis(),
                latitude = _currentLocation.value.first,
                longitude = _currentLocation.value.second,
                state = IncidentState.VERIFYING,
                triggerSource = "VOICE_ML: $detectedClass"
            )
        }
    }

    fun confirmSos(triggerSource: String = "MANUAL_BUTTON") {
        val incId = if (_currentIncident.value.id.isNotEmpty() && _currentIncident.value.state == IncidentState.VERIFYING) {
            _currentIncident.value.id
        } else {
            "INC-${System.currentTimeMillis().toString().takeLast(6)}"
        }

        _currentIncident.value = Incident(
            id = incId,
            timestamp = System.currentTimeMillis(),
            latitude = _currentLocation.value.first,
            longitude = _currentLocation.value.second,
            state = IncidentState.CONFIRMED_SOS,
            triggerSource = triggerSource,
            alertsSentCount = _contacts.value.count { it.isEmergencyContact }
        )

        // Automatically trigger recording command on wearable
        _wearable.value = _wearable.value.copy(isRecording = true)
    }

    fun cancelIncident() {
        _currentIncident.value = _currentIncident.value.copy(state = IncidentState.CANCELLED)
        _wearable.value = _wearable.value.copy(isRecording = false)
    }

    fun resolveIncident() {
        _currentIncident.value = _currentIncident.value.copy(state = IncidentState.RESOLVED)
        _wearable.value = _wearable.value.copy(isRecording = false)
    }

    fun addContact(contact: Contact) {
        _contacts.value = _contacts.value + contact
    }

    fun removeContact(contactId: String) {
        _contacts.value = _contacts.value.filter { it.id != contactId }
    }

    fun addEvidenceSegment(segment: EvidenceSegment) {
        _evidenceList.value = listOf(segment) + _evidenceList.value
        _currentIncident.value = _currentIncident.value.copy(
            evidenceSegmentsCount = _evidenceList.value.size
        )
    }

    fun setEsp32WifiHost(host: String) {
        _esp32WifiHost.value = host
    }

    companion object {
        val instance = SaforaRepository()
    }
}
