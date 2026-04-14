package ch.toroag.nexis.worker.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.view.KeyEvent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import ch.toroag.nexis.worker.data.NexisApiService

object CommandExecutor {
    private const val CHANNEL_ID = "nexis_commands"
    private var notifId = 1000

    fun execute(context: Context, cmd: NexisApiService.PendingCommand) {
        try {
            when (cmd.action.lowercase()) {
                "open_url" -> {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(cmd.arg))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                }
                "open_app" -> {
                    val launch = context.packageManager.getLaunchIntentForPackage(cmd.arg)
                        ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    if (launch != null) context.startActivity(launch)
                }
                "notify" -> showNotification(context, cmd.arg)
                "clip"   -> {
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("NeXiS", cmd.arg))
                }
                "media"  -> dispatchMediaKey(context, cmd.arg)
                "volume" -> setVolume(context, cmd.arg)
            }
        } catch (_: Exception) {}
    }

    private fun dispatchMediaKey(context: Context, action: String) {
        val keyCode = when (action.lowercase()) {
            "play", "play-pause", "toggle" -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
            "pause"                        -> KeyEvent.KEYCODE_MEDIA_PAUSE
            "next"                         -> KeyEvent.KEYCODE_MEDIA_NEXT
            "previous", "prev"             -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
            "stop"                         -> KeyEvent.KEYCODE_MEDIA_STOP
            else                           -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
        }
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP,   keyCode))
    }

    private fun setVolume(context: Context, arg: String) {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val pct = arg.filter { it.isDigit() }.toIntOrNull()?.coerceIn(0, 100) ?: return
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        am.setStreamVolume(AudioManager.STREAM_MUSIC, (pct * max / 100), 0)
    }

    private fun showNotification(context: Context, message: String) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "NeXiS Commands", NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("NeXiS")
            .setContentText(message)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(notifId++, notif)
        } catch (_: SecurityException) {}
    }
}
