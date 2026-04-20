package ch.toroag.nexis.worker.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONObject
import java.util.UUID

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "nexis_prefs")

class PreferencesRepository(private val context: Context) {

    companion object {
        private val BASE_URL_KEY         = stringPreferencesKey("base_url")
        private val TOKEN_KEY            = stringPreferencesKey("bearer_token")
        private val DEVICE_ID_KEY        = stringPreferencesKey("device_id")
        private val CACHED_DEVICES_KEY   = stringPreferencesKey("cached_devices")
        private val DEVICE_PASSWORDS_KEY = stringPreferencesKey("device_passwords")

        @Volatile private var instance: PreferencesRepository? = null
        fun get(context: Context): PreferencesRepository =
            instance ?: synchronized(this) {
                instance ?: PreferencesRepository(context.applicationContext).also { instance = it }
            }
    }

    val baseUrl:  Flow<String> = context.dataStore.data.map { it[BASE_URL_KEY]  ?: "" }
    val token:    Flow<String> = context.dataStore.data.map { it[TOKEN_KEY]     ?: "" }
    val deviceId: Flow<String> = context.dataStore.data.map { it[DEVICE_ID_KEY] ?: "" }

    /** Returns the persistent device UUID, generating one on first call. */
    suspend fun getOrCreateDeviceId(): String {
        val existing = context.dataStore.data.first()[DEVICE_ID_KEY] ?: ""
        if (existing.isNotEmpty()) return existing
        val newId = UUID.randomUUID().toString()
        context.dataStore.edit { prefs -> prefs[DEVICE_ID_KEY] = newId }
        return newId
    }

    suspend fun saveCredentials(baseUrl: String, token: String) {
        context.dataStore.edit { prefs ->
            prefs[BASE_URL_KEY] = baseUrl.trimEnd('/')
            prefs[TOKEN_KEY]    = token
        }
    }

    suspend fun updateBaseUrl(baseUrl: String) {
        context.dataStore.edit { prefs -> prefs[BASE_URL_KEY] = baseUrl.trimEnd('/') }
    }

    suspend fun clearToken() {
        context.dataStore.edit { prefs -> prefs.remove(TOKEN_KEY) }
    }

    // ── Device cache (for WOL when server is offline) ─────────────────────────

    suspend fun saveCachedDevices(json: String) {
        context.dataStore.edit { prefs -> prefs[CACHED_DEVICES_KEY] = json }
    }

    suspend fun getCachedDevices(): String =
        context.dataStore.data.first()[CACHED_DEVICES_KEY] ?: "[]"

    // ── Per-device passwords (used for PC unlock) ─────────────────────────────

    suspend fun saveDevicePassword(deviceId: String, password: String) {
        val raw = context.dataStore.data.first()[DEVICE_PASSWORDS_KEY] ?: "{}"
        val map = runCatching { JSONObject(raw) }.getOrDefault(JSONObject())
        map.put(deviceId, password)
        context.dataStore.edit { prefs -> prefs[DEVICE_PASSWORDS_KEY] = map.toString() }
    }

    suspend fun getDevicePassword(deviceId: String): String {
        val raw = context.dataStore.data.first()[DEVICE_PASSWORDS_KEY] ?: "{}"
        return runCatching { JSONObject(raw).optString(deviceId, "") }.getOrDefault("")
    }
}
