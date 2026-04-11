package ch.toroag.nexis.worker.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import ch.toroag.nexis.worker.data.NexisApiService
import ch.toroag.nexis.worker.data.PreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SettingsViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = PreferencesRepository.get(app)
    private val api   = NexisApiService(prefs)

    val baseUrl: StateFlow<String> get() = _baseUrl
    private val _baseUrl = MutableStateFlow("")

    private val _status = MutableStateFlow<String?>(null)
    val status: StateFlow<String?> = _status

    init {
        viewModelScope.launch {
            _baseUrl.value = prefs.baseUrl.first()
        }
    }

    fun logout(onDone: () -> Unit) {
        viewModelScope.launch {
            prefs.clearToken()
            onDone()
        }
    }

    fun reAuthenticate(newPassword: String, onSuccess: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val url = prefs.baseUrl.first()
            runCatching { api.getToken(url, newPassword) }
                .onSuccess { token ->
                    prefs.saveCredentials(url, token)
                    _status.value = "Re-authenticated successfully"
                    onSuccess()
                }
                .onFailure { _status.value = "Failed: ${it.message}" }
        }
    }

    fun clearStatus() { _status.value = null }
}
