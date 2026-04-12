package ch.toroag.nexis.worker.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import ch.toroag.nexis.worker.data.PreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Restarts WakeWordService automatically after phone reboot if the setting is on. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        CoroutineScope(Dispatchers.IO).launch {
            val prefs = PreferencesRepository.get(context)
            if (prefs.wakeWordEnabled.first()) {
                context.startForegroundService(
                    Intent(context, WakeWordService::class.java).apply {
                        action = WakeWordService.ACTION_START
                    }
                )
            }
        }
    }
}
