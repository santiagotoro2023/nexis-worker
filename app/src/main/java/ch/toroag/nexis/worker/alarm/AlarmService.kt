package ch.toroag.nexis.worker.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.MediaPlayer
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import ch.toroag.nexis.worker.MainActivity
import ch.toroag.nexis.worker.data.NexisApiService
import ch.toroag.nexis.worker.data.PreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.concurrent.LinkedBlockingQueue

class AlarmService : Service() {

    companion object {
        const val CHANNEL_ID    = "nexis_alarm"
        const val NOTIF_ID      = 9001
        const val ACTION_DISMISS = "ch.toroag.nexis.worker.ALARM_DISMISS"
        const val ACTION_SNOOZE  = "ch.toroag.nexis.worker.ALARM_SNOOZE"

        fun dismiss(context: Context) {
            context.startService(Intent(context, AlarmService::class.java).apply {
                action = ACTION_DISMISS
            })
        }
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var audioTrack: AudioTrack? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var vibrator: Vibrator? = null
    private val audioQueue = LinkedBlockingQueue<ByteArray>()
    private var dismissed = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel()

        // Acquire a wake lock so CPU stays on while playing
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "nexis:alarm_wake_lock",
        ).also { it.acquire(10 * 60 * 1000L) } // max 10 min
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_DISMISS) {
            dismiss(); return START_NOT_STICKY
        }
        if (intent?.action == ACTION_SNOOZE) {
            snooze(intent.getStringExtra("nexis_id") ?: ""); return START_NOT_STICKY
        }

        val label      = intent?.getStringExtra("label")      ?: "NeXiS Alarm"
        val nexisId    = intent?.getStringExtra("nexis_id")   ?: ""
        val postDelay  = intent?.getIntExtra("post_delay", 0) ?: 0
        val postAction = intent?.getStringExtra("post_action") ?: ""
        val baseUrl    = intent?.getStringExtra("base_url")   ?: ""
        val token      = intent?.getStringExtra("token")      ?: ""

        startForeground(NOTIF_ID, buildNotification(label, nexisId))

        startAlarmTone()
        startVibration()

        // Auto-dismiss after 60s if user doesn't interact
        scope.launch {
            delay(60_000)
            if (!dismissed) {
                dismiss()
                if (postAction.isNotBlank() && postDelay >= 0) {
                    runPostAction(postDelay, postAction, baseUrl, token)
                }
            }
        }

        return START_NOT_STICKY
    }

    private fun dismiss() {
        if (dismissed) return
        dismissed = true
        audioTrack?.runCatching { stop(); release() }
        audioTrack = null
        vibrator?.cancel()
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(NOTIF_ID)
        stopSelf()
    }

    private fun snooze(nexisId: String) {
        // Snooze 9 minutes
        if (nexisId.isNotEmpty()) {
            AlarmScheduler.schedule(
                context    = this,
                nexisId    = "${nexisId}_snooze",
                timeStr    = "9m",
                label      = "NeXiS Alarm (snoozed)",
                baseUrl    = "",
                token      = "",
            )
        }
        dismiss()
    }

    private fun startAlarmTone() {
        val tone        = AlarmToneGenerator.sequence
        val sampleRate  = AlarmToneGenerator.sampleRate
        val bufSizePcm  = AudioTrack.getMinBufferSize(
            sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT,
        ) * 4

        val at = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(bufSizePcm)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        audioTrack = at
        at.play()

        scope.launch(Dispatchers.IO) {
            // Ramp up volume over first 3 loops
            for (loop in 0 until 100) {
                if (dismissed) break
                val volume = (loop / 3f).coerceAtMost(1f)
                at.setVolume(volume)
                at.write(tone, 0, tone.size)
            }
        }
    }

    private fun startVibration() {
        val pattern = longArrayOf(0, 400, 200, 400, 200, 700, 350)
        vibrator = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
    }

    private fun runPostAction(postDelaySecs: Int, prompt: String, baseUrl: String, token: String) {
        if (baseUrl.isEmpty() || token.isEmpty()) return
        scope.launch {
            delay(postDelaySecs * 1000L)

            // Resolve credentials from prefs if not passed (fallback)
            val prefs = PreferencesRepository.get(this@AlarmService)
            val url   = baseUrl.ifEmpty  { runBlocking { prefs.baseUrl.first() } }
            val tok   = token.ifEmpty    { runBlocking { prefs.token.first()   } }
            if (url.isEmpty() || tok.isEmpty()) return@launch

            val api = NexisApiService(prefs, this@AlarmService)

            // Enable voice so we get audio chunks back
            runCatching { api.enableVoice(url, tok, true) }

            val audioPlayer = AlarmAudioPlayer(api, url, tok, cacheDir)
            api.streamChat(
                baseUrl      = url,
                token        = tok,
                msg          = prompt,
                onToken      = { /* text is secondary for this use case */ },
                onClear      = {},
                onAudioReady = { chunkId -> audioPlayer.enqueue(chunkId) },
                onDone       = {},
                onError      = {},
            )
        }
    }

    private fun buildNotification(label: String, nexisId: String): Notification {
        val dismissPi = PendingIntent.getService(
            this, 0,
            Intent(this, AlarmService::class.java).setAction(ACTION_DISMISS),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val snoozePi = PendingIntent.getService(
            this, 1,
            Intent(this, AlarmService::class.java).setAction(ACTION_SNOOZE)
                .putExtra("nexis_id", nexisId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val openPi = PendingIntent.getActivity(
            this, 2,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("NeXiS")
            .setContentText(label)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setFullScreenIntent(openPi, true)
            .setOngoing(true)
            .setAutoCancel(false)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Dismiss", dismissPi)
            .addAction(android.R.drawable.ic_popup_sync, "Snooze 9m", snoozePi)
            .setContentIntent(openPi)
            .build()
    }

    private fun ensureChannel() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        val ch = NotificationChannel(CHANNEL_ID, "NeXiS Alarms",
            NotificationManager.IMPORTANCE_HIGH).apply {
            description      = "NeXiS alarm notifications"
            setShowBadge(true)
            enableVibration(false)   // we handle vibration manually
            setSound(null, null)     // we handle audio manually
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        nm.createNotificationChannel(ch)
    }

    override fun onDestroy() {
        scope.cancel()
        wakeLock?.release()
        audioTrack?.runCatching { stop(); release() }
        vibrator?.cancel()
        super.onDestroy()
    }
}

/** Minimal audio player for post-alarm TTS chunks. */
private class AlarmAudioPlayer(
    private val api:      NexisApiService,
    private val baseUrl:  String,
    private val token:    String,
    private val cacheDir: File,
) {
    private val queue = LinkedBlockingQueue<Int>()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    init { scope.launch { drain() } }

    fun enqueue(id: Int) { queue.offer(id) }

    private suspend fun drain() {
        while (true) {
            val id  = queue.take()
            val wav = api.fetchAudioChunk(baseUrl, token, id) ?: continue
            playWav(wav, id)
        }
    }

    private suspend fun playWav(wav: ByteArray, id: Int) {
        val f = File(cacheDir, "nexis_alarm_tts_$id.wav")
        f.writeBytes(wav)
        kotlinx.coroutines.suspendCancellableCoroutine<Unit> { cont ->
            val mp = MediaPlayer()
            try {
                mp.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                mp.setDataSource(f.absolutePath)
                mp.prepare()
                mp.setOnCompletionListener { f.delete(); mp.release(); cont.resume(Unit) {} }
                mp.setOnErrorListener     { _, _, _ -> f.delete(); mp.release(); cont.resume(Unit) {}; true }
                mp.start()
            } catch (e: Exception) {
                f.delete(); runCatching { mp.release() }; cont.resume(Unit) {}
            }
        }
    }
}
