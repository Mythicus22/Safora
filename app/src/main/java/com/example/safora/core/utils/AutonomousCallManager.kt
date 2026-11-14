package com.example.safora.core.utils

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.telephony.TelephonyManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object AutonomousCallManager {
    private const val TAG = "AutonomousCallManager"

    /**
     * Autonomously dials the given phone number sequentially.
     * Uses Intent.ACTION_CALL which immediately initiates the outbound call
     * without presenting the dialler keypad or waiting for the user to press Call.
     *
     * Note: Requires android.permission.CALL_PHONE granted at runtime.
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
            val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            if (telephonyManager?.simState != TelephonyManager.SIM_STATE_READY) {
                Log.w(TAG, "SIM card not ready. SimState: ${telephonyManager?.simState}")
            }

            val callIntent = Intent(Intent.ACTION_CALL).apply {
                data = Uri.parse("tel:$cleanNumber")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }

            context.startActivity(callIntent)
            Log.i(TAG, "Autonomous call initiated to $cleanNumber")
            onCallAttempted?.invoke(cleanNumber)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initiate autonomous call: ${e.message}")
            onError?.invoke(e.message ?: "Failed to initiate call")
        }
    }

    /**
     * Executes sequential autonomous calls across a list of phone numbers with delays.
     * Missed call duration simulation: dials contact 1, waits 15-20s, dials contact 2, etc.
     */
    fun executeSequentialCalls(
        context: Context,
        phoneNumbers: List<String>,
        maxRetriesPerContact: Int = 1,
        onStatusUpdate: (String) -> Unit
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            for (phone in phoneNumbers) {
                var attempt = 0
                while (attempt < maxRetriesPerContact) {
                    attempt++
                    onStatusUpdate("Dialing contact $phone (Attempt $attempt/$maxRetriesPerContact)...")
                    initiateAutonomousCall(
                        context = context,
                        phoneNumber = phone,
                        onCallAttempted = {
                            onStatusUpdate("Call dispatched to $phone.")
                        },
                        onError = { err ->
                            onStatusUpdate("Call error on $phone: $err")
                        }
                    )
                    // Wait before dialing next contact
                    delay(12000)
                }
            }
            onStatusUpdate("Autonomous emergency calling sequence finished.")
        }
    }
}
