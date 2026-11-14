package com.example.safora.core.utils

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.ContactsContract
import com.example.safora.data.model.Contact

object ContactPickerHelper {
    /**
     * Resolves the selected contact Uri from the native Android Contact Picker
     * and extracts the contact's displayName and primary phoneNumber.
     */
    fun resolveContactFromUri(context: Context, contactUri: Uri): Contact? {
        var name = ""
        var phone = ""
        val contentResolver = context.contentResolver

        try {
            contentResolver.query(contactUri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idIndex = cursor.getColumnIndex(ContactsContract.Contacts._ID)
                    val hasPhoneIndex = cursor.getColumnIndex(ContactsContract.Contacts.HAS_PHONE_NUMBER)
                    val nameIndex = cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)

                    val contactId = if (idIndex != -1) cursor.getString(idIndex) else null
                    val hasPhoneNumber = if (hasPhoneIndex != -1) cursor.getInt(hasPhoneIndex) else 0
                    if (nameIndex != -1) {
                        name = cursor.getString(nameIndex) ?: "Contact"
                    }

                    if (hasPhoneNumber > 0 && contactId != null) {
                        contentResolver.query(
                            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                            null,
                            "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
                            arrayOf(contactId),
                            null
                        )?.use { phoneCursor ->
                            if (phoneCursor.moveToFirst()) {
                                val pIndex = phoneCursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                                if (pIndex != -1) {
                                    phone = phoneCursor.getString(pIndex) ?: ""
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return if (name.isNotBlank()) {
            Contact(
                id = "c_${System.currentTimeMillis()}",
                name = name,
                phone = if (phone.isNotBlank()) phone else "+91 (Selected Contact)",
                relationship = "Emergency Contact",
                isEmergencyContact = true
            )
        } else null
    }
}
