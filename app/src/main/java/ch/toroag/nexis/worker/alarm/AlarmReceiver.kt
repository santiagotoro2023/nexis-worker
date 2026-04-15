package ch.toroag.nexis.worker.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Receives the AlarmManager broadcast and hands off to AlarmService. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val serviceIntent = Intent(context, AlarmService::class.java).apply {
            putExtras(intent)
        }
        context.startForegroundService(serviceIntent)
        // Clean up past entries from the persisted list
        AlarmScheduler.prunePast(context)
    }
}
