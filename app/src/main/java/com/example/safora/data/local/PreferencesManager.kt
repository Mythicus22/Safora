package com.example.safora.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.safora.data.model.Contact
import com.example.safora.data.model.User
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

val Context.dataStore by preferencesDataStore(name = "safora_settings")

class PreferencesManager(private val context: Context) {

    companion object {
        val KEY_NAME = stringPreferencesKey("user_name")
        val KEY_PHONE = stringPreferencesKey("user_phone")
        val KEY_BLOOD = stringPreferencesKey("user_blood")
        val KEY_ONBOARDED = booleanPreferencesKey("user_onboarded")
        val KEY_DEMO_MODE = booleanPreferencesKey("demo_mode")
        val KEY_WIFI_HOST = stringPreferencesKey("wifi_host")
        val KEY_CONTACTS_JSON = stringPreferencesKey("contacts_json")
        val KEY_PERMISSIONS_REQUESTED_ONCE = booleanPreferencesKey("permissions_requested_once")
    }

    val userFlow: Flow<User> = context.dataStore.data.map { prefs ->
        User(
            name = prefs[KEY_NAME] ?: "",
            phone = prefs[KEY_PHONE] ?: "",
            bloodGroup = prefs[KEY_BLOOD] ?: "O+",
            isOnboarded = prefs[KEY_ONBOARDED] ?: false
        )
    }

    val contactsFlow: Flow<List<Contact>> = context.dataStore.data.map { prefs ->
        val jsonStr = prefs[KEY_CONTACTS_JSON] ?: ""
        if (jsonStr.isBlank()) {
            emptyList()
        } else {
            try {
                val array = JSONArray(jsonStr)
                val list = mutableListOf<Contact>()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    list.add(
                        Contact(
                            id = obj.optString("id", "c_$i"),
                            name = obj.optString("name", "Contact"),
                            phone = obj.optString("phone", ""),
                            relationship = obj.optString("relationship", "Emergency Contact"),
                            isEmergencyContact = obj.optBoolean("isEmergencyContact", true)
                        )
                    )
                }
                list
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    val demoModeFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_DEMO_MODE] ?: true
    }

    val wifiHostFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_WIFI_HOST] ?: "192.168.4.1"
    }

    val permissionsRequestedOnceFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_PERMISSIONS_REQUESTED_ONCE] ?: false
    }

    suspend fun saveUser(user: User) {
        context.dataStore.edit { prefs ->
            prefs[KEY_NAME] = user.name
            prefs[KEY_PHONE] = user.phone
            prefs[KEY_BLOOD] = user.bloodGroup
            prefs[KEY_ONBOARDED] = user.isOnboarded
        }
    }

    suspend fun saveContacts(contacts: List<Contact>) {
        val array = JSONArray()
        contacts.forEach { c ->
            val obj = JSONObject().apply {
                put("id", c.id)
                put("name", c.name)
                put("phone", c.phone)
                put("relationship", c.relationship)
                put("isEmergencyContact", c.isEmergencyContact)
            }
            array.put(obj)
        }
        context.dataStore.edit { prefs ->
            prefs[KEY_CONTACTS_JSON] = array.toString()
        }
    }

    suspend fun setDemoMode(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_DEMO_MODE] = enabled
        }
    }

    suspend fun setWifiHost(host: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_WIFI_HOST] = host
        }
    }

    suspend fun setPermissionsRequestedOnce(requested: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_PERMISSIONS_REQUESTED_ONCE] = requested
        }
    }
}
