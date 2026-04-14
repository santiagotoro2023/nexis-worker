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
import java.util.UUID

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "nexis_prefs")

class PreferencesRepository(private val context: Context) {

    companion object {
        private val BASE_URL_KEY   = stringPreferencesKey("base_url")
        private val TOKEN_KEY      = stringPreferencesKey("bearer_token")
        private val NTFY_TOPIC_KEY = stringPreferencesKey("ntfy_topic")
        private val DEVICE_ID_KEY  = stringPreferencesKey("device_id")

        @Volatile private var instance: PreferencesRepository? = null
        fun get(context: Context): PreferencesRepository =
            instance ?: synchronized(this) {
                instance ?: PreferencesRepository(context.applicationContext).also { instance = it }
            }
    }

    val baseUrl:   Flow<String> = context.dataStore.data.map { it[BASE_URL_KEY]   ?: "" }
    val token:     Flow<String> = context.dataStore.data.map { it[TOKEN_KEY]      ?: "" }
    val ntfyTopic: Flow<String> = context.dataStore.data.map { it[NTFY_TOPIC_KEY] ?: "" }
    val deviceId:  Flow<String> = context.dataStore.data.map { it[DEVICE_ID_KEY]  ?: "" }

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

    suspend fun saveNtfyTopic(topic: String) {
        context.dataStore.edit { prefs -> prefs[NTFY_TOPIC_KEY] = topic.trim() }
    }

}
