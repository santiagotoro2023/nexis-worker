package ch.toroag.nexis.worker.ui.devices

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import ch.toroag.nexis.worker.data.NexisApiService
import ch.toroag.nexis.worker.data.PreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class DevicesViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = PreferencesRepository.get(app)
    private val api   = NexisApiService(prefs, app)

    private val _devices  = MutableStateFlow<List<NexisApiService.DeviceInfo>>(emptyList())
    val devices: StateFlow<List<NexisApiService.DeviceInfo>> = _devices

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _probeOutput  = MutableStateFlow<String?>(null)
    val probeOutput: StateFlow<String?> = _probeOutput

    private val _probeLoading = MutableStateFlow(false)
    val probeLoading: StateFlow<Boolean> = _probeLoading

    /** Map of deviceId → saved password, loaded from DataStore. */
    private val _passwords = MutableStateFlow<Map<String, String>>(emptyMap())
    val passwords: StateFlow<Map<String, String>> = _passwords

    private var baseUrl = ""
    private var token   = ""

    init {
        viewModelScope.launch {
            combine(prefs.baseUrl, prefs.token) { u, t -> Pair(u, t) }.collect { (u, t) ->
                baseUrl = u; token = t
                if (u.isNotEmpty() && t.isNotEmpty()) loadDevices()
            }
        }
    }

    fun loadDevices() {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            _devices.value = api.getDevices(baseUrl, token)
            _isLoading.value = false
        }
    }

    fun setRole(deviceId: String, role: String) {
        viewModelScope.launch(Dispatchers.IO) {
            api.setDeviceRole(baseUrl, token, deviceId, role)
            loadDevices()
        }
    }

    fun probeDevice(dev: NexisApiService.DeviceInfo) {
        viewModelScope.launch(Dispatchers.IO) {
            _probeLoading.value = true
            _probeOutput.value  = if (dev.deviceType == "desktop" && dev.online)
                api.probeController(baseUrl, token)
            else
                api.probeDevice(baseUrl, token, dev.deviceId)
            _probeLoading.value = false
        }
    }

    fun clearProbe() { _probeOutput.value = null }

    fun deleteDevice(deviceId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _devices.value = _devices.value.filterNot { it.deviceId == deviceId }
            api.deleteDevice(baseUrl, token, deviceId)
        }
    }

    /** Save the unlock password for a specific device to local DataStore. */
    fun saveDevicePassword(deviceId: String, password: String) {
        viewModelScope.launch(Dispatchers.IO) {
            prefs.saveDevicePassword(deviceId, password)
            // Refresh the in-memory map so the UI updates
            val updated = _passwords.value.toMutableMap()
            updated[deviceId] = password
            _passwords.value = updated
        }
    }

    /** Load saved passwords from DataStore into memory for all known devices. */
    fun loadPasswords(deviceIds: List<String>) {
        viewModelScope.launch(Dispatchers.IO) {
            val map = deviceIds.associateWith { prefs.getDevicePassword(it) }
            _passwords.value = map
        }
    }
}
