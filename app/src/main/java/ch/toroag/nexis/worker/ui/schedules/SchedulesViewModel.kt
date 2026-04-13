package ch.toroag.nexis.worker.ui.schedules

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

typealias Schedule = NexisApiService.ScheduleEntry

class SchedulesViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = PreferencesRepository.get(app)
    private val api   = NexisApiService(prefs, app)

    private val _schedules    = MutableStateFlow<List<Schedule>>(emptyList())
    private val _isLoading    = MutableStateFlow(false)
    private val _errorMessage = MutableStateFlow<String?>(null)

    val schedules:    StateFlow<List<Schedule>> = _schedules
    val isLoading:    StateFlow<Boolean>         = _isLoading
    val errorMessage: StateFlow<String?>         = _errorMessage

    private var baseUrl = ""
    private var token   = ""

    init {
        viewModelScope.launch {
            combine(prefs.baseUrl, prefs.token) { u, t -> u to t }.collect { (u, t) ->
                baseUrl = u; token = t
                if (u.isNotEmpty() && t.isNotEmpty()) loadSchedules()
            }
        }
    }

    fun loadSchedules() {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            _errorMessage.value = null
            runCatching { api.getSchedules(baseUrl, token) }
                .onSuccess { _schedules.value = it }
                .onFailure { _errorMessage.value = it.message }
            _isLoading.value = false
        }
    }

    fun toggle(id: Int, active: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { api.scheduleAction(baseUrl, token, "toggle", id = id, active = active) }
                .onSuccess {
                    _schedules.value = _schedules.value.map {
                        if (it.id == id) it.copy(active = active) else it
                    }
                }
                .onFailure { _errorMessage.value = it.message }
        }
    }

    fun delete(id: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { api.scheduleAction(baseUrl, token, "delete", id = id) }
                .onSuccess { _schedules.value = _schedules.value.filter { it.id != id } }
                .onFailure { _errorMessage.value = it.message }
        }
    }

    fun runNow(id: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { api.scheduleAction(baseUrl, token, "run", id = id) }
                .onFailure { _errorMessage.value = it.message }
        }
    }

    fun addSchedule(name: String, expr: String, prompt: String, onDone: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                api.scheduleAction(baseUrl, token, "add",
                    name = name, expr = expr, prompt = prompt)
            }
                .onSuccess { loadSchedules(); viewModelScope.launch { onDone() } }
                .onFailure { _errorMessage.value = it.message }
        }
    }

    fun clearError() { _errorMessage.value = null }
}
