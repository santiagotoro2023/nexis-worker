package ch.toroag.nexis.worker.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "nexis_prefs")

class PreferencesRepository(private val context: Context) {

    companion object {
        private val BASE_URL_KEY      = stringPreferencesKey("base_url")
        private val TOKEN_KEY         = stringPreferencesKey("bearer_token")
        private val WAKE_WORD_KEY_KEY = stringPreferencesKey("porcupine_access_key")
        private val WAKE_WORD_ON_KEY  = booleanPreferencesKey("wake_word_enabled")

        @Volatile private var instance: PreferencesRepository? = null
        fun get(context: Context): PreferencesRepository =
            instance ?: synchronized(this) {
                instance ?: PreferencesRepository(context.applicationContext).also { instance = it }
            }
    }

    val baseUrl:       Flow<String>  = context.dataStore.data.map { it[BASE_URL_KEY]      ?: "" }
    val token:         Flow<String>  = context.dataStore.data.map { it[TOKEN_KEY]         ?: "" }
    val wakeWordKey:   Flow<String>  = context.dataStore.data.map { it[WAKE_WORD_KEY_KEY] ?: "" }
    val wakeWordEnabled: Flow<Boolean> = context.dataStore.data.map { it[WAKE_WORD_ON_KEY] ?: false }

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

    suspend fun saveWakeWordKey(key: String) {
        context.dataStore.edit { prefs -> prefs[WAKE_WORD_KEY_KEY] = key }
    }

    suspend fun setWakeWordEnabled(on: Boolean) {
        context.dataStore.edit { prefs -> prefs[WAKE_WORD_ON_KEY] = on }
    }
}
