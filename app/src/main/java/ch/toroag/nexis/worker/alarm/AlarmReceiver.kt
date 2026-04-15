package ch.toroag.nexis.worker.alarm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat

/** Receives AlarmManager broadcasts for alarms, warnings, and notification actions. */
class AlarmReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_WARNING                  = "ch.toroag.nexis.worker.ALARM_WARNING"
        const val ACTION_CANCEL_FROM_NOTIFICATION = "ch.toroag.nexis.worker.ALARM_CANCEL_NOTIF"
        private const val WARN_CHAN               = "nexis_alarm_warn"
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_WARNING -> {
                val label   = intent.getStringExtra("label") ?: "NeXiS Alarm"
                showWarningNotification(context, label)
            }
            ACTION_CANCEL_FROM_NOTIFICATION -> {
                val nexisId = intent.getStringExtra("nexis_id") ?: return
                AlarmScheduler.cancel(context, nexisId)
            }
            else -> {
                // Normal alarm fire — start AlarmService
                context.startForegroundService(
                    Intent(context, AlarmService::class.java).apply { putExtras(intent) }
                )
                AlarmScheduler.prunePast(context)
            }
        }
    }

    private fun showWarningNotification(context: Context, label: String) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        ensureWarnChannel(context, nm)
        val notif = NotificationCompat.Builder(context, WARN_CHAN)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("NeXiS — alarm in 30 minutes")
            .setContentText(label)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        nm.notify(System.currentTimeMillis().toInt(), notif)
    }

    private fun ensureWarnChannel(context: Context, nm: NotificationManager) {
        if (nm.getNotificationChannel(WARN_CHAN) != null) return
        nm.createNotificationChannel(
            NotificationChannel(WARN_CHAN, "NeXiS Alarm Warnings",
                NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "30-minute upcoming alarm reminders"
            }
        )
    }
}
