package ch.toroag.nexis.worker.ui.voice

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import ch.toroag.nexis.worker.data.NexisApiService
import ch.toroag.nexis.worker.data.PreferencesRepository
import ch.toroag.nexis.worker.ui.chat.AudioPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

enum class VoiceState { Idle, Listening, Thinking, Speaking }

class VoiceViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = PreferencesRepository.get(app)
    private val api   = NexisApiService(prefs, app)

    private val _state        = MutableStateFlow(VoiceState.Idle)
    private val _transcript   = MutableStateFlow("")   // last user utterance
    private val _response     = MutableStateFlow("")   // current assistant response
    private val _errorMessage = MutableStateFlow<String?>(null)

    val state:        StateFlow<VoiceState> = _state
    val transcript:   StateFlow<String>     = _transcript
    val response:     StateFlow<String>     = _response
    val errorMessage: StateFlow<String?>    = _errorMessage

    private var baseUrl     = ""
    private var token       = ""
    private var audioPlayer: AudioPlayer? = null

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

    /** Called by the UI with the transcribed text from SpeechRecognizer. */
    fun onTranscript(text: String) {
        if (text.isBlank()) { _state.value = VoiceState.Idle; return }
        _transcript.value = text
        _response.value   = ""
        _state.value      = VoiceState.Thinking
        viewModelScope.launch(Dispatchers.IO) {
            // Enable server-side Piper voice so we get AUDIOREADY chunks
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
                    // AudioPlayer plays async; state returns to Idle when queue drains
                    viewModelScope.launch {
                        kotlinx.coroutines.delay(300)
                        while (audioPlayer?.isPlaying() == true) kotlinx.coroutines.delay(200)
                        _state.value = VoiceState.Idle
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

    fun clearError() { _errorMessage.value = null }

    override fun onCleared() {
        audioPlayer?.destroy()
    }
}
