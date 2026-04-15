package ch.toroag.nexis.desktop.ui.chat

import ch.toroag.nexis.desktop.data.NexisApiService
import ch.toroag.nexis.desktop.data.PreferencesRepository
import ch.toroag.nexis.desktop.util.DesktopCommandExecutor
import ch.toroag.nexis.desktop.util.SystemTrayManager
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
    val role:       String,
    val content:    String,
    val id:         Long    = System.currentTimeMillis(),
    val hasAttach:  Boolean = false,
    val attachName: String  = "",
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

    private val _voiceEnabled = MutableStateFlow(false)
    val voiceEnabled: StateFlow<Boolean> = _voiceEnabled

    private val _monitorAlert = MutableStateFlow<String?>(null)
    val monitorAlert: StateFlow<String?> = _monitorAlert

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
            api.streamSyncWithAlerts(
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
                onAlert  = { type, msg, _ ->
                    _monitorAlert.value = msg
                    SystemTrayManager.notify("NeXiS Monitor", msg, if (type == "disk") 1 else 0)
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

    fun sendMessage(
        text:         String,
        fileData:     String?  = null,
        fileMimeType: String?  = null,
        fileName:     String?  = null,
    ) {
        if (_isStreaming.value || (text.isBlank() && fileData == null)) return
        scope.launch {
            val hasAttach = fileData != null
            _messages.value = _messages.value + ChatMessage(
                "user", text, hasAttach = hasAttach,
                attachName = fileName ?: "")
            val assistantId = System.currentTimeMillis() + 1
            _messages.value = _messages.value + ChatMessage("assistant", "", assistantId)
            _isStreaming.value = true
            _errorMessage.value = null

            api.streamChat(
                baseUrl      = baseUrl,
                token        = token,
                msg          = text,
                fileData     = fileData,
                fileMimeType = fileMimeType,
                fileName     = fileName,
                onToken      = { tok ->
                    _messages.value = _messages.value.map { m ->
                        if (m.id == assistantId) m.copy(content = m.content + tok) else m
                    }
                },
                onClear      = {
                    _messages.value = _messages.value.map { m ->
                        if (m.id == assistantId) m.copy(content = "") else m
                    }
                },
                onAudioReady = { /* desktop TTS: audio plays on server, client gets narration inline */ },
                onDone       = {
                    _isStreaming.value = false
                    syncHistLen = _messages.value.size
                    // Notify tray if window is hidden
                    val lastAssistant = _messages.value.lastOrNull { it.role == "assistant" }
                    if (lastAssistant != null && lastAssistant.content.length > 10) {
                        SystemTrayManager.notify("NeXiS", lastAssistant.content.take(80))
                    }
                },
                onError      = { err ->
                    _isStreaming.value = false
                    _errorMessage.value = if (err == "401") "Session expired — please log in again"
                                         else "Error: $err"
                },
            )
        }
    }

    /** Transcribe [wavBytes] via the daemon's Whisper endpoint and send result as a chat message. */
    fun transcribeAndSend(wavBytes: ByteArray) {
        scope.launch {
            val text = runCatching { api.transcribeAudio(baseUrl, token, wavBytes) }.getOrDefault("")
            if (text.isNotBlank()) sendMessage(text)
        }
    }

    fun toggleVoice(on: Boolean) {
        _voiceEnabled.value = on
        scope.launch { runCatching { api.enableVoice(baseUrl, token, on) } }
    }

    fun dismissMonitorAlert() { _monitorAlert.value = null }

    /** Trigger a server-side screenshot and return it as a PendingAttachment-compatible triple. */
    fun takeScreenshotAndAttach(): Any? = runCatching {
        val raw = api.desktopAction(baseUrl, token, "screenshot")
        // The daemon returns the description, not the raw image — grab region instead
        // For proper image attach, use region action which returns base64
        null // the screenshot action currently returns a description; use /api/desktop region for images
    }.getOrNull()

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
