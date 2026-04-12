package ch.toroag.nexis.worker.ui.chat

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import ch.toroag.nexis.worker.data.NexisApiService
import ch.toroag.nexis.worker.data.PreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class ChatMessage(
    val role:    String,   // "user" | "assistant"
    val content: String,
    val id:      Long = System.currentTimeMillis(),
)

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

    private val _voiceEnabled   = MutableStateFlow(false)
    val voiceEnabled: StateFlow<Boolean> = _voiceEnabled

    private val _externalTyping = MutableStateFlow(false)
    val externalTyping: StateFlow<Boolean> = _externalTyping

    private var baseUrl   = ""
    private var token     = ""
    private var audioPlayer: AudioPlayer? = null
    private var syncJob:  Job? = null
    private var syncHistLen = -1

    init {
        viewModelScope.launch {
            combine(prefs.baseUrl, prefs.token) { u, t -> Pair(u, t) }.collect { (u, t) ->
                if (u.isNotEmpty() && t.isNotEmpty()) {
                    baseUrl = u; token = t
                    initAudioPlayer()
                    loadModels()
                    initHistory()
                }
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
        syncJob?.cancel()
        syncJob = viewModelScope.launch(Dispatchers.IO) {
            api.streamSync(
                baseUrl  = baseUrl,
                token    = token,
                onEvent  = { typing, histLen ->
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
                onClosed = {
                    _externalTyping.value = false
                    viewModelScope.launch {
                        delay(5000)
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

    fun sendMessage(text: String) {
        if (_isStreaming.value || text.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            _messages.value = _messages.value + ChatMessage("user", text)
            val assistantId = System.currentTimeMillis() + 1
            _messages.value = _messages.value + ChatMessage("assistant", "", assistantId)
            _isStreaming.value = true
            _errorMessage.value = null

            if (_voiceEnabled.value) api.enableVoice(baseUrl, token, true)

            api.streamChat(
                baseUrl      = baseUrl,
                token        = token,
                msg          = text,
                onToken      = { tok ->
                    _messages.value = _messages.value.map { m ->
                        if (m.id == assistantId) m.copy(content = m.content + tok) else m
                    }
                },
                onAudioReady = { chunkId -> audioPlayer?.enqueue(chunkId) },
                onDone       = {
                    _isStreaming.value = false
                    syncHistLen = _messages.value.size
                },
                onError      = { err ->
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
