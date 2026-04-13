package ch.toroag.nexis.worker.ui.voice

import android.app.Application
import android.speech.tts.TextToSpeech
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import ch.toroag.nexis.worker.data.NexisApiService
import ch.toroag.nexis.worker.data.PreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.util.Locale

enum class VoiceState { Idle, Listening, Thinking, Speaking }

class VoiceViewModel(app: Application) : AndroidViewModel(app), TextToSpeech.OnInitListener {

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

    private var baseUrl = ""
    private var token   = ""
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    init {
        tts = TextToSpeech(app, this)
        viewModelScope.launch {
            combine(prefs.baseUrl, prefs.token) { u, t -> u to t }.collect { (u, t) ->
                baseUrl = u; token = t
            }
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.US
            ttsReady = true
        }
    }

    /** Called by the UI with the transcribed text from SpeechRecognizer. */
    fun onTranscript(text: String) {
        if (text.isBlank()) { _state.value = VoiceState.Idle; return }
        _transcript.value = text
        _response.value   = ""
        _state.value      = VoiceState.Thinking
        viewModelScope.launch(Dispatchers.IO) {
            val sb = StringBuilder()
            api.streamChat(
                baseUrl  = baseUrl,
                token    = token,
                msg      = text,
                onToken  = { tok -> sb.append(tok); _response.value = sb.toString() },
                onAudioReady = {},
                onDone   = {
                    _state.value = VoiceState.Speaking
                    speak(sb.toString())
                },
                onError  = { err ->
                    _errorMessage.value = err
                    _state.value = VoiceState.Idle
                },
            )
        }
    }

    private fun speak(text: String) {
        if (!ttsReady || tts == null) { _state.value = VoiceState.Idle; return }
        // Strip markdown markers before speaking
        val clean = text
            .replace(Regex("```[\\s\\S]*?```"), "")
            .replace(Regex("[*_#`>]"), "")
            .trim()
        tts!!.speak(clean, TextToSpeech.QUEUE_FLUSH, null, "nx_resp")
        // Poll until done — TTS has no coroutine-friendly callback in all API levels
        viewModelScope.launch {
            kotlinx.coroutines.delay(300)
            while (tts?.isSpeaking == true) kotlinx.coroutines.delay(200)
            _state.value = VoiceState.Idle
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
        tts?.stop()
        _state.value = VoiceState.Idle
    }

    fun clearError() { _errorMessage.value = null }

    override fun onCleared() {
        tts?.stop()
        tts?.shutdown()
    }
}
