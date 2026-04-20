package ch.toroag.nexis.desktop.ui.settings

import ch.toroag.nexis.desktop.data.CertPinStore
import ch.toroag.nexis.desktop.data.NexisApiService
import ch.toroag.nexis.desktop.data.PreferencesRepository
import ch.toroag.nexis.desktop.util.DesktopUpdateChecker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

sealed interface UpdateState {
    data object Idle        : UpdateState
    data object Checking    : UpdateState
    data object UpToDate    : UpdateState
    data class  Available(val release: DesktopUpdateChecker.Release) : UpdateState
    data class  Downloading(val progress: Int)                        : UpdateState
    data object Installing  : UpdateState
    data object Done        : UpdateState
    data class  Error(val msg: String)                                : UpdateState
}

class SettingsViewModel : AutoCloseable {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val prefs = PreferencesRepository.get()
    private val api   = NexisApiService(prefs)

    private val _baseUrl       = MutableStateFlow("")
    val baseUrl: StateFlow<String> = _baseUrl

    private val _certPin       = MutableStateFlow<String?>(null)
    val certPin: StateFlow<String?> = _certPin

    private val _status        = MutableStateFlow<String?>(null)
    val status: StateFlow<String?> = _status

    private val _updateState   = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState

    private val _health        = MutableStateFlow<NexisApiService.HealthInfo?>(null)
    val health: StateFlow<NexisApiService.HealthInfo?> = _health

    private val _healthLoading = MutableStateFlow(false)
    val healthLoading: StateFlow<Boolean> = _healthLoading

    private val _haConfig      = MutableStateFlow<NexisApiService.HaConfig?>(null)
    val haConfig: StateFlow<NexisApiService.HaConfig?> = _haConfig

    private val _haTestResult  = MutableStateFlow<String?>(null)
    val haTestResult: StateFlow<String?> = _haTestResult

    private val _haTestLoading = MutableStateFlow(false)
    val haTestLoading: StateFlow<Boolean> = _haTestLoading

    private var baseUrlCurrent = ""
    private var tokenCurrent   = ""

    init {
        scope.launch {
            combine(prefs.baseUrl, prefs.token) { u, t -> Pair(u, t) }.collect { (u, t) ->
                baseUrlCurrent = u; tokenCurrent = t
                _baseUrl.value = u
                _certPin.value = CertPinStore.getPin()
                if (u.isNotEmpty() && t.isNotEmpty()) { refreshHealth(); loadHaConfig() }
            }
        }
    }

    fun refreshHealth() {
        if (baseUrlCurrent.isEmpty() || tokenCurrent.isEmpty()) return
        scope.launch {
            _healthLoading.value = true
            _health.value = runCatching { api.getHealth(baseUrlCurrent, tokenCurrent) }.getOrNull()
            _healthLoading.value = false
        }
    }

    fun forgetCertificate() {
        CertPinStore.clearPin()
        _certPin.value = null
        _status.value = "Certificate forgotten — re-pair on next connection"
    }

    fun reAuthenticate(password: String, onDone: () -> Unit) {
        if (password.isBlank() || baseUrlCurrent.isEmpty()) return
        scope.launch {
            runCatching { api.getToken(baseUrlCurrent, password) }
                .onSuccess { token ->
                    prefs.saveCredentials(baseUrlCurrent, token)
                    _status.value = "Re-authenticated successfully"
                    onDone()
                }
                .onFailure { _status.value = "Auth failed: ${it.message}" }
        }
    }

    fun logout(onLogout: () -> Unit) {
        scope.launch {
            prefs.clearToken()
            CertPinStore.clearPin()
            onLogout()
        }
    }

    fun clearStatus() { _status.value = null }

    fun loadHaConfig() {
        if (baseUrlCurrent.isEmpty() || tokenCurrent.isEmpty()) return
        scope.launch(Dispatchers.IO) {
            _haConfig.value = runCatching { api.getHaConfig(baseUrlCurrent, tokenCurrent) }.getOrNull()
        }
    }

    fun saveHaConfig(haUrl: String, haUsername: String, haPassword: String, mainSwitch: String,
                     computerSwitch: String, startDelay: Int, stopDelay: Int) {
        scope.launch(Dispatchers.IO) {
            val cfg = NexisApiService.HaConfig(haUrl, haUsername, haPassword, mainSwitch, computerSwitch, startDelay, stopDelay)
            val ok  = runCatching { api.saveHaConfig(baseUrlCurrent, tokenCurrent, cfg) }.getOrDefault(false)
            if (ok) { _haConfig.value = cfg; _status.value = "Home Assistant settings saved" }
            else _status.value = "Failed to save HA settings"
        }
    }

    fun testHaConnection() {
        scope.launch(Dispatchers.IO) {
            _haTestLoading.value = true
            _haTestResult.value  = null
            val (ok, msg) = runCatching { api.testHaConnection(baseUrlCurrent, tokenCurrent) }
                .getOrDefault(Pair(false, "error"))
            _haTestResult.value  = if (ok) "✓ $msg" else "✗ $msg"
            _haTestLoading.value = false
        }
    }

    fun checkForUpdate() {
        if (_updateState.value is UpdateState.Checking || _updateState.value is UpdateState.Downloading) return
        scope.launch {
            _updateState.value = UpdateState.Checking
            val release = DesktopUpdateChecker.checkForUpdate()
            _updateState.value = if (release != null) UpdateState.Available(release) else UpdateState.UpToDate
        }
    }

    fun downloadAndInstall(release: DesktopUpdateChecker.Release) {
        scope.launch {
            _updateState.value = UpdateState.Downloading(0)
            val file = DesktopUpdateChecker.downloadDeb(release) { pct ->
                _updateState.value = UpdateState.Downloading(pct)
            }
            if (file == null) {
                _updateState.value = UpdateState.Error("Download failed")
                return@launch
            }
            _updateState.value = UpdateState.Installing
            val ok = DesktopUpdateChecker.installDeb(file)
            _updateState.value = if (ok) UpdateState.Done else UpdateState.Error("Install failed — try: sudo dpkg -i ${file.absolutePath}")
        }
    }

    fun dismissUpdate() { _updateState.value = UpdateState.Idle }

    override fun close() {}
}
