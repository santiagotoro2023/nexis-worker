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
    private val _nodes      = MutableStateFlow<List<NexisApiService.HvNode>>(emptyList())
    private val _error      = MutableStateFlow("")
    private val _loading    = MutableStateFlow(false)
    private val _connected  = MutableStateFlow(false)

    val vms:       StateFlow<List<NexisApiService.HvVm>>       = _vms
    val containers: StateFlow<List<NexisApiService.HvContainer>> = _containers
    val nodes:     StateFlow<List<NexisApiService.HvNode>>     = _nodes
    val error:     StateFlow<String>                           = _error
    val loading:   StateFlow<Boolean>                          = _loading
    val connected: StateFlow<Boolean>                          = _connected

    init {
        viewModelScope.launch {
            prefs.baseUrl.combine(prefs.token) { url, tok -> url to tok }.collect { (url, tok) ->
                _connected.value = url.isNotEmpty() && tok.isNotEmpty()
                if (_connected.value) refresh()
            }
        }
    }

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            _loading.value = true
            _error.value   = ""
            val baseUrl = prefs.baseUrl.first()
            val token   = prefs.token.first()
            if (baseUrl.isEmpty() || token.isEmpty()) { _loading.value = false; return@launch }
            try {
                _nodes.value      = api.listHypNodes(baseUrl, token)
                _vms.value        = api.listHypVms(baseUrl, token)
                _containers.value = api.listHypContainers(baseUrl, token)
            } catch (e: Exception) {
                _error.value = e.message ?: "Connection error"
            } finally {
                _loading.value = false
            }
        }
    }

    fun vmAction(nodeId: String, vmId: String, action: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val baseUrl = prefs.baseUrl.first()
            val token   = prefs.token.first()
            try {
                api.hypVmAction(baseUrl, token, nodeId, vmId, action)
                refresh()
            } catch (e: Exception) {
                _error.value = e.message ?: "Action failed"
            }
        }
    }

    fun containerAction(nodeId: String, ctName: String, action: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val baseUrl = prefs.baseUrl.first()
            val token   = prefs.token.first()
            try {
                api.hypContainerAction(baseUrl, token, nodeId, ctName, action)
                refresh()
            } catch (e: Exception) {
                _error.value = e.message ?: "Action failed"
            }
        }
    }
}
