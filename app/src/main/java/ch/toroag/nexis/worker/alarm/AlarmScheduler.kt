package ch.toroag.nexis.worker.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.ZoneId

object AlarmScheduler {

    private const val PREFS = "nexis_alarms"
    private const val KEY_LIST = "alarm_list"

    /**
     * Schedule an alarm.
     * @param nexisId  stable ID string from the daemon (e.g. "nexis_1718000000")
     * @param timeStr  "HH:MM" (24h) for alarms, or "Xm" / "Xs" for timers
     * @param label    display label shown in the notification
     * @param postDelay seconds to wait after dismissal before running postAction (0 = no action)
     * @param postAction prompt to send to Nexis after delay, e.g. "//brief" (empty = none)
     * @param baseUrl   Nexis controller base URL (for post-action TTS)
     * @param token     Bearer token (for post-action TTS)
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

        // Persist record for listing
        val record = JSONObject().apply {
            put("nexis_id",    nexisId)
            put("time_str",    timeStr)
            put("label",       label)
            put("trigger_ms",  triggerMs)
            put("post_delay",  postDelay)
            put("post_action", postAction)
        }
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val arr = try { JSONArray(prefs.getString(KEY_LIST, "[]")) } catch (_: Exception) { JSONArray() }
        // Remove any existing entry with same ID first
        val filtered = JSONArray()
        for (i in 0 until arr.length()) {
            if (arr.getJSONObject(i).getString("nexis_id") != nexisId) filtered.put(arr.getJSONObject(i))
        }
        filtered.put(record)
        prefs.edit().putString(KEY_LIST, filtered.toString()).apply()
    }

    fun cancel(context: Context, nexisId: String) {
        val requestCode = nexisId.hashCode()
        val intent = Intent(context, AlarmReceiver::class.java)
        val pi = PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        ) ?: return
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pi)
        pi.cancel()

        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val arr = try { JSONArray(prefs.getString(KEY_LIST, "[]")) } catch (_: Exception) { JSONArray() }
        val filtered = JSONArray()
        for (i in 0 until arr.length()) {
            if (arr.getJSONObject(i).getString("nexis_id") != nexisId) filtered.put(arr.getJSONObject(i))
        }
        prefs.edit().putString(KEY_LIST, filtered.toString()).apply()
    }

    /** Remove fired/past alarms from the persisted list. */
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

    /** Parse "HH:MM" → next occurrence millis, or "Xm"/"Xs" → now+X millis */
    private fun parseTriggerMillis(timeStr: String): Long? {
        val s = timeStr.trim()
        // Relative timer: "25m", "90s", "1h30m"
        val timerPattern = Regex("""^(?:(\d+)h)?(?:(\d+)m)?(?:(\d+)s)?$""")
        val tm = timerPattern.matchEntire(s)
        if (tm != null && s.isNotEmpty() && s.any { it.isLetter() }) {
            val h  = tm.groupValues[1].toIntOrNull() ?: 0
            val m  = tm.groupValues[2].toIntOrNull() ?: 0
            val ss = tm.groupValues[3].toIntOrNull() ?: 0
            val totalMs = ((h * 3600L) + (m * 60L) + ss) * 1000L
            if (totalMs > 0) return System.currentTimeMillis() + totalMs
        }
        // Absolute time HH:MM
        val parts = s.split(":")
        if (parts.size >= 2) {
            val h = parts[0].toIntOrNull() ?: return null
            val m = parts[1].toIntOrNull() ?: return null
            val zone = ZoneId.systemDefault()
            var next = ZonedDateTime.now(zone)
                .withHour(h).withMinute(m).withSecond(0).withNano(0)
            if (next.toInstant().toEpochMilli() <= System.currentTimeMillis() + 30_000) {
                next = next.plusDays(1)   // already passed today, schedule for tomorrow
            }
            return next.toInstant().toEpochMilli()
        }
        return null
    }
}
