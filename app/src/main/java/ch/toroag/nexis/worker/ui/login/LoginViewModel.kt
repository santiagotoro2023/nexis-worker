package ch.toroag.nexis.worker.ui.login

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import ch.toroag.nexis.worker.data.NexisApiService
import ch.toroag.nexis.worker.data.PreferencesRepository
import ch.toroag.nexis.worker.util.CertPinStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class LoginViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = PreferencesRepository.get(app)
    private val api   = NexisApiService(prefs, app)

    private val _uiState = MutableStateFlow<LoginState>(LoginState.Idle)
    val uiState: StateFlow<LoginState> = _uiState

    /** Fingerprint of the server cert, set after first successful connection. */
    val certFingerprint: String? get() = CertPinStore.getPin(getApplication())

    fun login(baseUrl: String, password: String) {
        if (baseUrl.isBlank() || password.isBlank()) {
            _uiState.value = LoginState.Error("URL and password are required")
            return
        }
        val url = if (baseUrl.startsWith("http")) baseUrl else "https://$baseUrl"
        _uiState.value = LoginState.Loading
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { api.getToken(url, password) }
                .onSuccess { token ->
                    prefs.saveCredentials(url, token)
                    _uiState.value = LoginState.Success
                }
                .onFailure { e ->
                    _uiState.value = LoginState.Error(e.message ?: "Connection failed")
                }
        }
    }

    fun clearError() { _uiState.value = LoginState.Idle }
}

sealed interface LoginState {
    object Idle    : LoginState
    object Loading : LoginState
    object Success : LoginState
    data class Error(val message: String) : LoginState
}
