package ch.toroag.nexis.desktop.ui.history

import ch.toroag.nexis.desktop.data.NexisApiService
import ch.toroag.nexis.desktop.data.PreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class HistoryViewModel : AutoCloseable {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val prefs = PreferencesRepository.get()
    private val api   = NexisApiService(prefs)

    private val _history      = MutableStateFlow<List<NexisApiService.HistoryMessage>>(emptyList())
    val history: StateFlow<List<NexisApiService.HistoryMessage>> = _history

    private val _isLoading    = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    private var baseUrl = ""
    private var token   = ""

    init {
        scope.launch {
            combine(prefs.baseUrl, prefs.token) { u, t -> Pair(u, t) }.collect { (u, t) ->
                baseUrl = u; token = t
                if (u.isNotEmpty() && t.isNotEmpty()) loadHistory()
            }
        }
    }

    fun loadHistory() {
        scope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            runCatching { api.getHistory(baseUrl, token) }
                .onSuccess { _history.value = it }
                .onFailure { _errorMessage.value = it.message }
            _isLoading.value = false
        }
    }

    fun clearHistory() {
        scope.launch {
            runCatching { api.clearConversation(baseUrl, token) }
            loadHistory()
        }
    }

    override fun close() {}
}
