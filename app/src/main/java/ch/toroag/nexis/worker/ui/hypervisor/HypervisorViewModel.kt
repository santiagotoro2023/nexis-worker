package ch.toroag.nexis.worker.ui.hypervisor

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import ch.toroag.nexis.worker.data.NexisApiService
import ch.toroag.nexis.worker.data.PreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class HypervisorViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = PreferencesRepository.get(app)
    private val api   = NexisApiService(prefs, app)

    private val _vms        = MutableStateFlow<List<NexisApiService.HvVm>>(emptyList())
    private val _containers = MutableStateFlow<List<NexisApiService.HvContainer>>(emptyList())
    private val _metrics    = MutableStateFlow<NexisApiService.HvMetrics?>(null)
    private val _cmdResult  = MutableStateFlow("")
    private val _error      = MutableStateFlow("")
    private val _loading    = MutableStateFlow(false)
    private val _configured = MutableStateFlow(false)

    val vms:        StateFlow<List<NexisApiService.HvVm>>        = _vms
    val containers: StateFlow<List<NexisApiService.HvContainer>> = _containers
    val metrics:    StateFlow<NexisApiService.HvMetrics?>        = _metrics
    val cmdResult:  StateFlow<String>                            = _cmdResult
    val error:      StateFlow<String>                            = _error
    val loading:    StateFlow<Boolean>                           = _loading
    val configured: StateFlow<Boolean>                          = _configured

    init {
        viewModelScope.launch {
            prefs.hvUrl.combine(prefs.hvToken) { url, tok -> url to tok }.collect { (url, tok) ->
                _configured.value = url.isNotEmpty() && tok.isNotEmpty()
                if (_configured.value) refresh()
            }
        }
    }

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            _loading.value = true
            _error.value   = ""
            val url = prefs.hvUrl.first()
            val tok = prefs.hvToken.first()
            if (url.isEmpty() || tok.isEmpty()) { _loading.value = false; return@launch }
            try {
                _metrics.value    = api.getHvMetrics(url, tok)
                _vms.value        = api.listHvVms(url, tok)
                _containers.value = api.listHvContainers(url, tok)
            } catch (e: Exception) {
                _error.value = e.message ?: "Connection error"
            } finally {
                _loading.value = false
            }
        }
    }

    fun vmAction(vmId: String, action: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val url = prefs.hvUrl.first()
            val tok = prefs.hvToken.first()
            api.hvVmAction(url, tok, vmId, action)
            refresh()
        }
    }

    fun containerAction(ctName: String, action: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val url = prefs.hvUrl.first()
            val tok = prefs.hvToken.first()
            api.hvContainerAction(url, tok, ctName, action)
            refresh()
        }
    }

    fun sendCommand(command: String) {
        if (command.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            _cmdResult.value = "Processing..."
            val url = prefs.hvUrl.first()
            val tok = prefs.hvToken.first()
            _cmdResult.value = api.hvCommand(url, tok, command)
        }
    }

    fun connect(hvUrl: String, password: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val tok = api.getHvToken(hvUrl, password)
                prefs.saveHvCredentials(hvUrl, tok)
                launch(Dispatchers.Main) { onSuccess() }
            } catch (e: Exception) {
                launch(Dispatchers.Main) { onError(e.message ?: "Connection failed") }
            }
        }
    }

    fun disconnect() {
        viewModelScope.launch { prefs.clearHvToken() }
    }
}
