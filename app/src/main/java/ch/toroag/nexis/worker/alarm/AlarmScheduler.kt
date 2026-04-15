package ch.toroag.nexis.worker.alarm

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.ZoneId

object AlarmScheduler {

    private const val PREFS          = "nexis_alarms"
    private const val KEY_LIST       = "alarm_list"
    private const val COUNTDOWN_CHAN = "nexis_alarm_countdown"

    /**
     * Schedule an alarm or timer.
     * @param nexisId   stable ID from the daemon
     * @param timeStr   "HH:MM" for alarms, "Xm"/"Xs"/"XhYm" for timers
     * @param label     shown in notifications
     * @param postDelay seconds after dismissal before running postAction
     * @param postAction prompt sent to NeXiS after delay (e.g. "//brief")
     * @param baseUrl   controller URL for TTS post action
     * @param token     Bearer token for TTS post action
     */
    fun schedule(
        context:    Context,
        nexisId:    String,
        timeStr:    String,
        label:      String,
        postDelay:  Int    = 0,
        postAction: String = "",
        baseUrl:    String = "",
        token:      String = "",
    ) {
        val triggerMs = parseTriggerMillis(timeStr) ?: return
        val isTimer   = timeStr.trim().any { it.isLetter() }
        val requestCode = nexisId.hashCode()

        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("nexis_id",    nexisId)
            putExtra("label",       label)
            putExtra("post_delay",  postDelay)
            putExtra("post_action", postAction)
            putExtra("base_url",    baseUrl)
            putExtra("token",       token)
        }
        val pi = PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.setAlarmClock(AlarmManager.AlarmClockInfo(triggerMs, pi), pi)

        // ── Countdown / upcoming notification ─────────────────────────────────
        ensureCountdownChannel(context)
        showCountdownNotification(context, nexisId, label, triggerMs, isTimer, requestCode, baseUrl, token, postDelay, postAction)

        // ── 30-minute warning ─────────────────────────────────────────────────
        val warnMs = triggerMs - 30 * 60 * 1000L
        if (warnMs > System.currentTimeMillis() + 60_000L) {
            val warnIntent = Intent(context, AlarmReceiver::class.java).apply {
                action = AlarmReceiver.ACTION_WARNING
                putExtra("nexis_id", nexisId)
                putExtra("label",    label)
            }
            val warnPi = PendingIntent.getBroadcast(
                context, requestCode + 5000, warnIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, warnMs, warnPi)
        }

