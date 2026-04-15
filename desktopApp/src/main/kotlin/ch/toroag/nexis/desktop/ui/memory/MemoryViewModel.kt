package ch.toroag.nexis.desktop.ui.memory

import ch.toroag.nexis.desktop.data.NexisApiService
import ch.toroag.nexis.desktop.data.PreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class MemoryViewModel : AutoCloseable {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val prefs = PreferencesRepository.get()
    private val api   = NexisApiService(prefs)

    private val _memories     = MutableStateFlow<List<NexisApiService.MemoryEntry>>(emptyList())
    val memories: StateFlow<List<NexisApiService.MemoryEntry>> = _memories

    private val _isLoading    = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _searchQuery  = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    private var baseUrl = ""
    private var token   = ""

    init {
        scope.launch {
            combine(prefs.baseUrl, prefs.token) { u, t -> Pair(u, t) }.collect { (u, t) ->
                baseUrl = u; token = t
                if (u.isNotEmpty() && t.isNotEmpty()) loadMemories()
            }
        }
    }

    fun loadMemories() {
        scope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            runCatching { api.getMemories(baseUrl, token) }
                .onSuccess { list ->
                    val q = _searchQuery.value.lowercase()
                    _memories.value = if (q.isEmpty()) list
                                      else list.filter { it.content.lowercase().contains(q) }
                }
                .onFailure { _errorMessage.value = it.message }
            _isLoading.value = false
        }
    }

    fun setSearchQuery(q: String) {
        _searchQuery.value = q
        loadMemories()
    }

    fun deleteMemory(id: Int) {
        scope.launch {
            runCatching { api.deleteMemory(baseUrl, token, id) }
            loadMemories()
        }
    }

    override fun close() {}
}
