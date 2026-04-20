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

    private val _haConfig      = MutableStateFlow<NexisApiService.HaConfig?>(null)
    val haConfig: StateFlow<NexisApiService.HaConfig?> = _haConfig

    private val _haTestResult  = MutableStateFlow<String?>(null)
    val haTestResult: StateFlow<String?> = _haTestResult

    private val _haTestLoading = MutableStateFlow(false)
    val haTestLoading: StateFlow<Boolean> = _haTestLoading

    init {
        viewModelScope.launch {
            _baseUrl.value = prefs.baseUrl.first()
            _certPin.value = CertPinStore.getPin(getApplication())
        }
        refreshHealth()
        loadHaConfig()
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

    fun loadHaConfig() {
        viewModelScope.launch(Dispatchers.IO) {
            val url = prefs.baseUrl.first(); val tok = prefs.token.first()
            if (url.isNotEmpty() && tok.isNotEmpty())
                _haConfig.value = api.getHaConfig(url, tok)
        }
    }

    fun saveHaConfig(haUrl: String, haToken: String, mainSwitch: String,
                     computerSwitch: String, startDelay: Int, stopDelay: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val url = prefs.baseUrl.first(); val tok = prefs.token.first()
            val cfg = NexisApiService.HaConfig(haUrl, haToken, mainSwitch, computerSwitch, startDelay, stopDelay)
            val ok  = runCatching { api.saveHaConfig(url, tok, cfg) }.getOrDefault(false)
            if (ok) { _haConfig.value = cfg; _status.value = "Home Assistant settings saved" }
            else _status.value = "Failed to save HA settings"
        }
    }

    fun testHaConnection() {
        viewModelScope.launch(Dispatchers.IO) {
            _haTestLoading.value = true
            _haTestResult.value  = null
            val url = prefs.baseUrl.first(); val tok = prefs.token.first()
            val (ok, msg) = runCatching { api.testHaConnection(url, tok) }.getOrDefault(Pair(false, "error"))
            _haTestResult.value  = if (ok) "✓ $msg" else "✗ $msg"
            _haTestLoading.value = false
        }
    }

    fun clearStatus() { _status.value = null }
}
