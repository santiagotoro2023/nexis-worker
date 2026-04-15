package ch.toroag.nexis.desktop.ui.schedules

import ch.toroag.nexis.desktop.data.NexisApiService
import ch.toroag.nexis.desktop.data.PreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class SchedulesViewModel : AutoCloseable {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val prefs = PreferencesRepository.get()
    private val api   = NexisApiService(prefs)

    private val _schedules    = MutableStateFlow<List<NexisApiService.ScheduleEntry>>(emptyList())
    val schedules: StateFlow<List<NexisApiService.ScheduleEntry>> = _schedules

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
                if (u.isNotEmpty() && t.isNotEmpty()) loadSchedules()
            }
        }
    }

    fun loadSchedules() {
        scope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            runCatching { api.getSchedules(baseUrl, token) }
                .onSuccess { _schedules.value = it }
                .onFailure { _errorMessage.value = it.message }
            _isLoading.value = false
        }
    }

    fun deleteSchedule(id: Int) {
        scope.launch {
            runCatching { api.scheduleAction(baseUrl, token, "delete", id) }
            loadSchedules()
        }
    }

    override fun close() {}
}
