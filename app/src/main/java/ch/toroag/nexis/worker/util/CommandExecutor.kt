package ch.toroag.nexis.worker.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
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
            }
        } catch (_: Exception) {}
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
