package ch.toroag.nexis.desktop.ui.devices

import ch.toroag.nexis.desktop.data.NexisApiService
import ch.toroag.nexis.desktop.data.PreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class DevicesViewModel : AutoCloseable {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val prefs = PreferencesRepository.get()
    private val api   = NexisApiService(prefs)

    private val _devices      = MutableStateFlow<List<NexisApiService.DeviceInfo>>(emptyList())
    val devices: StateFlow<List<NexisApiService.DeviceInfo>> = _devices

    private val _isLoading    = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _probeOutput  = MutableStateFlow<String?>(null)
    val probeOutput: StateFlow<String?> = _probeOutput

    private val _probeLoading = MutableStateFlow(false)
    val probeLoading: StateFlow<Boolean> = _probeLoading

    private val _passwords    = MutableStateFlow<Map<String, String>>(emptyMap())
    val passwords: StateFlow<Map<String, String>> = _passwords

    private var baseUrl = ""
    private var token   = ""

    init {
        scope.launch {
            combine(prefs.baseUrl, prefs.token) { u, t -> Pair(u, t) }.collect { (u, t) ->
                baseUrl = u; token = t
                if (u.isNotEmpty() && t.isNotEmpty()) loadDevices()
            }
        }
    }

    fun loadDevices() {
        scope.launch(Dispatchers.IO) {
            val u = baseUrl.ifEmpty { prefs.baseUrl.first() }
            val t = token.ifEmpty { prefs.token.first() }
            if (u.isEmpty() || t.isEmpty()) return@launch
            _isLoading.value = true
            val devs = runCatching { api.getDevices(u, t) }.getOrDefault(emptyList())
            _devices.value = devs
            // Sync passwords from server and merge into local cache
            val serverPasswords = runCatching { api.getDevicePasswords(u, t) }.getOrDefault(emptyMap())
            if (serverPasswords.isNotEmpty()) {
                serverPasswords.forEach { (id, pw) -> prefs.saveDevicePassword(id, pw) }
            }
            val allIds = devs.map { it.deviceId }
            _passwords.value = allIds.associateWith { prefs.getDevicePassword(it) }
            _isLoading.value = false
        }
    }

    fun saveDevicePassword(deviceId: String, password: String) {
        prefs.saveDevicePassword(deviceId, password)
        scope.launch(Dispatchers.IO) {
            runCatching { api.saveDevicePasswordRemote(baseUrl, token, deviceId, password) }
        }
        _passwords.value = _passwords.value + (deviceId to password)
    }

    fun setRole(deviceId: String, role: String) {
        scope.launch {
            runCatching { api.setDeviceRole(baseUrl, token, deviceId, role) }
            loadDevices()
        }
    }

    fun deleteDevice(deviceId: String) {
        scope.launch {
            runCatching { api.deleteDevice(baseUrl, token, deviceId) }
            _devices.value = _devices.value.filter { it.deviceId != deviceId }
        }
    }

    fun probeDevice(dev: NexisApiService.DeviceInfo) {
        scope.launch {
            _probeLoading.value = true
            _probeOutput.value = null
            _probeOutput.value = runCatching {
                if (dev.deviceType == "desktop" && dev.online)
                    api.probeController(baseUrl, token)
                else
                    api.probeDevice(baseUrl, token, dev.deviceId)
            }.getOrElse { it.message ?: "error" }
            _probeLoading.value = false
        }
    }

    fun clearProbe() {
        _probeOutput.value  = null
        _probeLoading.value = false
    }

    override fun close() {}
}
