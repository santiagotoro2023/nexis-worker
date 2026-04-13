package ch.toroag.nexis.worker.ui.remote

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

class RemoteViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = PreferencesRepository.get(app)
    private val api   = NexisApiService(prefs, app)

    private val _response  = MutableStateFlow("")
    val response: StateFlow<String> = _response

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private var baseUrl = ""
    private var token   = ""

    init {
        viewModelScope.launch {
            combine(prefs.baseUrl, prefs.token) { u, t -> Pair(u, t) }.collect { (u, t) ->
                baseUrl = u; token = t
            }
        }
    }

    fun send(command: String) {
        if (_isLoading.value || baseUrl.isEmpty() || token.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            _response.value  = ""
            api.streamChat(
                baseUrl      = baseUrl,
                token        = token,
                msg          = command,
                onToken      = { tok -> _response.value = _response.value + tok },
                onAudioReady = {},
                onDone       = { _isLoading.value = false },
                onError      = { err -> _isLoading.value = false; _response.value = "Error: $err" },
            )
        }
    }
}
