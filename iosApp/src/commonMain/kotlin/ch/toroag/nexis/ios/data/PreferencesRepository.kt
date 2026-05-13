package ch.toroag.nexis.ios.data

import com.russhwolf.settings.Settings
import com.russhwolf.settings.get
import com.russhwolf.settings.set
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

expect fun createSettings(): Settings

object PreferencesRepository {
    private val settings: Settings = createSettings()
    private const val K_BASE_URL  = "base_url"
    private const val K_TOKEN     = "bearer_token"
    private const val K_USERNAME  = "username"
    private const val K_ROLE      = "role"
    private const val K_HV_URL    = "hv_url"
    private const val K_HV_TOKEN  = "hv_token"
    private const val K_DEVICE_ID = "device_id"

    private val _baseUrl  = MutableStateFlow(settings.get(K_BASE_URL,  ""))
    private val _token    = MutableStateFlow(settings.get(K_TOKEN,     ""))
    private val _username = MutableStateFlow(settings.get(K_USERNAME,  ""))
    private val _role     = MutableStateFlow(settings.get(K_ROLE,      "user"))
    private val _hvUrl    = MutableStateFlow(settings.get(K_HV_URL,    ""))
    private val _hvToken  = MutableStateFlow(settings.get(K_HV_TOKEN,  ""))

    val baseUrl:  StateFlow<String> = _baseUrl
    val token:    StateFlow<String> = _token
    val username: StateFlow<String> = _username
    val role:     StateFlow<String> = _role
    val hvUrl:    StateFlow<String> = _hvUrl
    val hvToken:  StateFlow<String> = _hvToken

    fun saveCredentials(baseUrl: String, token: String, username: String = "", role: String = "user") {
        val url = baseUrl.trimEnd('/')
        settings[K_BASE_URL] = url
        settings[K_TOKEN]    = token
        settings[K_ROLE]     = role
        if (username.isNotEmpty()) { settings[K_USERNAME] = username; _username.value = username }
        _baseUrl.value = url; _token.value = token; _role.value = role
    }

    fun clearToken() { settings.remove(K_TOKEN); _token.value = "" }

    fun saveHvCredentials(hvUrl: String, hvToken: String) {
        val url = hvUrl.trimEnd('/')
        settings[K_HV_URL] = url; settings[K_HV_TOKEN] = hvToken
        _hvUrl.value = url; _hvToken.value = hvToken
    }

    fun clearHvToken() { settings.remove(K_HV_TOKEN); _hvToken.value = "" }

    fun getOrCreateDeviceId(): String {
        val existing: String = settings.get(K_DEVICE_ID, "")
        if (existing.isNotEmpty()) return existing
        val newId = generateUuid()
        settings[K_DEVICE_ID] = newId
        return newId
    }

    fun get() = this
}

expect fun generateUuid(): String