        // Persist record for listing
        val record = JSONObject().apply {
            put("nexis_id",    nexisId)
            put("time_str",    timeStr)
            put("label",       label)
            put("trigger_ms",  triggerMs)
            put("post_delay",  postDelay)
            put("post_action", postAction)
            put("is_timer",    isTimer)
        }
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val arr = try { JSONArray(prefs.getString(KEY_LIST, "[]")) } catch (_: Exception) { JSONArray() }
        val filtered = JSONArray()
        for (i in 0 until arr.length()) {
            if (arr.getJSONObject(i).getString("nexis_id") != nexisId) filtered.put(arr.getJSONObject(i))
        }
        filtered.put(record)
        prefs.edit().putString(KEY_LIST, filtered.toString()).apply()
    }

    fun cancel(context: Context, nexisId: String) {
        val requestCode = nexisId.hashCode()

        // Cancel main alarm
        val intent = Intent(context, AlarmReceiver::class.java)
        val pi = PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )
        if (pi != null) {
            (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(pi)
            pi.cancel()
        }

        // Cancel warning alarm
        val warnPi = PendingIntent.getBroadcast(
            context, requestCode + 5000,
            Intent(context, AlarmReceiver::class.java).apply { action = AlarmReceiver.ACTION_WARNING },
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )
        if (warnPi != null) {
            (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(warnPi)
            warnPi.cancel()
        }

        // Cancel countdown notification
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .cancel(requestCode)

        // Remove from persisted list
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val arr = try { JSONArray(prefs.getString(KEY_LIST, "[]")) } catch (_: Exception) { JSONArray() }
        val out = JSONArray()
        for (i in 0 until arr.length()) {
            if (arr.getJSONObject(i).getString("nexis_id") != nexisId) out.put(arr.getJSONObject(i))
        }
        prefs.edit().putString(KEY_LIST, out.toString()).apply()
    }

    /** Remove fired / past alarms from the persisted list. */
    fun prunePast(context: Context) {
        val now = System.currentTimeMillis()
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val arr = try { JSONArray(prefs.getString(KEY_LIST, "[]")) } catch (_: Exception) { JSONArray() }
        val filtered = JSONArray()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            if (obj.getLong("trigger_ms") > now) filtered.put(obj)
        }
        prefs.edit().putString(KEY_LIST, filtered.toString()).apply()
    }

    /** Returns all currently-scheduled alarm records (future only). */
    fun getAll(context: Context): List<JSONObject> {
        val now   = System.currentTimeMillis()
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val arr   = try { JSONArray(prefs.getString(KEY_LIST, "[]")) } catch (_: Exception) { JSONArray() }
        return (0 until arr.length())
            .map { arr.getJSONObject(it) }
            .filter { it.getLong("trigger_ms") > now }
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    private fun showCountdownNotification(
        context:    Context,
        nexisId:    String,
        label:      String,
        triggerMs:  Long,
        isTimer:    Boolean,
        notifId:    Int,
        baseUrl:    String,
        token:      String,
        postDelay:  Int,
        postAction: String,
    ) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Cancel action — broadcasts ACTION_CANCEL_FROM_NOTIFICATION to AlarmReceiver
        val cancelIntent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_CANCEL_FROM_NOTIFICATION
            putExtra("nexis_id", nexisId)
        }
        val cancelPi = PendingIntent.getBroadcast(
            context, notifId + 1000, cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val title = if (isTimer) "Timer running" else "Alarm set"

        val builder = NotificationCompat.Builder(context, COUNTDOWN_CHAN)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(label)
            .setWhen(triggerMs)
            .setShowWhen(true)
            .setOngoing(true)
            .setAutoCancel(false)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancel", cancelPi)

        // Countdown chronometer (API 24+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            builder.setUsesChronometer(true)
            builder.setChronometerCountDown(true)
        }

        nm.notify(notifId, builder.build())
    }

    private fun ensureCountdownChannel(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(COUNTDOWN_CHAN) != null) return
        nm.createNotificationChannel(
            NotificationChannel(COUNTDOWN_CHAN, "NeXiS Timers & Upcoming Alarms",
                NotificationManager.IMPORTANCE_DEFAULT).apply {
                description          = "Shows running timers and upcoming alarm countdowns"
                setSound(null, null)
                enableVibration(false)
            }
        )
    }

    /** Parse "HH:MM" → next occurrence millis, or "Xm"/"Xs"/"XhYm" → now+X millis */
    fun parseTriggerMillis(timeStr: String): Long? {
        val s = timeStr.trim()
        val timerPattern = Regex("""^(?:(\d+)h)?(?:(\d+)m)?(?:(\d+)s)?$""")
        val tm = timerPattern.matchEntire(s)
        if (tm != null && s.isNotEmpty() && s.any { it.isLetter() }) {
            val h  = tm.groupValues[1].toIntOrNull() ?: 0
            val m  = tm.groupValues[2].toIntOrNull() ?: 0
            val ss = tm.groupValues[3].toIntOrNull() ?: 0
            val totalMs = ((h * 3600L) + (m * 60L) + ss) * 1000L
            if (totalMs > 0) return System.currentTimeMillis() + totalMs
        }
        val parts = s.split(":")
        if (parts.size >= 2) {
            val h = parts[0].toIntOrNull() ?: return null
            val m = parts[1].toIntOrNull() ?: return null
            val zone = ZoneId.systemDefault()
            var next = ZonedDateTime.now(zone).withHour(h).withMinute(m).withSecond(0).withNano(0)
            if (next.toInstant().toEpochMilli() <= System.currentTimeMillis() + 30_000) {
                next = next.plusDays(1)
            }
            return next.toInstant().toEpochMilli()
        }
        return null
    }
}
