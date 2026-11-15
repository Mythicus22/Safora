package com.example.safora.core.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SmsManager
import android.util.Log
import androidx.core.content.ContextCompat

object EmergencyDispatcher {
    private const val TAG = "EmergencyDispatcher"

    /**
     * Sends an actual cellular SMS to emergency contacts or emergency services (112).
     */
    fun sendDirectSms(
        context: Context,
        phoneNumber: String,
        message: String,
        onSuccess: (() -> Unit)? = null,
        onError: ((String) -> Unit)? = null
    ) {
        val cleanNumber = phoneNumber.trim().filter { it.isDigit() || it == '+' }
        if (cleanNumber.isBlank()) {
            onError?.invoke("Invalid phone number")
            return
        }

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            val err = "SEND_SMS permission not granted. Please allow SMS permission in app settings."
            Log.e(TAG, err)
            onError?.invoke(err)
            return
        }

        try {
            val smsManager: SmsManager = try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    context.getSystemService(SmsManager::class.java)
                        ?: @Suppress("DEPRECATION") SmsManager.getDefault()
                } else {
                    @Suppress("DEPRECATION") SmsManager.getDefault()
                }
            } catch (_: Exception) {
                @Suppress("DEPRECATION") SmsManager.getDefault()
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
            Log.e(TAG, "Failed to send direct SMS to $cleanNumber: ${e.message}", e)
            onError?.invoke(e.message ?: "Failed to dispatch SMS")
        }
    }

    /**
     * Builds emergency broadcast payload for trusted contacts.
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
        return "EMERGENCY SOS: $userName needs immediate help! Location: $mapLink (Lat: ${"%.5f".format(latitude)}, Lng: ${"%.5f".format(longitude)}). Incident ID: $incidentId.$bloodText Sent via SAFORA Safety Shield."
    }

    /**
     * Builds formal police / ERSS emergency SMS payload tailored specifically for 112.
     */
    fun build112PoliceEmergencySmsText(
        userName: String,
        userPhone: String,
        latitude: Double,
        longitude: Double,
        incidentId: String,
        bloodGroup: String = ""
    ): String {
        val mapLink = "https://maps.google.com/?q=${latitude},${longitude}"
        val bloodText = if (bloodGroup.isNotBlank()) " Blood Group: $bloodGroup." else ""
        return "EMERGENCY 112: My name is $userName (Phone: $userPhone). I am in immediate distress and danger and need urgent emergency assistance. My current GPS location: $mapLink (Coordinates: ${"%.5f".format(latitude)}, ${"%.5f".format(longitude)}). Incident ID: $incidentId.$bloodText Sent via SAFORA Autonomous Safety System."
    }
}
