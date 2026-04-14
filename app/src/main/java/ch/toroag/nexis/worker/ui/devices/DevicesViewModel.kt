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

    fun probeDevice() {
        viewModelScope.launch(Dispatchers.IO) {
            _probeLoading.value = true
            _probeOutput.value  = api.probeController(baseUrl, token)
            _probeLoading.value = false
        }
    }

    fun clearProbe() { _probeOutput.value = null }
}
