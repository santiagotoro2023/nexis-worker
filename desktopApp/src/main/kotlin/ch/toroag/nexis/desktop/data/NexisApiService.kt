package ch.toroag.nexis.desktop.data

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.security.SecureRandom
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext

/** Desktop version of NexisApiService — no Android Context, cert pin via file. */
class NexisApiService(
    private val prefs: PreferencesRepository,
    onCertPinned: ((fingerprint: String) -> Unit)? = null,
) {
    private val trustManager = TofuTrustManager(onCertPinned)

    private fun buildClient(readTimeoutSec: Long): OkHttpClient {
        val sslCtx = SSLContext.getInstance("TLS")
        sslCtx.init(null, arrayOf(trustManager), SecureRandom())
        return OkHttpClient.Builder()
            .sslSocketFactory(sslCtx.socketFactory, trustManager)
            .hostnameVerifier { _, _ -> true }
            .connectTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .readTimeout(readTimeoutSec, TimeUnit.SECONDS)
            .build()
    }

    private val streamClient   = buildClient(300)
    private val standardClient = buildClient(30)

    private fun Request.Builder.withBearer(token: String) =
        addHeader("Authorization", "Bearer $token")

    // ── Auth ──────────────────────────────────────────────────────────────────

    fun getToken(baseUrl: String, password: String): String {
        val body = JSONObject().put("password", password).toString()
            .toRequestBody("application/json".toMediaType())
        val req = Request.Builder().url("$baseUrl/api/token").post(body).build()
        standardClient.newCall(req).execute().use { resp ->
            val text = resp.body?.string() ?: throw Exception("empty response")
            if (!resp.isSuccessful) throw Exception("invalid password")
            return JSONObject(text).getString("token")
        }
    }

    // ── Chat / SSE ────────────────────────────────────────────────────────────

    fun streamChat(
        baseUrl:      String,
        token:        String,
        msg:          String,
        fileData:     String?     = null,
        fileMimeType: String?     = null,
        fileName:     String?     = null,
        onToken:      (String) -> Unit,
        onClear:      () -> Unit  = {},
        onAudioReady: (Int) -> Unit = {},
        onDone:       () -> Unit,
        onError:      (String) -> Unit,
    ) {
        val bodyObj = JSONObject().put("msg", msg)
        if (fileData     != null) bodyObj.put("file_data", fileData)
        if (fileMimeType != null) bodyObj.put("file_type", fileMimeType)
        if (fileName     != null) bodyObj.put("file_name", fileName)
        val req = Request.Builder()
            .url("$baseUrl/api/chat")
            .post(bodyObj.toString().toRequestBody("application/json".toMediaType()))
            .withBearer(token)
            .build()
        try {
            streamClient.newCall(req).execute().use { resp ->
                if (resp.code == 401) { onError("401"); return }
                if (!resp.isSuccessful) { onError("HTTP ${resp.code}"); return }
                val reader = BufferedReader(InputStreamReader(resp.body!!.byteStream()))
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val l = line!!; if (!l.startsWith("data: ")) continue
                    val data = l.removePrefix("data: ")
                    when {
                        data == "[DONE]" -> { onDone(); return }
                        data.startsWith("[AUDIOREADY:") -> {
                            val id = data.removeSurrounding("[AUDIOREADY:", "]").toIntOrNull()
                            if (id != null) onAudioReady(id)
                        }
                        data == "[CLEAR]" -> onClear()
                        data.startsWith("[STATUS:")  -> {}
                        else -> onToken(data.replace('\u0000', '\n'))
                    }
                }
                onDone()
            }
        } catch (e: Exception) { onError(e.message ?: "stream error") }
    }

    fun abortChat(baseUrl: String, token: String) {
        val req = Request.Builder()
            .url("$baseUrl/api/chat/abort")
            .post("{}".toRequestBody("application/json".toMediaType()))
            .withBearer(token).build()
        runCatching { standardClient.newCall(req).execute().close() }
    }

    // ── Models ────────────────────────────────────────────────────────────────

    data class ModelInfo(val key: String, val label: String, val desc: String,
                         val installed: Boolean, val current: Boolean)

    fun getModels(baseUrl: String, token: String): List<ModelInfo> {
        val req = Request.Builder().url("$baseUrl/api/models").withBearer(token).get().build()
        return try {
            standardClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return emptyList()
                val arr = JSONObject(resp.body!!.string()).getJSONArray("models")
                (0 until arr.length()).map {
                    val o = arr.getJSONObject(it)
                    ModelInfo(o.getString("key"), o.getString("label"), o.getString("desc"),
                              o.optBoolean("installed", true), o.optBoolean("current", false))
                }
            }
        } catch (e: Exception) { emptyList() }
    }

    fun setModel(baseUrl: String, token: String, modelKey: String) {
        val body = JSONObject().put("model", modelKey).toString()
            .toRequestBody("application/json".toMediaType())
        val req = Request.Builder().url("$baseUrl/api/model").post(body).withBearer(token).build()
        runCatching { standardClient.newCall(req).execute().close() }
    }

    // ── Voice ─────────────────────────────────────────────────────────────────

    fun enableVoice(baseUrl: String, token: String, on: Boolean) {
        val body = JSONObject().put("on", on).toString()
            .toRequestBody("application/json".toMediaType())
        val req = Request.Builder().url("$baseUrl/api/voice").post(body).withBearer(token).build()
        runCatching { standardClient.newCall(req).execute().close() }
    }

    // ── History ───────────────────────────────────────────────────────────────

    data class HistoryMessage(val role: String, val content: String)

    fun getHistory(baseUrl: String, token: String): List<HistoryMessage> {
        val req = Request.Builder().url("$baseUrl/api/history").withBearer(token).get().build()
        return try {
            standardClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return emptyList()
                val arr = JSONObject(resp.body!!.string()).getJSONArray("history")
                (0 until arr.length()).map {
                    val o = arr.getJSONObject(it)
                    HistoryMessage(o.getString("role"), o.getString("content"))
                }
            }
        } catch (e: Exception) { emptyList() }
    }

    fun streamSync(baseUrl: String, token: String,
                   onEvent: (typing: Boolean, histLen: Int) -> Unit,
                   onClosed: () -> Unit) {
        val req = Request.Builder().url("$baseUrl/api/sync")
            .withBearer(token).addHeader("Accept", "text/event-stream").get().build()
        try {
            streamClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) { onClosed(); return }
                val reader = BufferedReader(InputStreamReader(resp.body!!.byteStream()))
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val l = line!!; if (!l.startsWith("data: ")) continue
                    try {
                        val obj = JSONObject(l.removePrefix("data: "))
                        onEvent(obj.optBoolean("typing", false), obj.optInt("hist_len", 0))
                    } catch (_: Exception) {}
                }
            }
        } catch (_: Exception) {}
        onClosed()
    }

    // ── Health ────────────────────────────────────────────────────────────────

    data class HealthInfo(val model: String, val modelLabel: String, val voice: Boolean,
                          val voiceModel: String, val memories: Int, val sessions: Int,
                          val histLen: Int, val uptimeSeconds: Int)

    fun getHealth(baseUrl: String, token: String): HealthInfo? {
        val req = Request.Builder().url("$baseUrl/api/health").withBearer(token).get().build()
        return try {
            standardClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val o = JSONObject(resp.body!!.string())
                HealthInfo(o.getString("model"), o.getString("model_label"),
                           o.getBoolean("voice"), o.getString("voice_model"),
                           o.getInt("memories"), o.getInt("sessions"),
                           o.getInt("hist_len"), o.getInt("uptime"))
            }
        } catch (e: Exception) { null }
    }

    // ── Memories ──────────────────────────────────────────────────────────────

    data class MemoryEntry(val id: Int, val content: String, val createdAt: String)

    fun getMemories(baseUrl: String, token: String): List<MemoryEntry> {
        val req = Request.Builder().url("$baseUrl/api/memories").withBearer(token).get().build()
        return try {
            standardClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return emptyList()
                val arr = JSONObject(resp.body!!.string()).getJSONArray("memories")
                (0 until arr.length()).map {
                    val o = arr.getJSONObject(it)
                    MemoryEntry(o.getInt("id"), o.getString("content"), o.optString("created_at"))
                }
            }
        } catch (e: Exception) { emptyList() }
    }

    fun deleteMemory(baseUrl: String, token: String, id: Int) {
        val body = JSONObject().put("action", "delete").put("id", id).toString()
            .toRequestBody("application/json".toMediaType())
        val req = Request.Builder().url("$baseUrl/api/memories").post(body).withBearer(token).build()
        runCatching { standardClient.newCall(req).execute().close() }
    }

    // ── History sessions ──────────────────────────────────────────────────────

    data class SessionMessage(val role: String, val content: String)
    data class SessionSummary(val sessionId: String, val started: String,
                              val source: String, val title: String,
                              val preview: List<SessionMessage>)

    fun getHistorySessions(baseUrl: String, token: String): List<SessionSummary> {
        val req = Request.Builder().url("$baseUrl/api/history/sessions").withBearer(token).get().build()
        return try {
            standardClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return emptyList()
                val arr = JSONObject(resp.body!!.string()).getJSONArray("sessions")
                (0 until arr.length()).map {
                    val o = arr.getJSONObject(it)
                    val prev = o.getJSONArray("preview")
                    SessionSummary(o.getString("session_id"), o.getString("started"),
                        o.optString("source", "web"), o.optString("title", ""),
                        (0 until prev.length()).map { i ->
                            val m = prev.getJSONObject(i)
                            SessionMessage(m.getString("role"), m.getString("content"))
                        })
                }
            }
        } catch (e: Exception) { emptyList() }
    }

    fun loadHistorySession(baseUrl: String, token: String, sessionId: String) {
        val body = JSONObject().put("session_id", sessionId).toString()
            .toRequestBody("application/json".toMediaType())
        val req = Request.Builder().url("$baseUrl/api/history/load").post(body).withBearer(token).build()
        standardClient.newCall(req).execute().close()
    }

    fun deleteHistorySession(baseUrl: String, token: String, sessionId: String) {
        val body = JSONObject().put("action", "delete").put("session_id", sessionId).toString()
            .toRequestBody("application/json".toMediaType())
        val req = Request.Builder().url("$baseUrl/api/history/sessions").post(body).withBearer(token).build()
        runCatching { standardClient.newCall(req).execute().close() }
    }

    // ── Desktop control ───────────────────────────────────────────────────────

    fun desktopAction(baseUrl: String, token: String, action: String,
                      arg: String = "", deviceId: String = ""): String {
        val obj = JSONObject().put("action", action).put("arg", arg)
        if (deviceId.isNotEmpty()) obj.put("device_id", deviceId)
        val body = obj.toString().toRequestBody("application/json".toMediaType())
        val req  = Request.Builder().url("$baseUrl/api/desktop").post(body).withBearer(token).build()
        val client = if (action == "screenshot") streamClient else standardClient
        return try {
            client.newCall(req).execute().use { resp ->
                val text = resp.body?.string() ?: return "(no response)"
                if (!resp.isSuccessful) return "(error ${resp.code})"
                JSONObject(text).optString("result", "(no result)")
            }
        } catch (e: Exception) { "(error: ${e.message})" }
    }

    // ── Devices ───────────────────────────────────────────────────────────────

    data class DeviceInfo(val deviceId: String, val hostname: String, val model: String,
                          val os: String, val arch: String, val deviceType: String,
                          val capabilities: List<String>, val ip: String, val mac: String,
                          val role: String?, val online: Boolean, val batteryPct: Int?,
                          val charging: Boolean?, val lastSeen: String)

    data class PendingCommand(val id: Int, val action: String, val arg: String)

    fun getDevices(baseUrl: String, token: String): List<DeviceInfo> {
        val req = Request.Builder().url("$baseUrl/api/devices").withBearer(token).get().build()
        return try {
            standardClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return emptyList()
                val arr = JSONObject(resp.body!!.string()).getJSONArray("devices")
                (0 until arr.length()).map {
                    val o = arr.getJSONObject(it)
                    val caps = o.optJSONArray("capabilities")
                    DeviceInfo(o.getString("device_id"), o.optString("hostname", ""),
                        o.optString("model", ""), o.optString("os", ""), o.optString("arch", ""),
                        o.optString("device_type", "desktop"),
                        if (caps != null) (0 until caps.length()).map { i -> caps.getString(i) } else emptyList(),
                        o.optString("ip", ""), o.optString("mac", ""),
                        o.optString("role").takeIf { r -> r.isNotEmpty() && r != "null" },
                        o.optBoolean("online", false),
                        if (o.isNull("battery_pct")) null else o.optInt("battery_pct"),
                        if (o.isNull("charging")) null else o.optBoolean("charging"),
                        o.optString("last_seen", ""))
                }
            }
        } catch (e: Exception) { emptyList() }
    }

    fun registerDevice(baseUrl: String, token: String, info: JSONObject): Boolean {
        val body = info.toString().toRequestBody("application/json".toMediaType())
        val req  = Request.Builder().url("$baseUrl/api/device/register").post(body).withBearer(token).build()
        return try { standardClient.newCall(req).execute().use { it.isSuccessful } } catch (e: Exception) { false }
    }

    fun setDeviceRole(baseUrl: String, token: String, deviceId: String, role: String) {
        val body = JSONObject().put("device_id", deviceId).put("role", role).toString()
            .toRequestBody("application/json".toMediaType())
        val req = Request.Builder().url("$baseUrl/api/device/role").post(body).withBearer(token).build()
        runCatching { standardClient.newCall(req).execute().close() }
    }

    fun deleteDevice(baseUrl: String, token: String, deviceId: String) {
        val body = JSONObject().put("device_id", deviceId).toString()
            .toRequestBody("application/json".toMediaType())
        val req = Request.Builder().url("$baseUrl/api/device/delete").post(body).withBearer(token).build()
        runCatching { standardClient.newCall(req).execute().close() }
    }

    fun pollCommands(baseUrl: String, token: String, deviceId: String): List<PendingCommand> {
        val req = Request.Builder()
            .url("$baseUrl/api/commands/pending?device_id=$deviceId")
            .withBearer(token).get().build()
        return try {
            standardClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return emptyList()
                val arr = JSONObject(resp.body!!.string()).getJSONArray("commands")
                (0 until arr.length()).map { i ->
                    val o = arr.getJSONObject(i)
                    PendingCommand(o.getInt("id"), o.getString("action"), o.optString("arg", ""))
                }
            }
        } catch (e: Exception) { emptyList() }
    }

    fun ackCommands(baseUrl: String, token: String, ids: List<Int>) {
        if (ids.isEmpty()) return
        val body = JSONObject().put("ids", org.json.JSONArray(ids)).toString()
            .toRequestBody("application/json".toMediaType())
        val req = Request.Builder().url("$baseUrl/api/commands/ack").post(body).withBearer(token).build()
        runCatching { standardClient.newCall(req).execute().close() }
    }

    fun wakeOnLan(baseUrl: String, token: String, mac: String): String {
        val body = JSONObject().put("mac", mac).toString()
            .toRequestBody("application/json".toMediaType())
        val req = Request.Builder().url("$baseUrl/api/wol").post(body).withBearer(token).build()
        return try {
            standardClient.newCall(req).execute().use { resp ->
                val text = resp.body?.string() ?: return "(no response)"
                if (!resp.isSuccessful) return "(error ${resp.code})"
                JSONObject(text).optString("result", "(no result)")
            }
        } catch (e: Exception) { "(error: ${e.message})" }
    }

    fun sendDeviceCommand(baseUrl: String, token: String, deviceId: String,
                          action: String, arg: String = ""): String {
        val body = JSONObject().put("device_id", deviceId).put("action", action).put("arg", arg)
            .toString().toRequestBody("application/json".toMediaType())
        val req = Request.Builder().url("$baseUrl/api/device/command").post(body).withBearer(token).build()
        return try {
            standardClient.newCall(req).execute().use { resp ->
                val text = resp.body?.string() ?: return "(no response)"
                if (!resp.isSuccessful) return "(error ${resp.code})"
                val obj = JSONObject(text)
                if (obj.optBoolean("ok")) "queued: ${obj.optString("queued", action)}" else "(failed)"
            }
        } catch (e: Exception) { "(error: ${e.message})" }
    }

    fun probeController(baseUrl: String, token: String): String {
        val req = Request.Builder().url("$baseUrl/api/probe").withBearer(token).get().build()
        return try {
            standardClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return "(probe failed: ${resp.code})"
                JSONObject(resp.body!!.string()).optString("probe", "(no output)")
            }
        } catch (e: Exception) { "(probe error: ${e.message})" }
    }

    fun probeDevice(baseUrl: String, token: String, deviceId: String): String {
        val req = Request.Builder().url("$baseUrl/api/probe/device?device_id=$deviceId")
            .withBearer(token).get().build()
        return try {
            standardClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return "(probe failed: ${resp.code})"
                JSONObject(resp.body!!.string()).optString("probe", "(no output)")
            }
        } catch (e: Exception) { "(probe error: ${e.message})" }
    }

    // ── Schedules ─────────────────────────────────────────────────────────────

    data class ScheduleEntry(val id: Int, val name: String, val expr: String,
                             val prompt: String, val active: Boolean, val lastRun: String?)

    fun getSchedules(baseUrl: String, token: String): List<ScheduleEntry> {
        val req = Request.Builder().url("$baseUrl/api/schedules").withBearer(token).get().build()
        return try {
            standardClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return emptyList()
                val arr = JSONObject(resp.body!!.string()).getJSONArray("schedules")
                (0 until arr.length()).map {
                    val o = arr.getJSONObject(it)
                    ScheduleEntry(o.getInt("id"), o.optString("name", ""), o.optString("expr", ""),
                                  o.optString("prompt", ""), o.optBoolean("active", true),
                                  if (o.isNull("last_run")) null else o.optString("last_run"))
                }
            }
        } catch (e: Exception) { emptyList() }
    }

    fun scheduleAction(baseUrl: String, token: String, action: String, id: Int? = null,
                       name: String? = null, expr: String? = null, prompt: String? = null,
                       active: Boolean? = null) {
        val obj = JSONObject().put("action", action)
        if (id     != null) obj.put("id", id)
        if (name   != null) obj.put("name", name)
        if (expr   != null) obj.put("expr", expr)
        if (prompt != null) obj.put("prompt", prompt)
        if (active != null) obj.put("active", active)
        val body = obj.toString().toRequestBody("application/json".toMediaType())
        val req  = Request.Builder().url("$baseUrl/api/schedules").post(body).withBearer(token).build()
        runCatching { standardClient.newCall(req).execute().close() }
    }

    fun clearConversation(baseUrl: String, token: String) {
        val req = Request.Builder().url("$baseUrl/api/clear")
            .post("{}".toRequestBody("application/json".toMediaType()))
            .withBearer(token).build()
        runCatching { standardClient.newCall(req).execute().close() }
    }

    fun fetchAudioChunk(baseUrl: String, token: String, chunkId: Int): ByteArray? {
        val req = Request.Builder().url("$baseUrl/api/audio/$chunkId")
            .withBearer(token).get().build()
        return try {
            streamClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.bytes() else null
            }
        } catch (e: Exception) { null }
    }

    // ── Code interpreter ──────────────────────────────────────────────────────

    data class ExecResult(val stdout: String, val stderr: String,
                          val exitCode: Int, val runtimeMs: Int)

    fun execCode(baseUrl: String, token: String, lang: String, code: String,
                 timeout: Int = 30): ExecResult {
        val body = JSONObject().put("lang", lang).put("code", code).put("timeout", timeout)
            .toString().toRequestBody("application/json".toMediaType())
        val req = Request.Builder().url("$baseUrl/api/exec")
            .post(body).withBearer(token).build()
        val client = buildClient(timeout.toLong() + 10)
        return try {
            client.newCall(req).execute().use { resp ->
                val o = JSONObject(resp.body?.string() ?: "{}")
                ExecResult(o.optString("stdout"), o.optString("stderr"),
                           o.optInt("exit_code", -1), o.optInt("runtime_ms", 0))
            }
        } catch (e: Exception) { ExecResult("", e.message ?: "error", -1, 0) }
    }

    // ── STT transcription (remote Whisper) ────────────────────────────────────

    fun transcribeAudio(baseUrl: String, token: String, wavBytes: ByteArray): String {
        val body = wavBytes.toRequestBody("audio/wav".toMediaType())
        val req  = Request.Builder().url("$baseUrl/api/stt/transcribe")
            .post(body).withBearer(token).build()
        return try {
            streamClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return ""
                JSONObject(resp.body?.string() ?: "{}").optString("text", "")
            }
        } catch (e: Exception) { "" }
    }

    // ── System monitor ────────────────────────────────────────────────────────

    data class MonitorStats(val cpu: Float, val mem: Float, val disk: Float)

    fun getMonitorStats(baseUrl: String, token: String): MonitorStats? {
        val req = Request.Builder().url("$baseUrl/api/monitor").withBearer(token).get().build()
        return try {
            standardClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val o = JSONObject(resp.body!!.string())
                MonitorStats(o.optDouble("cpu", 0.0).toFloat(),
                             o.optDouble("mem", 0.0).toFloat(),
                             o.optDouble("disk", 0.0).toFloat())
            }
        } catch (e: Exception) { null }
    }

    // Extended streamSync that also surfaces monitor alerts
    fun streamSyncWithAlerts(
        baseUrl:  String,
        token:    String,
        onEvent:  (typing: Boolean, histLen: Int) -> Unit,
        onAlert:  (type: String, msg: String, val_: Float) -> Unit,
        onClosed: () -> Unit,
    ) {
        val req = Request.Builder().url("$baseUrl/api/sync")
            .withBearer(token).addHeader("Accept", "text/event-stream").get().build()
        try {
            streamClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) { onClosed(); return }
                val reader = BufferedReader(InputStreamReader(resp.body!!.byteStream()))
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val l = line!!; if (!l.startsWith("data: ")) continue
                    try {
                        val obj = JSONObject(l.removePrefix("data: "))
                        if (obj.has("alert")) {
                            val a = obj.getJSONObject("alert")
                            onAlert(a.optString("type"), a.optString("msg"),
                                    a.optDouble("val", 0.0).toFloat())
                        } else {
                            onEvent(obj.optBoolean("typing", false), obj.optInt("hist_len", 0))
                        }
                    } catch (_: Exception) {}
                }
            }
        } catch (_: Exception) {}
        onClosed()
    }

    // ── Home Assistant / HomeLab ───────────────────────────────────────────────

    data class HaConfig(
        val url:            String,
        val username:       String,
        val password:       String,
        val mainSwitch:     String,
        val computerSwitch: String,
    )

    data class HaStatus(
        val main:     String,
        val computer: String,
        val busy:     Boolean,
        val sequence: String?,
    )

    data class HaLogEntry(val ts: Double, val msg: String)

    fun getHaConfig(baseUrl: String, token: String): HaConfig? = try {
        val req = Request.Builder().url("$baseUrl/api/ha/config").withBearer(token).get().build()
        standardClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return null
            val o = JSONObject(resp.body!!.string())
            HaConfig(
                url            = o.optString("url",             ""),
                username       = o.optString("username",        ""),
                password       = o.optString("password",        ""),
                mainSwitch     = o.optString("main_switch",     "switch.homelab_main_switch"),
                computerSwitch = o.optString("computer_switch", "switch.homelab_computer_switch"),
            )
        }
    } catch (e: Exception) { null }

    fun saveHaConfig(baseUrl: String, token: String, config: HaConfig): Boolean = try {
        val obj = JSONObject()
            .put("url",             config.url)
            .put("username",        config.username)
            .put("password",        config.password)
            .put("main_switch",     config.mainSwitch)
            .put("computer_switch", config.computerSwitch)
        val body = obj.toString().toRequestBody("application/json".toMediaType())
        val req  = Request.Builder().url("$baseUrl/api/ha/config").post(body).withBearer(token).build()
        standardClient.newCall(req).execute().use { it.isSuccessful }
    } catch (e: Exception) { false }

    fun getHaStatus(baseUrl: String, token: String): HaStatus? = try {
        val req = Request.Builder().url("$baseUrl/api/ha/status").withBearer(token).get().build()
        standardClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return null
            val o = JSONObject(resp.body!!.string())
            HaStatus(
                main     = o.optString("main",     "unknown"),
                computer = o.optString("computer", "unknown"),
                busy     = o.optBoolean("busy",    false),
                sequence = if (o.isNull("sequence")) null else o.optString("sequence"),
            )
        }
    } catch (e: Exception) { null }

    fun haAction(baseUrl: String, token: String, action: String): Boolean = try {
        val body = JSONObject().put("action", action).toString()
            .toRequestBody("application/json".toMediaType())
        val req  = Request.Builder().url("$baseUrl/api/ha/action").post(body).withBearer(token).build()
        standardClient.newCall(req).execute().use { it.isSuccessful }
    } catch (e: Exception) { false }

    fun getHaLog(baseUrl: String, token: String): List<HaLogEntry> = try {
        val req = Request.Builder().url("$baseUrl/api/ha/log").withBearer(token).get().build()
        standardClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return emptyList()
            val arr = JSONObject(resp.body!!.string()).getJSONArray("entries")
            (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                HaLogEntry(ts = o.optDouble("ts", 0.0), msg = o.optString("msg", ""))
            }
        }
    } catch (e: Exception) { emptyList() }

    fun testHaConnection(baseUrl: String, token: String): Pair<Boolean, String> = try {
        val body = "{}".toRequestBody("application/json".toMediaType())
        val req  = Request.Builder().url("$baseUrl/api/ha/test").post(body).withBearer(token).build()
        standardClient.newCall(req).execute().use { resp ->
            val o   = JSONObject(resp.body!!.string())
            val ok  = o.optBoolean("ok", false)
            val msg = o.optString("message", if (ok) "Connected" else "Failed")
            Pair(ok, msg)
        }
    } catch (e: Exception) { Pair(false, e.message ?: "error") }

    // ── Device unlock passwords (server-side, synced to all clients) ───────────

    fun getDevicePasswords(baseUrl: String, token: String): Map<String, String> = try {
        val req = Request.Builder().url("$baseUrl/api/device/passwords").withBearer(token).get().build()
        standardClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return emptyMap()
            val o = JSONObject(resp.body!!.string())
            o.keys().asSequence().associateWith { o.optString(it, "") }
        }
    } catch (e: Exception) { emptyMap() }

    fun saveDevicePasswordRemote(baseUrl: String, token: String, deviceId: String, password: String): Boolean = try {
        val body = JSONObject().put("device_id", deviceId).put("password", password)
            .toString().toRequestBody("application/json".toMediaType())
        val req = Request.Builder().url("$baseUrl/api/device/password").post(body).withBearer(token).build()
        standardClient.newCall(req).execute().use { it.isSuccessful }
    } catch (e: Exception) { false }

    // ── Hypervisor API ────────────────────────────────────────────────────────

    data class HvVm(val id: String, val name: String, val status: String,
                    val vcpus: Int, val memoryMb: Long)
    data class HvContainer(val name: String, val status: String,
                           val memoryMb: Long, val cpus: Int)
    data class HvMetrics(val cpu: Double, val mem: Double, val disk: Double,
                         val vmsTotal: Int, val vmsActive: Int,
                         val ctsTotal: Int, val ctsActive: Int)

    fun getHvToken(hvUrl: String, password: String): String {
        val body = JSONObject().put("password", password).toString()
            .toRequestBody("application/json".toMediaType())
        val req = Request.Builder().url("$hvUrl/api/auth/login").post(body).build()
        standardClient.newCall(req).execute().use { resp ->
            val text = resp.body?.string() ?: throw Exception("empty response")
            if (!resp.isSuccessful) throw Exception("invalid password")
            return JSONObject(text).getString("token")
        }
    }

    fun listHvVms(hvUrl: String, hvToken: String): List<HvVm> = try {
        val req = Request.Builder().url("$hvUrl/api/vms").withBearer(hvToken).get().build()
        standardClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return emptyList()
            val arr = JSONObject(resp.body!!.string()).getJSONArray("vms")
            (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                HvVm(o.getString("id"), o.getString("name"), o.optString("status", "unknown"),
                     o.optInt("vcpus", 1), o.optLong("memory_mb", 0))
            }
        }
    } catch (e: Exception) { emptyList() }

    fun hvVmAction(hvUrl: String, hvToken: String, vmId: String, action: String): Boolean = try {
        val req = Request.Builder().url("$hvUrl/api/vms/$vmId/$action")
            .post("{}".toRequestBody("application/json".toMediaType())).withBearer(hvToken).build()
        standardClient.newCall(req).execute().use { it.isSuccessful }
    } catch (e: Exception) { false }

    fun listHvContainers(hvUrl: String, hvToken: String): List<HvContainer> = try {
        val req = Request.Builder().url("$hvUrl/api/containers").withBearer(hvToken).get().build()
        standardClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return emptyList()
            val arr = JSONObject(resp.body!!.string()).getJSONArray("containers")
            (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                HvContainer(o.getString("name"), o.optString("status", "unknown"),
                            o.optLong("memory_mb", 0), o.optInt("cpus", 1))
            }
        }
    } catch (e: Exception) { emptyList() }

    fun hvContainerAction(hvUrl: String, hvToken: String, ctName: String, action: String): Boolean = try {
        val req = Request.Builder().url("$hvUrl/api/containers/$ctName/$action")
            .post("{}".toRequestBody("application/json".toMediaType())).withBearer(hvToken).build()
        standardClient.newCall(req).execute().use { it.isSuccessful }
    } catch (e: Exception) { false }

    fun getHvMetrics(hvUrl: String, hvToken: String): HvMetrics? = try {
        val req = Request.Builder().url("$hvUrl/api/metrics").withBearer(hvToken).get().build()
        standardClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return null
            val o = JSONObject(resp.body!!.string())
            HvMetrics(o.optDouble("cpu", 0.0), o.optDouble("mem", 0.0), o.optDouble("disk", 0.0),
                      o.optInt("vms_total", 0), o.optInt("vms_active", 0),
                      o.optInt("cts_total", 0), o.optInt("cts_active", 0))
        }
    } catch (e: Exception) { null }

    fun hvCommand(hvUrl: String, hvToken: String, command: String): String = try {
        val body = JSONObject().put("command", command).toString()
            .toRequestBody("application/json".toMediaType())
        val req = Request.Builder().url("$hvUrl/api/nexis/command")
            .post(body).withBearer(hvToken).build()
        standardClient.newCall(req).execute().use { resp ->
            val text = resp.body?.string() ?: return "No response"
            JSONObject(text).optString("result", text)
        }
    } catch (e: Exception) { "Error: ${e.message}" }
}
