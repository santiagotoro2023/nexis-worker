package ch.toroag.nexis.worker.util

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import ai.picovoice.porcupine.Porcupine
import ai.picovoice.porcupine.PorcupineActivationException
import ai.picovoice.porcupine.PorcupineException
import ch.toroag.nexis.worker.MainActivity
import ch.toroag.nexis.worker.R
import ch.toroag.nexis.worker.data.PreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * Foreground service that listens for the "Hey Nexis" wake word using Porcupine.
 * Audio is processed entirely on-device — nothing is sent to any server.
 *
 * Setup:
 * 1. Get a free access key at https://console.picovoice.ai
 * 2. Create a custom "Hey Nexis" wake word at console.picovoice.ai/ppn
 *    (select Android as the platform)
 * 3. Download the .ppn file, rename it to "hey-nexis_android.ppn"
 * 4. Place it in app/src/main/assets/
 * 5. Enter your access key in the app Settings -> wake word
 */
class WakeWordService : Service() {

    companion object {
        const val ACTION_START = "ch.toroag.nexis.worker.WAKE_START"
        const val ACTION_STOP  = "ch.toroag.nexis.worker.WAKE_STOP"
        const val NOTIF_CHANNEL = "nexis_wake"
        const val NOTIF_ID      = 42
        const val WAKE_MODEL_ASSET = "hey-nexis_android.ppn"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var porcupine: Porcupine? = null
    private var audioRecorder: android.media.AudioRecord? = null
    private var listening = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopListening()
            stopSelf()
            return START_NOT_STICKY
        }
        startForeground(NOTIF_ID, buildNotification("listening for 'Hey Nexis'..."))
        scope.launch { startListening() }
        return START_STICKY
    }

    override fun onDestroy() {
        stopListening()
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun startListening() {
        val prefs = PreferencesRepository.get(applicationContext)
        val accessKey = prefs.wakeWordKey.first()

        if (accessKey.isBlank()) {
            updateNotification("wake word: enter access key in settings")
            return
        }

        // Check for custom model in assets
        val modelAssets = try {
            assets.list("")?.toList() ?: emptyList()
        } catch (e: Exception) { emptyList() }

        try {
            porcupine = if (WAKE_MODEL_ASSET in modelAssets) {
                // Use custom "Hey Nexis" model
                Porcupine.Builder()
                    .setAccessKey(accessKey)
                    .setKeywordPath("$filesDir/$WAKE_MODEL_ASSET")
                    .setSensitivity(0.5f)
                    .build(applicationContext)
            } else {
                // Fallback: use built-in "Porcupine" keyword as placeholder
                updateNotification("wake word: place hey-nexis_android.ppn in assets")
                Porcupine.Builder()
                    .setAccessKey(accessKey)
                    .setKeyword(Porcupine.BuiltInKeyword.PORCUPINE)
                    .setSensitivity(0.5f)
                    .build(applicationContext)
            }
        } catch (e: PorcupineActivationException) {
            updateNotification("wake word: invalid access key")
            return
        } catch (e: PorcupineException) {
            updateNotification("wake word: ${e.message?.take(60)}")
            return
        }

        val sampleRate  = porcupine!!.sampleRate
        val frameLength = porcupine!!.frameLength
        val minBuf      = android.media.AudioRecord.getMinBufferSize(
            sampleRate,
            android.media.AudioFormat.CHANNEL_IN_MONO,
            android.media.AudioFormat.ENCODING_PCM_16BIT,
        )

        audioRecorder = android.media.AudioRecord(
            android.media.MediaRecorder.AudioSource.VOICE_RECOGNITION,
            sampleRate,
            android.media.AudioFormat.CHANNEL_IN_MONO,
            android.media.AudioFormat.ENCODING_PCM_16BIT,
            maxOf(minBuf, frameLength * 2),
        )

        audioRecorder?.startRecording()
        listening = true
        val frame = ShortArray(frameLength)

        while (listening) {
            var read = 0
            while (read < frameLength && listening) {
                val r = audioRecorder?.read(frame, read, frameLength - read) ?: break
                if (r > 0) read += r
            }
            if (!listening) break
            try {
                val idx = porcupine?.process(frame) ?: -1
                if (idx >= 0) onWakeWord()
            } catch (_: Exception) { break }
        }
    }

    private fun onWakeWord() {
        SoundFx.micActivate()
        // Launch app and signal wake word detection
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            action  = "ch.toroag.nexis.worker.WAKE_WORD_DETECTED"
            flags   = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        startActivity(launchIntent)
    }

    private fun stopListening() {
        listening = false
        audioRecorder?.stop()
        audioRecorder?.release()
        audioRecorder = null
        porcupine?.delete()
        porcupine = null
    }

    private fun createNotificationChannel() {
        val ch = NotificationChannel(
            NOTIF_CHANNEL,
            "NeXiS Wake Word",
            NotificationManager.IMPORTANCE_LOW,
        ).apply { description = "Always-on wake word detection" }
        getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
    }

    private fun buildNotification(text: String): Notification {
        val tapIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, tapIntent, PendingIntent.FLAG_IMMUTABLE)
        val stopIntent = Intent(this, WakeWordService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPending = PendingIntent.getService(
            this, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE)

        return Notification.Builder(this, NOTIF_CHANNEL)
            .setContentTitle("NeXiS")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_nexis_logo)
            .setContentIntent(pendingIntent)
            .addAction(Notification.Action.Builder(null, "Stop", stopPending).build())
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIF_ID, buildNotification(text))
    }
}
