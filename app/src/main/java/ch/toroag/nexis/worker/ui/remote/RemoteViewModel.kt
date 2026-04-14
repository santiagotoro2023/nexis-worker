package ch.toroag.nexis.worker.ui.remote

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import ch.toroag.nexis.worker.data.NexisApiService
import ch.toroag.nexis.worker.data.PreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class RemoteViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = PreferencesRepository.get(app)
    private val api   = NexisApiService(prefs, app)

    private val _result         = MutableStateFlow("")
    val result: StateFlow<String> = _result

    private val _isLoading      = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _devices        = MutableStateFlow<List<NexisApiService.DeviceInfo>>(emptyList())
    val devices: StateFlow<List<NexisApiService.DeviceInfo>> = _devices

    private val _selectedDevice = MutableStateFlow<NexisApiService.DeviceInfo?>(null)
    val selectedDevice: StateFlow<NexisApiService.DeviceInfo?> = _selectedDevice

    private val _devicesLoading = MutableStateFlow(false)
    val devicesLoading: StateFlow<Boolean> = _devicesLoading

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
            _devicesLoading.value = true
            val all = api.getDevices(baseUrl, token).filter { it.deviceType == "desktop" }
            _devices.value = all
            if (_selectedDevice.value == null || _selectedDevice.value !in all) {
                _selectedDevice.value = all.firstOrNull { it.role == "primary_pc" }
                    ?: all.firstOrNull()
            }
            _devicesLoading.value = false
        }
    }

    fun selectDevice(device: NexisApiService.DeviceInfo?) {
        _selectedDevice.value = device
    }

    fun action(action: String, arg: String = "") {
        if (_isLoading.value || baseUrl.isEmpty() || token.isEmpty()) return
        val devId = _selectedDevice.value?.deviceId ?: ""
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            _result.value    = ""
            _result.value    = api.desktopAction(baseUrl, token, action, arg, devId)
            _isLoading.value = false
        }
    }

    fun pasteFromPc() {
        if (_isLoading.value || baseUrl.isEmpty() || token.isEmpty()) return
        val devId = _selectedDevice.value?.deviceId ?: ""
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            _result.value    = ""
            val text = api.desktopAction(baseUrl, token, "clip_read", "", devId)
            if (!text.startsWith("(")) {
                val cm = getApplication<Application>()
                    .getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("NeXiS PC Clipboard", text))
                _result.value = "pasted: ${text.take(80)}${if (text.length > 80) "…" else ""}"
            } else {
                _result.value = text
            }
            _isLoading.value = false
        }
    }
}
