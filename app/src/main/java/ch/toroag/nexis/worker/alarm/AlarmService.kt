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
        const val CHANNEL_ID     = "nexis_alarm"
        const val NOTIF_ID       = 9001
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
    private var wakeLock:   PowerManager.WakeLock? = null
    private var vibrator:   Vibrator? = null
    private var dismissed   = false

    // Post-action state — set in onStartCommand, used in dismiss()
    private var postDelaySecs = 0
    private var postActionStr = ""
    private var postBaseUrl   = ""
    private var postToken     = ""
    private var alarmLabel    = "NeXiS Alarm"
    private var alarmNexisId  = ""

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "nexis:alarm_wake_lock",
        ).also { it.acquire(10 * 60 * 1000L) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_DISMISS -> { dismiss(); return START_NOT_STICKY }
            ACTION_SNOOZE  -> { snoozeAlarm(intent.getStringExtra("nexis_id") ?: ""); return START_NOT_STICKY }
        }

        alarmLabel    = intent?.getStringExtra("label")        ?: "NeXiS Alarm"
        alarmNexisId  = intent?.getStringExtra("nexis_id")     ?: ""
        postDelaySecs = intent?.getIntExtra("post_delay", 0)   ?: 0
        postActionStr = intent?.getStringExtra("post_action")  ?: ""
        postBaseUrl   = intent?.getStringExtra("base_url")     ?: ""
        postToken     = intent?.getStringExtra("token")        ?: ""

        // Cancel the countdown notification that was shown when the alarm was set
        if (alarmNexisId.isNotEmpty()) {
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .cancel(alarmNexisId.hashCode())
        }

        startForeground(NOTIF_ID, buildNotification(alarmLabel, alarmNexisId))
        startAlarmTone()
        startVibration()

        // Auto-dismiss after 60 s if no interaction
        scope.launch {
            delay(60_000)
            if (!dismissed) dismiss()
        }

        return START_NOT_STICKY
    }

    private fun dismiss() {
        if (dismissed) return
        dismissed = true

        audioTrack?.runCatching { stop(); release() }
        audioTrack = null
        vibrator?.cancel()
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(NOTIF_ID)

        // Run post action on a fresh, service-independent scope so it survives stopSelf()
        if (postActionStr.isNotBlank()) {
            val freshScope  = CoroutineScope(Dispatchers.IO + SupervisorJob())
            val url         = postBaseUrl
            val tok         = postToken
            val delayMs     = postDelaySecs * 1000L
            val prompt      = postActionStr
            val appCtx      = applicationContext
            freshScope.launch {
                try {
                    delay(delayMs)
                    val prefs       = PreferencesRepository.get(appCtx)
                    val resolvedUrl = url.ifEmpty { prefs.baseUrl.first() }
                    val resolvedTok = tok.ifEmpty { prefs.token.first() }
                    if (resolvedUrl.isEmpty() || resolvedTok.isEmpty()) return@launch
                    val api = NexisApiService(prefs, appCtx)
                    runCatching { api.enableVoice(resolvedUrl, resolvedTok, true) }
                    val player = AlarmAudioPlayer(api, resolvedUrl, resolvedTok, appCtx.cacheDir)
                    api.streamChat(
                        baseUrl      = resolvedUrl,
                        token        = resolvedTok,
                        msg          = prompt,
                        onToken      = {},
                        onClear      = {},
                        onAudioReady = { id -> player.enqueue(id) },
                        onDone       = {},
                        onError      = {},
                    )
                } catch (_: Exception) {}
                freshScope.cancel()
            }
        }

        stopSelf()
    }

    private fun snoozeAlarm(nexisId: String) {
        // Snooze 9 min — does NOT run the post action
        if (nexisId.isNotEmpty()) {
            AlarmScheduler.schedule(
                context    = this,
                nexisId    = "${nexisId}_snooze",
                timeStr    = "9m",
                label      = "NeXiS Alarm (snoozed)",
                baseUrl    = postBaseUrl,
                token      = postToken,
                postDelay  = postDelaySecs,
                postAction = postActionStr,
            )
        }
        // Dismiss without triggering post action (it will trigger after the snoozed alarm)
        postActionStr = ""
        dismiss()
    }

    private fun startAlarmTone() {
        val tone       = AlarmToneGenerator.sequence
        val sampleRate = AlarmToneGenerator.sampleRate
        val bufSize    = AudioTrack.getMinBufferSize(
            sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT) * 4

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
            .setBufferSizeInBytes(bufSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        audioTrack = at
        at.play()

        scope.launch {
            for (loop in 0 until 100) {
                if (dismissed) break
                at.setVolume((loop / 3f).coerceAtMost(1f))
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

    private fun buildNotification(label: String, nexisId: String): Notification {
        val dismissPi = PendingIntent.getService(this, 0,
            Intent(this, AlarmService::class.java).setAction(ACTION_DISMISS),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val snoozePi = PendingIntent.getService(this, 1,
            Intent(this, AlarmService::class.java).setAction(ACTION_SNOOZE)
                .putExtra("nexis_id", nexisId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val openPi = PendingIntent.getActivity(this, 2,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

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
            enableVibration(false)
            setSound(null, null)
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

// ── Post-alarm TTS audio player ────────────────────────────────────────────────

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
