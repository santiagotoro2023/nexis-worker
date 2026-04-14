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

    // Common app display name → package name. Covers the apps most people ask for by name.
    private val KNOWN_APPS = mapOf(
        "spotify"        to "com.spotify.music",
        "youtube"        to "com.google.android.youtube",
        "youtube music"  to "com.google.android.apps.youtube.music",
        "maps"           to "com.google.android.apps.maps",
        "google maps"    to "com.google.android.apps.maps",
        "chrome"         to "com.android.chrome",
        "instagram"      to "com.instagram.android",
        "whatsapp"       to "com.whatsapp",
        "telegram"       to "org.telegram.messenger",
        "netflix"        to "com.netflix.mediaclient",
        "settings"       to "com.android.settings",
        "gmail"          to "com.google.android.gm",
        "discord"        to "com.discord",
        "twitter"        to "com.twitter.android",
        "x"              to "com.twitter.android",
        "reddit"         to "com.reddit.frontpage",
        "snapchat"       to "com.snapchat.android",
        "tiktok"         to "com.zhiliaoapp.musically",
        "facebook"       to "com.facebook.katana",
        "messenger"      to "com.facebook.orca",
        "photos"         to "com.google.android.apps.photos",
        "calculator"     to "com.google.android.calculator",
        "clock"          to "com.google.android.deskclock",
        "play store"     to "com.android.vending",
        "google play"    to "com.android.vending",
        "drive"          to "com.google.android.apps.docs",
        "google drive"   to "com.google.android.apps.docs",
        "calendar"       to "com.google.android.calendar",
        "contacts"       to "com.google.android.contacts",
        "phone"          to "com.google.android.dialer",
        "messages"       to "com.google.android.apps.messaging",
        "keep"           to "com.google.android.keep",
        "amazon"         to "com.amazon.mShop.android.shopping",
        "prime video"    to "com.amazon.avod.thirdpartyclient",
        "disney+"        to "com.disney.disneyplus",
        "disney plus"    to "com.disney.disneyplus",
        "twitch"         to "tv.twitch.android.app",
        "vlc"            to "org.videolan.vlc",
        "zoom"           to "us.zoom.videomeetings",
        "teams"          to "com.microsoft.teams",
        "outlook"        to "com.microsoft.office.outlook",
        "onedrive"       to "com.microsoft.skydrive",
        "waze"           to "com.waze",
        "shazam"         to "com.shazam.android",
        "soundcloud"     to "com.soundcloud.android",
        "amazon music"   to "com.amazon.mp3",
        "deezer"         to "deezer.android.app",
        "tidal"          to "com.aspiro.tidal",
        "camera"         to "com.android.camera2",
        "files"          to "com.google.android.apps.nbu.files",
        "signal"         to "org.thoughtcrime.securesms",
        "uber"           to "com.ubercab",
        "lyft"           to "me.lyft.android",
        "airbnb"         to "com.airbnb.android",
        "google"         to "com.google.android.googlequicksearchbox",
        "assistant"      to "com.google.android.apps.googleassistant",
        "clock"          to "com.google.android.deskclock",
        "news"           to "com.google.android.apps.magazines",
        "fit"            to "com.google.android.apps.fitness",
        "google fit"     to "com.google.android.apps.fitness",
        "meet"           to "com.google.android.apps.tachyon",
        "google meet"    to "com.google.android.apps.tachyon",
        "duo"            to "com.google.android.apps.tachyon",
    )

    fun execute(context: Context, cmd: NexisApiService.PendingCommand) {
        try {
            when (cmd.action.lowercase()) {
                "open_url" -> {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(cmd.arg))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
                "open_app" -> launchApp(context, cmd.arg)
                "notify"   -> showNotification(context, cmd.arg)
                "clip"     -> {
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("NeXiS", cmd.arg))
                }
                "media"    -> dispatchMediaKey(context, cmd.arg)
                "volume"   -> setVolume(context, cmd.arg)
            }
        } catch (_: Exception) {}
    }

    private fun launchApp(context: Context, arg: String) {
        val pm = context.packageManager

        // 1. Try as exact package name (e.g. "com.spotify.music")
        pm.getLaunchIntentForPackage(arg)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ?.let { context.startActivity(it); return }

        // 2. Try known-name table (e.g. "spotify" → "com.spotify.music")
        val knownPkg = KNOWN_APPS[arg.lowercase().trim()]
        if (knownPkg != null) {
            pm.getLaunchIntentForPackage(knownPkg)
                ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                ?.let { context.startActivity(it); return }
        }

        // 3. Fuzzy search installed apps by display label
        val query = arg.lowercase().trim()
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val match = pm.queryIntentActivities(launcherIntent, 0)
            .firstOrNull { it.loadLabel(pm).toString().lowercase().contains(query) }
        match?.activityInfo?.packageName?.let { pkg ->
            pm.getLaunchIntentForPackage(pkg)
                ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                ?.let { context.startActivity(it); return }
        }
    }

    private fun dispatchMediaKey(context: Context, action: String) {
        val keyCode = when (action.lowercase()) {
            "play", "play-pause", "toggle" -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
            "pause"                        -> KeyEvent.KEYCODE_MEDIA_PAUSE
            "next"                         -> KeyEvent.KEYCODE_MEDIA_NEXT
            "previous", "prev"             -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
            "stop"                         -> KeyEvent.KEYCODE_MEDIA_STOP
            "seek_forward"                 -> KeyEvent.KEYCODE_MEDIA_FAST_FORWARD
            "seek_backward"                -> KeyEvent.KEYCODE_MEDIA_REWIND
            else                           -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
        }
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP,   keyCode))
    }

    private fun setVolume(context: Context, arg: String) {
        val am  = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
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
