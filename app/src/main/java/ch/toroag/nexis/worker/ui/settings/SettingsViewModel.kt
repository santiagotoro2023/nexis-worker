package ch.toroag.nexis.worker.ui.settings

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import ch.toroag.nexis.worker.data.NexisApiService
import ch.toroag.nexis.worker.data.PreferencesRepository
import ch.toroag.nexis.worker.util.CertPinStore
import ch.toroag.nexis.worker.util.WakeWordService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SettingsViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = PreferencesRepository.get(app)
    private val api   = NexisApiService(prefs, app)

    private val _baseUrl       = MutableStateFlow("")
    val baseUrl: StateFlow<String> = _baseUrl

    private val _certPin       = MutableStateFlow<String?>(null)
    val certPin: StateFlow<String?> = _certPin

    private val _status        = MutableStateFlow<String?>(null)
    val status: StateFlow<String?> = _status

    private val _wakeWordKey     = MutableStateFlow("")
    val wakeWordKey: StateFlow<String> = _wakeWordKey

    private val _wakeWordEnabled = MutableStateFlow(false)
    val wakeWordEnabled: StateFlow<Boolean> = _wakeWordEnabled

    init {
        viewModelScope.launch {
            _baseUrl.value        = prefs.baseUrl.first()
            _certPin.value        = CertPinStore.getPin(getApplication())
            _wakeWordKey.value    = prefs.wakeWordKey.first()
            _wakeWordEnabled.value = prefs.wakeWordEnabled.first()
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

    /** Clears the pinned certificate. The next connection will re-pin automatically. */
    fun forgetCertificate() {
        CertPinStore.clearPin(getApplication())
        _certPin.value = null
        _status.value = "Certificate cleared — next connection will re-pair"
    }

    fun clearStatus() { _status.value = null }

    fun saveWakeWordKey(key: String) {
        _wakeWordKey.value = key
        viewModelScope.launch { prefs.saveWakeWordKey(key) }
    }

    fun setWakeWordEnabled(on: Boolean) {
        _wakeWordEnabled.value = on
        viewModelScope.launch {
            prefs.setWakeWordEnabled(on)
            val ctx = getApplication<Application>()
            val svc = Intent(ctx, WakeWordService::class.java)
            if (on) {
                svc.action = WakeWordService.ACTION_START
                ctx.startForegroundService(svc)
            } else {
                svc.action = WakeWordService.ACTION_STOP
                ctx.startService(svc)
            }
        }
    }
}
