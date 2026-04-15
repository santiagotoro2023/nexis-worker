package ch.toroag.nexis.desktop.ui.login

import ch.toroag.nexis.desktop.data.CertPinStore
import ch.toroag.nexis.desktop.data.NexisApiService
import ch.toroag.nexis.desktop.data.PreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed interface LoginState {
    object Idle    : LoginState
    object Loading : LoginState
    object Success : LoginState
    data class Error(val message: String) : LoginState
}

class LoginViewModel : AutoCloseable {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val prefs = PreferencesRepository.get()
    private val api   = NexisApiService(prefs)

    private val _uiState = MutableStateFlow<LoginState>(LoginState.Idle)
    val uiState: StateFlow<LoginState> = _uiState

    val certFingerprint: String? get() = CertPinStore.getPin()

    fun login(baseUrl: String, password: String) {
        if (baseUrl.isBlank() || password.isBlank()) {
            _uiState.value = LoginState.Error("URL and password are required")
            return
        }
        val url = if (baseUrl.startsWith("http")) baseUrl else "https://$baseUrl"
        _uiState.value = LoginState.Loading
        scope.launch {
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

    override fun close() { /* scope cancelled on app exit */ }
}
