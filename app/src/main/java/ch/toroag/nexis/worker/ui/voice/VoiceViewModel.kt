package ch.toroag.nexis.worker.ui.voice

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import ch.toroag.nexis.worker.data.NexisApiService
import ch.toroag.nexis.worker.data.PreferencesRepository
import ch.toroag.nexis.worker.ui.chat.AudioPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

enum class VoiceState { Idle, Listening, Thinking, Speaking }

class VoiceViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = PreferencesRepository.get(app)
    private val api   = NexisApiService(prefs, app)

    private val _state        = MutableStateFlow(VoiceState.Idle)
    private val _transcript   = MutableStateFlow("")
    private val _response     = MutableStateFlow("")
    private val _errorMessage = MutableStateFlow<String?>(null)

    val state:        StateFlow<VoiceState> = _state
    val transcript:   StateFlow<String>     = _transcript
    val response:     StateFlow<String>     = _response
    val errorMessage: StateFlow<String?>    = _errorMessage

    private var baseUrl     = ""
    private var token       = ""
    private var audioPlayer: AudioPlayer? = null
    private var thinkingJob: Job?         = null

    init {
        viewModelScope.launch {
            combine(prefs.baseUrl, prefs.token) { u, t -> u to t }.collect { (u, t) ->
                baseUrl = u; token = t
                if (u.isNotEmpty() && t.isNotEmpty()) {
                    audioPlayer?.destroy()
                    audioPlayer = AudioPlayer(api, u, t, getApplication<Application>().cacheDir)
                }
            }
        }
    }

    fun onTranscript(text: String) {
        if (text.isBlank()) { _state.value = VoiceState.Idle; return }
        _transcript.value = text
        _response.value   = ""
        _state.value      = VoiceState.Thinking
        thinkingJob = viewModelScope.launch(Dispatchers.IO) {
            api.enableVoice(baseUrl, token, true)
            val sb = StringBuilder()
            api.streamChat(
                baseUrl  = baseUrl,
                token    = token,
                msg      = text,
                onToken  = { tok -> sb.append(tok); _response.value = sb.toString() },
                onAudioReady = { chunkId ->
                    _state.value = VoiceState.Speaking
                    audioPlayer?.enqueue(chunkId)
                },
                onDone   = {
                    viewModelScope.launch {
                        kotlinx.coroutines.delay(300)
                        while (audioPlayer?.isPlaying() == true) kotlinx.coroutines.delay(200)
                        if (_state.value == VoiceState.Speaking) _state.value = VoiceState.Idle
                    }
                },
                onError  = { err ->
                    _errorMessage.value = err
                    _state.value = VoiceState.Idle
                },
            )
        }
    }

    fun startListening() {
        if (_state.value != VoiceState.Idle) return
        _state.value = VoiceState.Listening
    }

    fun onListeningCancelled() {
        if (_state.value == VoiceState.Listening) _state.value = VoiceState.Idle
    }

    fun stopSpeaking() {
        audioPlayer?.stop()
        _state.value = VoiceState.Idle
    }

    /** Abort an in-progress Thinking or Speaking state and return to Idle. */
    fun abort() {
        thinkingJob?.cancel()
        thinkingJob = null
        audioPlayer?.stop()
        _state.value = VoiceState.Idle
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { api.abortChat(baseUrl, token) }
        }
    }

    fun clearError() { _errorMessage.value = null }

    override fun onCleared() {
        audioPlayer?.destroy()
    }
}
