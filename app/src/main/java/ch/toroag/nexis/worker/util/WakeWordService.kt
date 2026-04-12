package ch.toroag.nexis.worker.util

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import ch.toroag.nexis.worker.MainActivity
import ch.toroag.nexis.worker.R
import ch.toroag.nexis.worker.data.NexisApiService
import ch.toroag.nexis.worker.data.PreferencesRepository
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.KeywordSpotter
import com.k2fsa.sherpa.onnx.KeywordSpotterConfig
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineStream
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL
import kotlin.coroutines.resume

/**
 * Always-on wake word detection using Sherpa-ONNX (fully on-device).
 * No API keys, no accounts — the model (~15 MB) downloads automatically on first use.
 *
 * When "Hey Nexis" / "Nexis" is detected:
 * - If the app is in the foreground: opens MainActivity (which starts STT there)
 * - If the app is in the background: captures voice via on-device STT, sends to
 *   the Nexis controller, and streams the response into the notification bar.
 */
class WakeWordService : Service() {

    companion object {
        const val ACTION_START  = "ch.toroag.nexis.worker.WAKE_START"
        const val ACTION_STOP   = "ch.toroag.nexis.worker.WAKE_STOP"
        const val NOTIF_CHANNEL = "nexis_wake"
        const val NOTIF_ID      = 42

        private const val SAMPLE_RATE  = 16000
        private const val FRAME_SIZE   = 512   // ~32 ms at 16 kHz
        private const val MODEL_DIR    = "sherpa-onnx-kws"

        private const val HF_BASE =
            "https://huggingface.co/csukuangfj/" +
            "sherpa-onnx-kws-zipformer-gigaspeech-3.3M-2024-01-01/resolve/main"

        private val MODEL_FILES = listOf(
            "encoder-epoch-12-avg-2-chunk-16-left-64.onnx",
            "decoder-epoch-12-avg-2-chunk-16-left-64.onnx",
            "joiner-epoch-12-avg-2-chunk-16-left-64.onnx",
            "tokens.txt",
        )

        // BPE tokens verified against the model's tokens.txt vocabulary.
        private val KEYWORDS_TXT =
            "▁HE Y ▁NE X IS @hey_nexis\n" +   // "hey nexis"
            "▁NE X IS @hey_nexis\n" +           // "nexis"
            "▁HE Y ▁NE X US @hey_nexis\n" +    // "hey nexus"
            "▁NE X US @hey_nexis\n"             // "nexus"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var kws: KeywordSpotter? = null
    private var stream: OnlineStream? = null
    private var recorder: AudioRecord? = null
    @Volatile private var listening = false
    @Volatile private var inConversation = false

    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopDetection(); stopSelf(); return START_NOT_STICKY
        }
        startForeground(NOTIF_ID, buildNotification("starting..."))
        scope.launch { runCatching { initialize() }.onFailure { e ->
            updateNotification("error: ${e.message?.take(80)}")
        }}
        return START_STICKY
    }

    override fun onDestroy() {
        stopDetection(); scope.cancel(); super.onDestroy()
    }

    // ── Setup ─────────────────────────────────────────────────────────────────

    private suspend fun initialize() {
        val modelDir = File(filesDir, MODEL_DIR)

        if (!allFilesPresent(modelDir)) {
            updateNotification("downloading model (~15 MB)...")
            try {
                downloadModels(modelDir)
            } catch (e: Exception) {
                updateNotification("download failed — check connection and retry")
                return
            }
        }

        File(modelDir, "keywords.txt").writeText(KEYWORDS_TXT)
        startDetection(modelDir)
    }

    private fun allFilesPresent(dir: File) =
        MODEL_FILES.all { File(dir, it).exists() }

    private suspend fun downloadModels(dir: File) = withContext(Dispatchers.IO) {
        dir.mkdirs()
        MODEL_FILES.forEachIndexed { i, name ->
            updateNotification("downloading model (${i + 1}/${MODEL_FILES.size})...")
            val dest = File(dir, name)
            if (!dest.exists()) {
                val tmp = File(dir, "$name.tmp")
                try {
                    URL("$HF_BASE/$name").openStream().use { src ->
                        tmp.outputStream().use { dst -> src.copyTo(dst) }
                    }
                    tmp.renameTo(dest)
                } finally {
                    if (tmp.exists()) tmp.delete()
                }
            }
        }
    }

    // ── Detection loop ────────────────────────────────────────────────────────

    private suspend fun startDetection(modelDir: File) {
        val config = KeywordSpotterConfig(
            featConfig = FeatureConfig(sampleRate = SAMPLE_RATE, featureDim = 80),
            modelConfig = OnlineModelConfig(
                transducer = OnlineTransducerModelConfig(
                    encoder = File(modelDir, MODEL_FILES[0]).absolutePath,
                    decoder = File(modelDir, MODEL_FILES[1]).absolutePath,
                    joiner  = File(modelDir, MODEL_FILES[2]).absolutePath,
                ),
                tokens     = File(modelDir, MODEL_FILES[3]).absolutePath,
                numThreads = 1,
                debug      = false,
                provider   = "cpu",
            ),
            keywordsFile       = File(modelDir, "keywords.txt").absolutePath,
            maxActivePaths     = 4,
            numTrailingBlanks  = 2,
            keywordsThreshold  = 0.15f,
            keywordsScore      = 2.0f,
        )

        try {
            kws    = KeywordSpotter(assetManager = null, config = config)
            stream = kws!!.createStream()
        } catch (e: Throwable) {
            updateNotification("model load failed: ${e.message?.take(60)}")
            return
        }

        val minBuf = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        recorder = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            maxOf(minBuf, FRAME_SIZE * 4),
        )

        if (recorder!!.state != AudioRecord.STATE_INITIALIZED) {
            updateNotification("microphone unavailable")
            recorder?.release(); recorder = null; return
        }

        recorder!!.startRecording()
        listening = true
        updateNotification("nexis is listening...")

        val shortBuf = ShortArray(FRAME_SIZE)
        val floatBuf = FloatArray(FRAME_SIZE)
        while (listening) {
            if (inConversation) {
                // Pause reading while in a headless conversation
                kotlinx.coroutines.delay(100)
                continue
            }
            val n = recorder?.read(shortBuf, 0, FRAME_SIZE) ?: break
            if (n <= 0) continue
            for (i in 0 until n) floatBuf[i] = shortBuf[i] / 32768.0f
            stream!!.acceptWaveform(floatBuf.copyOf(n), sampleRate = SAMPLE_RATE)
            kws!!.decode(stream!!)
            if (kws!!.getResult(stream!!).keyword.isNotEmpty()) {
                // Fresh stream for next detection
                stream?.release()
                stream = kws!!.createStream()
                onWakeWord()
            }
        }
    }

    // ── Wake word response ────────────────────────────────────────────────────

    private fun onWakeWord() {
        SoundFx.micActivate()

        // Check if we have credentials to do headless conversation
        scope.launch {
            val prefs = PreferencesRepository.get(applicationContext)
            val baseUrl = prefs.baseUrl.first()
            val token   = prefs.token.first()

            if (baseUrl.isNotEmpty() && token.isNotEmpty()) {
                // Headless mode: capture voice + send to Nexis without opening app
                runCatching { headlessConversation(baseUrl, token) }
                    .onFailure { updateNotification("nexis is listening...") }
            } else {
                // Not logged in — open app
                openApp()
            }
        }
    }

    private fun openApp() {
        startActivity(Intent(this, MainActivity::class.java).apply {
            action = "ch.toroag.nexis.worker.WAKE_WORD_DETECTED"
            flags  = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        })
    }

    // ── Headless conversation ─────────────────────────────────────────────────

    private suspend fun headlessConversation(baseUrl: String, token: String) {
        inConversation = true
        try {
            // Stop AudioRecord so SpeechRecognizer can use the mic
            recorder?.stop()

            updateNotification("listening...")
            val text = captureVoice()

            if (text.isNullOrBlank()) {
                updateNotification("nexis is listening...")
                return
            }

            updateNotification("you: $text")

            // Send to Nexis and stream response into notification
            val api = NexisApiService(PreferencesRepository.get(applicationContext), applicationContext)
            val response = StringBuilder()
            api.streamChat(
                baseUrl      = baseUrl,
                token        = token,
                msg          = text,
                onToken      = { tok ->
                    response.append(tok)
                    // Update notification with first ~100 chars of response
                    val preview = response.toString().take(120)
                    updateNotification("nexis: $preview")
                },
                onAudioReady = { /* audio plays on controller side only */ },
                onDone       = { updateNotification("nexis is listening...") },
                onError      = { err ->
                    if (err == "401") updateNotification("not connected — open app to log in")
                    else updateNotification("nexis is listening...")
                },
            )
        } finally {
            inConversation = false
            // Resume AudioRecord
            try { recorder?.startRecording() } catch (_: Exception) {}
        }
    }

    /** Capture a voice utterance using on-device SpeechRecognizer. Must be called from IO. */
    private suspend fun captureVoice(): String? = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { cont ->
            val sr = SpeechRecognizer.createSpeechRecognizer(applicationContext)
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            }

            sr.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: android.os.Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onPartialResults(partialResults: android.os.Bundle?) {
                    val partial = partialResults
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull() ?: return
                    if (partial.isNotBlank()) updateNotification("you: $partial...")
                }
                override fun onResults(results: android.os.Bundle?) {
                    val text = results
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                    sr.destroy()
                    if (!cont.isCompleted) cont.resume(text)
                }
                override fun onError(error: Int) {
                    sr.destroy()
                    if (!cont.isCompleted) cont.resume(null)
                }
                override fun onEvent(eventType: Int, params: android.os.Bundle?) {}
            })

            sr.startListening(intent)
            cont.invokeOnCancellation { runCatching { sr.destroy() } }
        }
    }

    private fun stopDetection() {
        listening = false
        recorder?.stop(); recorder?.release(); recorder = null
        stream?.release(); stream = null
        kws?.release(); kws = null
    }

    // ── Notification helpers ──────────────────────────────────────────────────

    private fun createNotificationChannel() {
        val ch = NotificationChannel(
            NOTIF_CHANNEL, "NeXiS Wake Word", NotificationManager.IMPORTANCE_LOW
        ).apply { description = "Always-on wake word detection" }
        getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
    }

    private fun buildNotification(text: String): Notification {
        val tap  = PendingIntent.getActivity(this, 0,
            Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 0,
            Intent(this, WakeWordService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, NOTIF_CHANNEL)
            .setContentTitle("NeXiS")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_nexis_logo)
            .setContentIntent(tap)
            .addAction(Notification.Action.Builder(null, "Stop", stop).build())
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) =
        getSystemService(NotificationManager::class.java).notify(NOTIF_ID, buildNotification(text))
}
