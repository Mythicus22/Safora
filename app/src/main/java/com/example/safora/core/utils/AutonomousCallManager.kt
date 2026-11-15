package com.example.safora.core.utils

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import java.util.Collections

object AutonomousCallManager {
    private const val TAG = "AutonomousCallManager"
    private const val PREFS_NAME = "safora_call_state"
    private const val KEY_CALLED_NUMBERS = "called_numbers"
    private const val KEY_SEQUENCE_COMPLETED = "sequence_completed"

    private var callScopeJob: Job? = null
    private val calledNumbersInCurrentIncident = Collections.synchronizedSet(mutableSetOf<String>())
    private var isSequenceRunning = false

    private fun getSavedCalledNumbers(context: Context): MutableSet<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return (prefs.getStringSet(KEY_CALLED_NUMBERS, emptySet()) ?: emptySet()).toMutableSet()
    }

    private fun saveCalledNumber(context: Context, number: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val current = getSavedCalledNumbers(context)
        current.add(number)
        prefs.edit().putStringSet(KEY_CALLED_NUMBERS, current).apply()
    }

    fun clearSavedCallState(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
    }

    private fun markSequenceCompleted(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_SEQUENCE_COMPLETED, true).apply()
    }

    fun isSequenceCompleted(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_SEQUENCE_COMPLETED, false)
    }

    /**
     * Halts all ongoing autonomous emergency calls immediately and clears state.
     */
    fun stopAllCalls(context: Context? = null) {
        Log.i(TAG, "Halting all autonomous emergency calls and resetting call state.")
        isSequenceRunning = false
        callScopeJob?.cancel()
        callScopeJob = null
        calledNumbersInCurrentIncident.clear()
        if (context != null) {
            clearSavedCallState(context)
        }
    }

    /**
     * Dials the given phone number. Uses ACTION_CALL if CALL_PHONE is granted,
     * otherwise falls back to ACTION_DIAL safely without crashing the app.
     */
    @SuppressLint("MissingPermission")
    fun initiateAutonomousCall(
        context: Context,
        phoneNumber: String,
        onCallAttempted: ((String) -> Unit)? = null,
        onError: ((String) -> Unit)? = null
    ) {
        val cleanNumber = phoneNumber.filter { it.isDigit() || it == '+' }
        if (cleanNumber.isBlank()) {
            onError?.invoke("Invalid phone number")
            return
        }

        try {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
                val callIntent = Intent(Intent.ACTION_CALL).apply {
                    data = Uri.parse("tel:$cleanNumber")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(callIntent)
                Log.i(TAG, "Autonomous direct call dispatched to $cleanNumber")
                onCallAttempted?.invoke(cleanNumber)
            } else {
                // Safe fallback to system dialer; never crash if runtime permission wasn't granted
                val dialIntent = Intent(Intent.ACTION_DIAL).apply {
                    data = Uri.parse("tel:$cleanNumber")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(dialIntent)
                Log.i(TAG, "Opened dialer for $cleanNumber (CALL_PHONE runtime permission not granted)")
                onCallAttempted?.invoke("$cleanNumber (Dialer)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initiate call to $cleanNumber: ${e.message}", e)
            onError?.invoke(e.message ?: "Failed to initiate call")
        }
    }

    /**
     * Executes sequential calls across emergency contacts.
     * Guarantees: Each contact is called AT MOST ONCE per incident, even across app exits and restarts.
     */
    fun executeSequentialCalls(
        context: Context,
        phoneNumbers: List<String>,
        onStatusUpdate: (String) -> Unit
    ) {
        if (isSequenceRunning) {
            Log.d(TAG, "Calling sequence is already running; skipping duplicate trigger.")
            return
        }
        if (isSequenceCompleted(context)) {
            Log.d(TAG, "Calling sequence was already completed for this incident; skipping re-execution.")
            return
        }
        isSequenceRunning = true

        val persistedCalled = getSavedCalledNumbers(context)
        calledNumbersInCurrentIncident.addAll(persistedCalled)

        callScopeJob?.cancel()
        callScopeJob = CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            try {
                for (phone in phoneNumbers) {
                    if (!isActive || !isSequenceRunning) break

                    val clean = phone.trim().filter { it.isDigit() || it == '+' }
                    if (clean.isBlank()) continue

                    // Strict requirement: Call is sent ONLY ONCE to each contact
                    if (calledNumbersInCurrentIncident.contains(clean)) {
                        Log.d(TAG, "Contact $clean has already been called in this incident. Skipping.")
                        continue
                    }
                    calledNumbersInCurrentIncident.add(clean)
                    saveCalledNumber(context, clean)

                    onStatusUpdate("Calling emergency contact $clean...")
                    initiateAutonomousCall(
                        context = context,
                        phoneNumber = clean,
                        onCallAttempted = {
                            onStatusUpdate("Call dispatched to $clean.")
                        },
                        onError = { err ->
                            onStatusUpdate("Call error on $clean: $err")
                        }
                    )

                    // Wait 15 seconds before considering next contact to give the dialer time
                    delay(15000)
                }
                if (isActive && isSequenceRunning) {
                    markSequenceCompleted(context)
                    onStatusUpdate("Autonomous emergency calling sequence completed.")
                }
            } catch (e: CancellationException) {
                Log.i(TAG, "Emergency call sequence cancelled.")
            } finally {
                isSequenceRunning = false
            }
        }
    }
}
