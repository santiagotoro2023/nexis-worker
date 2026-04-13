package ch.toroag.nexis.worker.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import ch.toroag.nexis.worker.data.NexisApiService
import ch.toroag.nexis.worker.data.PreferencesRepository
import ch.toroag.nexis.worker.util.CertPinStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.map

class SettingsViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = PreferencesRepository.get(app)
    private val api   = NexisApiService(prefs, app)

    private val _baseUrl = MutableStateFlow("")
    val baseUrl: StateFlow<String> = _baseUrl

    private val _certPin = MutableStateFlow<String?>(null)
    val certPin: StateFlow<String?> = _certPin

    private val _status  = MutableStateFlow<String?>(null)
    val status: StateFlow<String?> = _status

    private val _health  = MutableStateFlow<NexisApiService.HealthInfo?>(null)
    val health: StateFlow<NexisApiService.HealthInfo?> = _health

    private val _healthLoading = MutableStateFlow(false)
    val healthLoading: StateFlow<Boolean> = _healthLoading

    val ntfyTopic = prefs.ntfyTopic

    init {
        viewModelScope.launch {
            _baseUrl.value = prefs.baseUrl.first()
            _certPin.value = CertPinStore.getPin(getApplication())
        }
        refreshHealth()
    }

    fun refreshHealth() {
        viewModelScope.launch(Dispatchers.IO) {
            _healthLoading.value = true
            val url   = prefs.baseUrl.first()
            val token = prefs.token.first()
            if (url.isNotEmpty() && token.isNotEmpty()) {
                _health.value = api.getHealth(url, token)
            }
            _healthLoading.value = false
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
                    _status.value = "Re-authenticated"
                    onSuccess()
                }
                .onFailure { _status.value = "Failed: ${it.message}" }
        }
    }

    fun forgetCertificate() {
        CertPinStore.clearPin(getApplication())
        _certPin.value = null
        _status.value = "Certificate cleared — next connection will re-pair"
    }

    fun saveNtfyTopic(topic: String) {
        viewModelScope.launch { prefs.saveNtfyTopic(topic) }
    }

    fun clearStatus() { _status.value = null }
}
