package ch.toroag.nexis.worker.ui.history

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

typealias HistorySession = NexisApiService.SessionSummary
typealias PreviewMsg     = NexisApiService.SessionMessage

class HistoryViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = PreferencesRepository.get(app)
    private val api   = NexisApiService(prefs, app)

    private val _sessions     = MutableStateFlow<List<HistorySession>>(emptyList())
    private val _isLoading    = MutableStateFlow(false)
    private val _errorMessage = MutableStateFlow<String?>(null)

    val sessions:     StateFlow<List<HistorySession>> = _sessions
    val isLoading:    StateFlow<Boolean>               = _isLoading
    val errorMessage: StateFlow<String?>               = _errorMessage

    private var baseUrl = ""
    private var token   = ""

    init {
        viewModelScope.launch {
            combine(prefs.baseUrl, prefs.token) { u, t -> u to t }.collect { (u, t) ->
                baseUrl = u; token = t
                if (u.isNotEmpty() && t.isNotEmpty()) loadSessions()
            }
        }
    }

    fun loadSessions() {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            _errorMessage.value = null
            runCatching { api.getHistorySessions(baseUrl, token) }
                .onSuccess { _sessions.value = it }
                .onFailure { _errorMessage.value = it.message }
            _isLoading.value = false
        }
    }

    fun loadSession(sessionId: String, onSuccess: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            _errorMessage.value = null
            runCatching { api.loadHistorySession(baseUrl, token, sessionId) }
                .onSuccess { viewModelScope.launch { onSuccess() } }
                .onFailure { _errorMessage.value = it.message }
            _isLoading.value = false
        }
    }

    fun clearError() { _errorMessage.value = null }
}
