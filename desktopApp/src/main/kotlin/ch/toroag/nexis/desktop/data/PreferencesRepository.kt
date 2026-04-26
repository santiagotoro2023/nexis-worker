package ch.toroag.nexis.desktop.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONObject
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import java.util.prefs.Preferences as JavaPrefs

/**
 * Desktop preferences backed by the Java Preferences API (writes to
 * ~/.java/.userPrefs on Linux, or the user registry node for this package).
 */
class PreferencesRepository private constructor() {

    private val javaPrefs: JavaPrefs = JavaPrefs.userRoot().node("/ch/toroag/nexis/worker")

    // ── Keys ──────────────────────────────────────────────────────────────────
    private val K_BASE_URL        = "base_url"
    private val K_TOKEN           = "bearer_token"
    private val K_DEVICE_ID       = "device_id"
    private val K_CACHED_DEVICES  = "cached_devices"
    private val K_DEVICE_PASSWORDS = "device_passwords"
    private val K_HV_URL           = "hv_url"
    private val K_HV_TOKEN         = "hv_token"

    // ── StateFlows (updated after writes) ─────────────────────────────────────
    private val _baseUrl  = MutableStateFlow(javaPrefs.get(K_BASE_URL, ""))
    private val _token    = MutableStateFlow(javaPrefs.get(K_TOKEN,    ""))
    private val _deviceId = MutableStateFlow(javaPrefs.get(K_DEVICE_ID, ""))
    private val _hvUrl    = MutableStateFlow(javaPrefs.get(K_HV_URL,    ""))
    private val _hvToken  = MutableStateFlow(javaPrefs.get(K_HV_TOKEN,  ""))

    val baseUrl:  Flow<String> = _baseUrl
    val token:    Flow<String> = _token
    val deviceId: Flow<String> = _deviceId
    val hvUrl:    Flow<String> = _hvUrl
    val hvToken:  Flow<String> = _hvToken

    // ── Auth ──────────────────────────────────────────────────────────────────

    suspend fun saveCredentials(baseUrl: String, token: String) {
        val url = baseUrl.trimEnd('/')
        javaPrefs.put(K_BASE_URL, url)
        javaPrefs.put(K_TOKEN,    token)
        javaPrefs.flush()
        _baseUrl.value = url
        _token.value   = token
    }

    suspend fun clearToken() {
        javaPrefs.remove(K_TOKEN)
        javaPrefs.flush()
        _token.value = ""
    }

    fun saveHvCredentials(hvUrl: String, hvToken: String) {
        val url = hvUrl.trimEnd('/')
        javaPrefs.put(K_HV_URL,   url)
        javaPrefs.put(K_HV_TOKEN, hvToken)
        runCatching { javaPrefs.flush() }
        _hvUrl.value   = url
        _hvToken.value = hvToken
    }

    fun clearHvToken() {
        javaPrefs.remove(K_HV_TOKEN)
        runCatching { javaPrefs.flush() }
        _hvToken.value = ""
    }

    // ── Device ID ─────────────────────────────────────────────────────────────

    fun getOrCreateDeviceId(): String {
        val existing = javaPrefs.get(K_DEVICE_ID, "")
        if (existing.isNotEmpty()) return existing
        val newId = UUID.randomUUID().toString()
        javaPrefs.put(K_DEVICE_ID, newId)
        runCatching { javaPrefs.flush() }
        _deviceId.value = newId
        return newId
    }

    // ── Device cache (WOL when server is offline) ─────────────────────────────

    fun saveCachedDevices(json: String) {
        javaPrefs.put(K_CACHED_DEVICES, json)
        runCatching { javaPrefs.flush() }
    }

    fun getCachedDevices(): String = javaPrefs.get(K_CACHED_DEVICES, "[]")

    // ── Per-device passwords ──────────────────────────────────────────────────

    fun saveDevicePassword(deviceId: String, password: String) {
        val raw = javaPrefs.get(K_DEVICE_PASSWORDS, "{}")
        val map = runCatching { JSONObject(raw) }.getOrDefault(JSONObject())
        map.put(deviceId, password)
        javaPrefs.put(K_DEVICE_PASSWORDS, map.toString())
        runCatching { javaPrefs.flush() }
    }

    fun getDevicePassword(deviceId: String): String {
        val raw = javaPrefs.get(K_DEVICE_PASSWORDS, "{}")
        return runCatching { JSONObject(raw).optString(deviceId, "") }.getOrDefault("")
    }

    // ── Singleton ─────────────────────────────────────────────────────────────
    companion object {
        @Volatile private var instance: PreferencesRepository? = null
        fun get(): PreferencesRepository =
            instance ?: synchronized(this) {
                instance ?: PreferencesRepository().also { instance = it }
            }
    }
}
