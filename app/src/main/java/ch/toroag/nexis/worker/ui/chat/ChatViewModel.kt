package ch.toroag.nexis.worker.ui.chat

import android.app.Application
import android.content.Context
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.viewModelScope
import ch.toroag.nexis.worker.data.NexisApiService
import ch.toroag.nexis.worker.data.PreferencesRepository
import ch.toroag.nexis.worker.util.CommandExecutor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

data class ChatMessage(
    val role:       String,   // "user" | "assistant"
    val content:    String,
    val id:         Long = System.currentTimeMillis(),
    val hasImage:   Boolean = false,
    val isDocument: Boolean = false,
)

enum class ConnectionStatus { Connected, Connecting, Disconnected }

class ChatViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = PreferencesRepository.get(app)
    private val api   = NexisApiService(prefs, app)

    private val _messages      = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages

    private val _isStreaming   = MutableStateFlow(false)
    val isStreaming: StateFlow<Boolean> = _isStreaming

    private val _errorMessage  = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    private val _models        = MutableStateFlow<List<NexisApiService.ModelInfo>>(emptyList())
    val models: StateFlow<List<NexisApiService.ModelInfo>> = _models

    private val _currentModel  = MutableStateFlow("")
    val currentModel: StateFlow<String> = _currentModel

    private val _voiceEnabled   = MutableStateFlow(true)
    val voiceEnabled: StateFlow<Boolean> = _voiceEnabled

    private val _externalTyping   = MutableStateFlow(false)
    val externalTyping: StateFlow<Boolean> = _externalTyping

    private val _connectionStatus = MutableStateFlow(ConnectionStatus.Connecting)
    val connectionStatus: StateFlow<ConnectionStatus> = _connectionStatus

    private var baseUrl      = ""
    private var token        = ""
    private var audioPlayer: AudioPlayer? = null
    private var syncJob:     Job? = null
    private var pollJob:     Job? = null
    private var syncHistLen  = -1
    private var _syncRetries = 0

    init {
        viewModelScope.launch {
            combine(prefs.baseUrl, prefs.token) { u, t -> Pair(u, t) }.collect { (u, t) ->
                if (u.isNotEmpty() && t.isNotEmpty()) {
                    baseUrl = u; token = t
                    initAudioPlayer()
                    loadModels()
                    initHistory()
                    registerThisDevice()
                    startCommandPolling()   // (re)start whenever credentials load
                    viewModelScope.launch(Dispatchers.IO) {
                        runCatching { api.enableVoice(u, t, true) }
                    }
                }
            }
        }
        // Start polling only when app is foregrounded; cancel when backgrounded.
        // This is the primary battery-saving measure — the 2s loop only runs while visible.
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                if (baseUrl.isNotEmpty() && token.isNotEmpty()) startCommandPolling()
            }
            override fun onStop(owner: LifecycleOwner) { pollJob?.cancel() }
        })
    }

    private fun registerThisDevice() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val deviceId = prefs.getOrCreateDeviceId()
                val bm = getApplication<Application>()
                    .getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
                val battPct  = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                val charging = bm?.isCharging ?: false
                val ip = try {
                    val wm = getApplication<Application>()
                        .getSystemService(Context.WIFI_SERVICE) as? WifiManager
                    val addr = wm?.connectionInfo?.ipAddress ?: 0
                    "${addr and 0xff}.${addr shr 8 and 0xff}.${addr shr 16 and 0xff}.${addr shr 24 and 0xff}"
                } catch (_: Exception) { "" }
                val info = JSONObject().apply {
                    put("device_id",   deviceId)
                    put("hostname",    Build.MODEL)
                    put("model",       "${Build.MANUFACTURER} ${Build.MODEL}")
                    put("os",          "Android ${Build.VERSION.RELEASE}")
                    put("arch",        Build.SUPPORTED_ABIS.firstOrNull() ?: "")
                    put("device_type", "mobile")
                    put("capabilities", JSONArray(listOf("intents", "open_url", "notify", "clip")))
                    put("ip",          ip)
                    if (battPct != null) put("battery_pct", battPct)
                    put("charging",    charging)
                }
                api.registerDevice(baseUrl, token, info)
            } catch (_: Exception) {}
        }
    }

    private fun startCommandPolling() {
        pollJob?.cancel()
        pollJob = viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                try {
                    val deviceId = prefs.getOrCreateDeviceId()
                    if (deviceId.isNotEmpty() && baseUrl.isNotEmpty() && token.isNotEmpty()) {
                        val cmds = api.pollCommands(baseUrl, token, deviceId)
                        if (cmds.isNotEmpty()) {
                            val ctx = getApplication<Application>()
                            cmds.forEach { CommandExecutor.execute(ctx, it) }
                            api.ackCommands(baseUrl, token, cmds.map { it.id })
                        }
                    }
                } catch (_: Exception) {}
                delay(2_000)
            }
        }
    }

    private fun initHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            val hist = api.getHistory(baseUrl, token)
            if (hist.isNotEmpty()) {
                _messages.value = hist.mapIndexed { i, m ->
                    ChatMessage(m.role, m.content, i.toLong())
                }
                syncHistLen = hist.size
            }
            startSync()
        }
    }

    private fun startSync() {
        _connectionStatus.value = ConnectionStatus.Connecting
        syncJob?.cancel()
        syncJob = viewModelScope.launch(Dispatchers.IO) {
            api.streamSyncWithAlerts(
                baseUrl  = baseUrl,
                token    = token,
                onEvent  = { typing, histLen ->
                    _connectionStatus.value = ConnectionStatus.Connected
                    _syncRetries = 0
                    if (typing && !_isStreaming.value) {
                        _externalTyping.value = true
                    } else if (!typing) {
                        _externalTyping.value = false
                        if (!_isStreaming.value && syncHistLen >= 0 && histLen > syncHistLen) {
                            val hist = api.getHistory(baseUrl, token)
                            if (hist.isNotEmpty()) {
                                _messages.value = hist.mapIndexed { i, m ->
                                    ChatMessage(m.role, m.content, i.toLong())
                                }
                            }
                        }
                        syncHistLen = maxOf(syncHistLen, histLen)
                    }
                },
                onAlert  = { _, msg, _ ->
                    // Show in the chat error banner so the user sees it inline
                    _errorMessage.value = "⚠ $msg"
                },
                onClosed = {
                    _connectionStatus.value = ConnectionStatus.Disconnected
                    _externalTyping.value = false
                    viewModelScope.launch {
                        val wait = minOf(5_000L * (1L shl minOf(_syncRetries, 4)), 60_000L)
                        _syncRetries++
                        delay(wait)
                        if (baseUrl.isNotEmpty() && token.isNotEmpty()) startSync()
                    }
                },
            )
        }
    }

    private fun initAudioPlayer() {
        audioPlayer?.destroy()
        audioPlayer = AudioPlayer(api, baseUrl, token, getApplication<Application>().cacheDir)
    }

    fun sendMessage(
        text:          String,
        imageBase64:   String? = null,
        imageMimeType: String? = null,
        imageName:     String? = null,
    ) {
        if (_isStreaming.value || (text.isBlank() && imageBase64 == null)) return
        viewModelScope.launch(Dispatchers.IO) {
            val isDoc       = imageBase64 != null && (imageMimeType == null || !imageMimeType.startsWith("image/"))
            val displayText = text.ifBlank { if (isDoc) "[File]" else "[Image]" }
            _messages.value = _messages.value + ChatMessage(
                role       = "user",
                content    = displayText,
                hasImage   = imageBase64 != null && !isDoc,
                isDocument = isDoc,
            )
            val assistantId = System.currentTimeMillis() + 1
            _messages.value = _messages.value + ChatMessage("assistant", "", assistantId)
            _isStreaming.value = true
            _errorMessage.value = null

            if (_voiceEnabled.value) api.enableVoice(baseUrl, token, true)

            api.streamChat(
                baseUrl       = baseUrl,
                token         = token,
                msg           = text,
                fileData      = imageBase64,
                fileMimeType  = imageMimeType,
                fileName      = imageName,
                onToken       = { tok ->
                    _messages.value = _messages.value.map { m ->
                        if (m.id == assistantId) m.copy(content = m.content + tok) else m
                    }
                },
                onClear       = {
                    // Server found tool tags — clear first-pass text and wait for real answer
                    _messages.value = _messages.value.map { m ->
                        if (m.id == assistantId) m.copy(content = "") else m
                    }
                },
                onAudioReady  = { chunkId -> audioPlayer?.enqueue(chunkId) },
                onDone        = {
                    _isStreaming.value = false
                    syncHistLen = _messages.value.size
                },
                onError       = { err ->
                    _isStreaming.value = false
                    if (err == "401") _errorMessage.value = "Session expired — please log in again"
                    else _errorMessage.value = "Error: $err"
                },
            )
        }
    }

    fun abortStreaming() {
        viewModelScope.launch(Dispatchers.IO) {
            api.abortChat(baseUrl, token)
            audioPlayer?.stop()
            _isStreaming.value = false
        }
    }

    fun loadModels() {
        viewModelScope.launch(Dispatchers.IO) {
            val list = api.getModels(baseUrl, token)
            _models.value = list
            _currentModel.value = list.firstOrNull { it.current }?.label ?: ""
        }
    }

    fun selectModel(modelKey: String) {
        viewModelScope.launch(Dispatchers.IO) {
            api.setModel(baseUrl, token, modelKey)
            loadModels()
        }
    }

    fun toggleVoice(on: Boolean) {
        _voiceEnabled.value = on
        if (!on) audioPlayer?.stop()
    }

    fun clearConversation() {
        viewModelScope.launch(Dispatchers.IO) {
            api.clearConversation(baseUrl, token)
            _messages.value = emptyList()
            syncHistLen = 0
        }
    }

    fun clearError() { _errorMessage.value = null }

    override fun onCleared() {
        syncJob?.cancel()
        audioPlayer?.destroy()
    }
}
