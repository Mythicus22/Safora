package com.example.safora.core.utils

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.telephony.SmsManager
import android.util.Log

object EmergencyDispatcher {
    private const val TAG = "EmergencyDispatcher"

    /**
     * Sends an actual cellular SMS to emergency contacts containing
     * the user's live coordinates and incident ID.
     */
    fun sendDirectSms(
        context: Context,
        phoneNumber: String,
        message: String,
        onSuccess: (() -> Unit)? = null,
        onError: ((String) -> Unit)? = null
    ) {
        val cleanNumber = phoneNumber.filter { it.isDigit() || it == '+' }
        if (cleanNumber.isBlank()) {
            onError?.invoke("Invalid phone number")
            return
        }

        try {
            val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }

            // Split long emergency messages into parts if needed
            val parts = smsManager.divideMessage(message)
            if (parts.size > 1) {
                smsManager.sendMultipartTextMessage(cleanNumber, null, parts, null, null)
            } else {
                smsManager.sendTextMessage(cleanNumber, null, message, null, null)
            }

            Log.i(TAG, "Direct cellular emergency SMS dispatched to $cleanNumber")
            onSuccess?.invoke()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send direct SMS to $cleanNumber: ${e.message}")
            onError?.invoke(e.message ?: "Failed to dispatch SMS")
        }
    }

    /**
     * Builds the standard SAFORA emergency broadcast payload.
     */
    fun buildEmergencySmsText(
        userName: String,
        latitude: Double,
        longitude: Double,
        incidentId: String,
        bloodGroup: String = ""
    ): String {
        val mapLink = "https://maps.google.com/?q=${latitude},${longitude}"
        val bloodText = if (bloodGroup.isNotBlank()) " Blood: $bloodGroup." else ""
        return "EMERGENCY ALERT: $userName needs immediate help! Location: $mapLink (Lat: ${"%.5f".format(latitude)}, Lng: ${"%.5f".format(longitude)}). Incident ID: $incidentId.$bloodText Sent via SAFORA."
    }
}
