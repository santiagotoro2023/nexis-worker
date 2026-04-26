package ch.toroag.nexis.desktop.ui.hypervisor

import androidx.compose.runtime.*
import ch.toroag.nexis.desktop.data.NexisApiService
import ch.toroag.nexis.desktop.data.PreferencesRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

class HypervisorViewModel {

    private val prefs = PreferencesRepository.get()
    private val api   = NexisApiService(prefs)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    var vms        by mutableStateOf<List<NexisApiService.HvVm>>(emptyList())
    var containers by mutableStateOf<List<NexisApiService.HvContainer>>(emptyList())
    var metrics    by mutableStateOf<NexisApiService.HvMetrics?>(null)
    var cmdResult  by mutableStateOf("")
    var error      by mutableStateOf("")
    var loading    by mutableStateOf(false)
    var configured by mutableStateOf(false)

    init {
        scope.launch {
            prefs.hvToken.collect { tok ->
                val url = prefs.hvUrl.first()
                configured = url.isNotEmpty() && tok.isNotEmpty()
                if (configured) refresh()
            }
        }
    }

    fun refresh() {
        scope.launch {
            loading = true
            error   = ""
            val url = prefs.hvUrl.first()
            val tok = prefs.hvToken.first()
            if (url.isEmpty() || tok.isEmpty()) { loading = false; return@launch }
            try {
                metrics    = api.getHvMetrics(url, tok)
                vms        = api.listHvVms(url, tok)
                containers = api.listHvContainers(url, tok)
            } catch (e: Exception) {
                error = e.message ?: "Connection error"
            } finally {
                loading = false
            }
        }
    }

    fun vmAction(vmId: String, action: String) {
        scope.launch {
            val url = prefs.hvUrl.first()
            val tok = prefs.hvToken.first()
            api.hvVmAction(url, tok, vmId, action)
            refresh()
        }
    }

    fun containerAction(ctName: String, action: String) {
        scope.launch {
            val url = prefs.hvUrl.first()
            val tok = prefs.hvToken.first()
            api.hvContainerAction(url, tok, ctName, action)
            refresh()
        }
    }

    fun sendCommand(command: String) {
        if (command.isBlank()) return
        scope.launch {
            cmdResult = "Processing..."
            val url = prefs.hvUrl.first()
            val tok = prefs.hvToken.first()
            cmdResult = api.hvCommand(url, tok, command)
        }
    }

    fun connect(hvUrl: String, password: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        scope.launch {
            try {
                val tok = api.getHvToken(hvUrl, password)
                prefs.saveHvCredentials(hvUrl, tok)
                withContext(Dispatchers.Main) { onSuccess() }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { onError(e.message ?: "Connection failed") }
            }
        }
    }

    fun disconnect() {
        prefs.clearHvToken()
    }
}
