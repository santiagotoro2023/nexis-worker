package ch.toroag.nexis.worker.ui.memories

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

typealias Memory = NexisApiService.MemoryEntry

class MemoriesViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = PreferencesRepository.get(app)
    private val api   = NexisApiService(prefs, app)

    private val _allMemories  = MutableStateFlow<List<Memory>>(emptyList())
    private val _isLoading    = MutableStateFlow(false)
    private val _searchQuery  = MutableStateFlow("")
    private val _memories     = MutableStateFlow<List<Memory>>(emptyList())
    private val _errorMessage = MutableStateFlow<String?>(null)

    val memories:     StateFlow<List<Memory>>  = _memories
    val isLoading:    StateFlow<Boolean>        = _isLoading
    val searchQuery:  StateFlow<String>         = _searchQuery
    val errorMessage: StateFlow<String?>        = _errorMessage

    private var baseUrl = ""
    private var token   = ""

    init {
        viewModelScope.launch {
            combine(prefs.baseUrl, prefs.token) { u, t -> u to t }.collect { (u, t) ->
                baseUrl = u; token = t
                if (u.isNotEmpty() && t.isNotEmpty()) loadMemories()
            }
        }
        viewModelScope.launch {
            combine(_allMemories, _searchQuery) { all, q ->
                if (q.isBlank()) all
                else all.filter { it.content.contains(q, ignoreCase = true) }
            }.collect { _memories.value = it }
        }
    }

    fun loadMemories() {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            _errorMessage.value = null
            runCatching { api.getMemories(baseUrl, token) }
                .onSuccess { _allMemories.value = it }
                .onFailure { _errorMessage.value = it.message }
            _isLoading.value = false
        }
    }

    fun deleteMemory(id: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { api.deleteMemory(baseUrl, token, id) }
                .onSuccess { _allMemories.value = _allMemories.value.filter { it.id != id } }
                .onFailure { _errorMessage.value = it.message }
        }
    }

    fun setSearchQuery(q: String) { _searchQuery.value = q }
    fun clearError() { _errorMessage.value = null }
}
