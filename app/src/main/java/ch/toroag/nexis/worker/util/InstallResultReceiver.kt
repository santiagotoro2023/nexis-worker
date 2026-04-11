package ch.toroag.nexis.worker.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.util.Log

/**
 * Receives the result of a PackageInstaller silent install.
 * On success, Android automatically restarts the app with the new version.
 * On failure, we log the error; the old version keeps running.
 */
class InstallResultReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION = "ch.toroag.nexis.worker.INSTALL_RESULT"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val status  = intent.getIntExtra(PackageInstaller.EXTRA_STATUS,
                                          PackageInstaller.STATUS_FAILURE)
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: ""

        when (status) {
            PackageInstaller.STATUS_SUCCESS -> {
                // Android launches the newly installed app automatically.
                Log.i("NexisUpdate", "Install successful — restarting")
            }
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                // Should not happen when hasInstallPermission() is true,
                // but handle gracefully by forwarding the system prompt.
                val confirmIntent = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                confirmIntent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                confirmIntent?.let { context.startActivity(it) }
            }
            else -> {
                Log.e("NexisUpdate", "Install failed (status=$status): $message")
            }
        }
    }
}
