package ch.toroag.nexis.worker.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import ch.toroag.nexis.worker.MainActivity
import ch.toroag.nexis.worker.R
import ch.toroag.nexis.worker.data.NexisApiService
import ch.toroag.nexis.worker.data.PreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Foreground service that maintains an SSE sync connection to the NeXiS daemon.
 * Runs while the app is in background, shows a persistent notification, and
 * pops alerts for new messages and system monitor events.
 *
 * Battery-aware: reconnects with exponential back-off; does not poll aggressively.
 */
class NexisBackgroundService : Service() {

    companion object {
        private const val CH_PERSISTENT  = "nexis_bg"
        private const val CH_ALERTS      = "nexis_alerts"
        private const val NOTIF_ID_FG    = 1
        private const val NOTIF_ID_MSG   = 2
        private const val NOTIF_ID_ALERT = 3

        fun start(context: Context) {
            context.startForegroundService(Intent(context, NexisBackgroundService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, NexisBackgroundService::class.java))
        }
    }

    private val scope   = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var syncJob: Job? = null
    private var retries  = 0
    private var lastHistLen = -1

    private lateinit var nm:    NotificationManager
    private lateinit var prefs: PreferencesRepository
    private lateinit var api:   NexisApiService

    override fun onCreate() {
        super.onCreate()
        nm    = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        prefs = PreferencesRepository.get(this)
        api   = NexisApiService(prefs, this)
        createChannels()
        startForeground(NOTIF_ID_FG, buildFgNotification("Connected to NeXiS"))
        beginSync()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        syncJob?.cancel()
        super.onDestroy()
    }

    // ── Sync loop ──────────────────────────────────────────────────────────────

    private fun beginSync() {
        syncJob?.cancel()
        syncJob = scope.launch {
            val baseUrl = prefs.baseUrl.first()
            val token   = prefs.token.first()
            if (baseUrl.isEmpty() || token.isEmpty()) {
                // Not logged in — nothing to do; stop ourselves
                stopSelf()
                return@launch
            }

            updateFgNotif("Connected to NeXiS")

            api.streamSyncWithAlerts(
                baseUrl  = baseUrl,
                token    = token,
                onEvent  = { typing, histLen ->
                    retries = 0
                    if (!typing && histLen > lastHistLen && lastHistLen >= 0) {
                        // New message arrived while app was in background
                        showMessageNotification()
                    }
                    lastHistLen = maxOf(lastHistLen, histLen)
                },
                onAlert  = { _, msg, _ ->
                    showAlertNotification(msg)
                },
                onClosed = {
                    updateFgNotif("Reconnecting…")
                    scope.launch {
                        val wait = minOf(5_000L * (1L shl minOf(retries, 5)), 120_000L)
                        retries++
                        delay(wait)
                        beginSync()
                    }
                },
            )
        }
    }

    // ── Notifications ──────────────────────────────────────────────────────────

    private fun buildFgNotification(text: String): Notification {
        val tapIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CH_PERSISTENT)
            .setContentTitle("NeXiS")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_nexis_logo)
            .setContentIntent(tapIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun updateFgNotif(text: String) {
        nm.notify(NOTIF_ID_FG, buildFgNotification(text))
    }

    private fun showMessageNotification() {
        val tapIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(this, CH_ALERTS)
            .setContentTitle("NeXiS replied")
            .setContentText("Tap to view the response")
            .setSmallIcon(R.drawable.ic_nexis_logo)
            .setContentIntent(tapIntent)
            .setAutoCancel(true)
            .build()
        nm.notify(NOTIF_ID_MSG, n)
    }

    private fun showAlertNotification(msg: String) {
        val tapIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(this, CH_ALERTS)
            .setContentTitle("NeXiS Monitor")
            .setContentText(msg)
            .setSmallIcon(R.drawable.ic_nexis_logo)
            .setContentIntent(tapIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        nm.notify(NOTIF_ID_ALERT, n)
    }

    private fun createChannels() {
        nm.createNotificationChannel(
            NotificationChannel(CH_PERSISTENT, "NeXiS background sync",
                NotificationManager.IMPORTANCE_MIN).apply {
                description = "Persistent notification for background NeXiS sync"
                setShowBadge(false)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_ALERTS, "NeXiS alerts",
                NotificationManager.IMPORTANCE_HIGH).apply {
                description = "New message and system monitor alerts"
            }
        )
    }
}
