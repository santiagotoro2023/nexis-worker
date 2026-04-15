package ch.toroag.nexis.desktop.ui.chat

import ch.toroag.nexis.desktop.data.NexisApiService
import ch.toroag.nexis.desktop.data.PreferencesRepository
import ch.toroag.nexis.desktop.util.DesktopCommandExecutor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.net.InetAddress

data class ChatMessage(
    val role:    String,
    val content: String,
    val id:      Long = System.currentTimeMillis(),
)

enum class ConnectionStatus { Connected, Connecting, Disconnected }

class ChatViewModel : AutoCloseable {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val prefs = PreferencesRepository.get()
    private val api   = NexisApiService(prefs)

    private val _messages         = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages

    private val _isStreaming      = MutableStateFlow(false)
    val isStreaming: StateFlow<Boolean> = _isStreaming

    private val _errorMessage     = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    private val _models           = MutableStateFlow<List<NexisApiService.ModelInfo>>(emptyList())
    val models: StateFlow<List<NexisApiService.ModelInfo>> = _models

    private val _currentModel     = MutableStateFlow("")
    val currentModel: StateFlow<String> = _currentModel

    private val _externalTyping   = MutableStateFlow(false)
    val externalTyping: StateFlow<Boolean> = _externalTyping

    private val _connectionStatus = MutableStateFlow(ConnectionStatus.Connecting)
    val connectionStatus: StateFlow<ConnectionStatus> = _connectionStatus

    private var baseUrl      = ""
    private var token        = ""
    private var syncJob:     Job? = null
    private var pollJob:     Job? = null
    private var syncHistLen  = -1
    private var syncRetries  = 0

    init {
        scope.launch {
            combine(prefs.baseUrl, prefs.token) { u, t -> Pair(u, t) }.collect { (u, t) ->
                if (u.isNotEmpty() && t.isNotEmpty()) {
                    baseUrl = u; token = t
                    loadModels()
                    initHistory()
                    registerThisDevice()
                    startCommandPolling()
                }
            }
        }
    }

    private fun registerThisDevice() {
        scope.launch {
            try {
                val deviceId = prefs.getOrCreateDeviceId()
                val hostname = InetAddress.getLocalHost().hostName
                val osName   = "${System.getProperty("os.name")} ${System.getProperty("os.version")}"
                val arch     = System.getProperty("os.arch") ?: ""
                val info = JSONObject().apply {
                    put("device_id",    deviceId)
                    put("hostname",     hostname)
                    put("model",        hostname)
                    put("os",           osName)
                    put("arch",         arch)
                    put("device_type",  "desktop")
                    put("capabilities", JSONArray(listOf("open_url", "notify", "clip", "media", "volume")))
                    put("ip",           runCatching { InetAddress.getLocalHost().hostAddress }.getOrDefault(""))
                }
                api.registerDevice(baseUrl, token, info)
            } catch (_: Exception) {}
        }
    }

    private fun startCommandPolling() {
        pollJob?.cancel()
        pollJob = scope.launch {
            while (isActive) {
                try {
                    val deviceId = prefs.getOrCreateDeviceId()
                    if (deviceId.isNotEmpty() && baseUrl.isNotEmpty() && token.isNotEmpty()) {
                        val cmds = api.pollCommands(baseUrl, token, deviceId)
                        if (cmds.isNotEmpty()) {
                            cmds.forEach { DesktopCommandExecutor.execute(it) }
                            api.ackCommands(baseUrl, token, cmds.map { it.id })
                        }
                    }
                } catch (_: Exception) {}
                delay(2_000)
            }
        }
    }

    private fun initHistory() {
        scope.launch {
            val hist = runCatching { api.getHistory(baseUrl, token) }.getOrDefault(emptyList())
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
        syncJob = scope.launch {
            api.streamSync(
                baseUrl  = baseUrl,
                token    = token,
                onEvent  = { typing, histLen ->
                    _connectionStatus.value = ConnectionStatus.Connected
                    syncRetries = 0
                    if (typing && !_isStreaming.value) {
                        _externalTyping.value = true
                    } else if (!typing) {
                        _externalTyping.value = false
                        if (!_isStreaming.value && syncHistLen >= 0 && histLen > syncHistLen) {
                            scope.launch {
                                val hist = runCatching { api.getHistory(baseUrl, token) }.getOrDefault(emptyList())
                                if (hist.isNotEmpty()) {
                                    _messages.value = hist.mapIndexed { i, m ->
                                        ChatMessage(m.role, m.content, i.toLong())
                                    }
                                }
                            }
                        }
                        syncHistLen = maxOf(syncHistLen, histLen)
                    }
                },
                onClosed = {
                    _connectionStatus.value = ConnectionStatus.Disconnected
                    _externalTyping.value = false
                    scope.launch {
                        val wait = minOf(5_000L * (1L shl minOf(syncRetries, 4)), 60_000L)
                        syncRetries++
                        delay(wait)
                        if (baseUrl.isNotEmpty() && token.isNotEmpty()) startSync()
                    }
                },
            )
        }
    }

    fun sendMessage(text: String) {
        if (_isStreaming.value || text.isBlank()) return
        scope.launch {
            _messages.value = _messages.value + ChatMessage("user", text)
            val assistantId = System.currentTimeMillis() + 1
            _messages.value = _messages.value + ChatMessage("assistant", "", assistantId)
            _isStreaming.value = true
            _errorMessage.value = null

            api.streamChat(
                baseUrl  = baseUrl,
                token    = token,
                msg      = text,
                onToken  = { tok ->
                    _messages.value = _messages.value.map { m ->
                        if (m.id == assistantId) m.copy(content = m.content + tok) else m
                    }
                },
                onClear  = {
                    _messages.value = _messages.value.map { m ->
                        if (m.id == assistantId) m.copy(content = "") else m
                    }
                },
                onAudioReady = { /* desktop doesn't play TTS audio */ },
                onDone   = {
                    _isStreaming.value = false
                    syncHistLen = _messages.value.size
                },
                onError  = { err ->
                    _isStreaming.value = false
                    _errorMessage.value = if (err == "401") "Session expired — please log in again"
                                         else "Error: $err"
                },
            )
        }
    }

    fun abortStreaming() {
        scope.launch {
            runCatching { api.abortChat(baseUrl, token) }
            _isStreaming.value = false
        }
    }

    fun loadModels() {
        scope.launch {
            val list = runCatching { api.getModels(baseUrl, token) }.getOrDefault(emptyList())
            _models.value = list
            _currentModel.value = list.firstOrNull { it.current }?.label ?: ""
        }
    }

    fun selectModel(modelKey: String) {
        scope.launch {
            runCatching { api.setModel(baseUrl, token, modelKey) }
            loadModels()
        }
    }

    fun clearConversation() {
        scope.launch {
            runCatching { api.clearConversation(baseUrl, token) }
            _messages.value = emptyList()
            syncHistLen = 0
        }
    }

    fun clearError() { _errorMessage.value = null }

    override fun close() {
        syncJob?.cancel()
        pollJob?.cancel()
    }
}
