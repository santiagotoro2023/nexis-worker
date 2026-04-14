package ch.toroag.nexis.worker.data

import android.content.Context
import ch.toroag.nexis.worker.util.TofuTrustManager
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

class NexisApiService(
    private val prefs:   PreferencesRepository,
    private val context: Context,
    /** Called when a new cert is pinned (first connection). */
    onCertPinned: ((fingerprint: String) -> Unit)? = null,
) {
    // TOFU trust manager — accepts self-signed certs, pins on first use
    private val trustManager = TofuTrustManager(context, onCertPinned)

    private fun buildClient(readTimeoutSec: Long): OkHttpClient {
        val sslCtx = SSLContext.getInstance("TLS")
        sslCtx.init(null, arrayOf(trustManager), SecureRandom())
        return OkHttpClient.Builder()
            .sslSocketFactory(sslCtx.socketFactory, trustManager)
            // Self-signed certs won't match the hostname — TOFU fingerprint
            // pinning provides the security guarantee instead of hostname checks.
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

    /** Exchange a plaintext password for a persistent Bearer token. */
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
        onAudioReady: (Int) -> Unit,
        onDone:       () -> Unit,
        onError:      (String) -> Unit,
    ) {
        val bodyObj = JSONObject().put("msg", msg)
        if (fileData     != null) bodyObj.put("file_data", fileData)
        if (fileMimeType != null) bodyObj.put("file_type", fileMimeType)
        if (fileName     != null) bodyObj.put("file_name", fileName)
        val bodyJson = bodyObj.toString()
            .toRequestBody("application/json".toMediaType())
        val req = Request.Builder()
            .url("$baseUrl/api/chat")
            .post(bodyJson)
            .withBearer(token)
            .build()
        try {
            streamClient.newCall(req).execute().use { resp ->
                if (resp.code == 401) { onError("401"); return }
                if (!resp.isSuccessful) { onError("HTTP ${resp.code}"); return }
                val reader = BufferedReader(InputStreamReader(resp.body!!.byteStream()))
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val l = line!! ; if (!l.startsWith("data: ")) continue
                    val data = l.removePrefix("data: ")
                    when {
                        data == "[DONE]" -> { onDone(); return }
                        data.startsWith("[AUDIOREADY:") -> {
                            val id = data.removeSurrounding("[AUDIOREADY:", "]").toIntOrNull()
                            if (id != null) onAudioReady(id)
                        }
                        data == "[CLEAR]" -> onClear()
                        data.startsWith("[STATUS:") -> {}
                        else -> onToken(data.replace('\u0000', '\n'))
                    }
                }
                onDone()
            }
        } catch (e: Exception) {
            onError(e.message ?: "stream error")
        }
    }

    fun abortChat(baseUrl: String, token: String) {
        val req = Request.Builder()
            .url("$baseUrl/api/chat/abort")
            .post("{}".toRequestBody("application/json".toMediaType()))
            .withBearer(token)
            .build()
        runCatching { standardClient.newCall(req).execute().close() }
    }

    // ── Models ────────────────────────────────────────────────────────────────

    data class ModelInfo(val key: String, val label: String, val desc: String,
                         val installed: Boolean, val current: Boolean)

    fun getModels(baseUrl: String, token: String): List<ModelInfo> {
        val req = Request.Builder().url("$baseUrl/api/models").withBearer(token).get().build()
        standardClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return emptyList()
            val arr = JSONObject(resp.body!!.string()).getJSONArray("models")
            return (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                ModelInfo(o.getString("key"), o.getString("label"), o.getString("desc"),
                          o.optBoolean("installed", true), o.optBoolean("current", false))
            }
        }
    }

    fun setModel(baseUrl: String, token: String, modelKey: String) {
        val body = JSONObject().put("model", modelKey).toString()
            .toRequestBody("application/json".toMediaType())
        val req = Request.Builder().url("$baseUrl/api/model")
            .post(body).withBearer(token).build()
        runCatching { standardClient.newCall(req).execute().close() }
    }

    // ── Voice / TTS ───────────────────────────────────────────────────────────

    fun enableVoice(baseUrl: String, token: String, on: Boolean) {
        val body = JSONObject().put("on", on).toString()
            .toRequestBody("application/json".toMediaType())
        val req = Request.Builder().url("$baseUrl/api/voice")
            .post(body).withBearer(token).build()
        runCatching { standardClient.newCall(req).execute().close() }
    }

    // ── Cross-device sync ─────────────────────────────────────────────────────

    data class HistoryMessage(val role: String, val content: String)

    fun getHistory(baseUrl: String, token: String): List<HistoryMessage> {
        val req = Request.Builder().url("$baseUrl/api/history").withBearer(token).get().build()
        return try {
            standardClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return emptyList()
                val arr = org.json.JSONObject(resp.body!!.string()).getJSONArray("history")
                (0 until arr.length()).map {
                    val o = arr.getJSONObject(it)
                    HistoryMessage(o.getString("role"), o.getString("content"))
                }
            }
        } catch (e: Exception) { emptyList() }
    }

    /** Subscribes to /api/sync SSE. Calls onEvent on each {typing, hist_len} event.
     *  Blocks until the connection drops, then calls onClosed. */
    fun streamSync(
        baseUrl:   String,
        token:     String,
        onEvent:   (typing: Boolean, histLen: Int) -> Unit,
        onClosed:  () -> Unit,
    ) {
        val req = Request.Builder()
            .url("$baseUrl/api/sync")
            .withBearer(token)
            .addHeader("Accept", "text/event-stream")
            .get().build()
        try {
            streamClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) { onClosed(); return }
                val reader = BufferedReader(InputStreamReader(resp.body!!.byteStream()))
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val l = line!!
                    if (!l.startsWith("data: ")) continue
                    try {
                        val obj = org.json.JSONObject(l.removePrefix("data: "))
                        val typing  = obj.optBoolean("typing", false)
                        val histLen = obj.optInt("hist_len", 0)
                        onEvent(typing, histLen)
                    } catch (_: Exception) {}
                }
            }
        } catch (_: Exception) {}
        onClosed()
    }

    // ── Health / dashboard ────────────────────────────────────────────────────

    data class HealthInfo(
        val model:       String,
        val modelLabel:  String,
        val voice:       Boolean,
        val voiceModel:  String,
        val memories:    Int,
        val sessions:    Int,
        val histLen:     Int,
        val uptimeSeconds: Int,
    )

    fun getHealth(baseUrl: String, token: String): HealthInfo? {
        val req = Request.Builder().url("$baseUrl/api/health").withBearer(token).get().build()
        return try {
            standardClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val o = JSONObject(resp.body!!.string())
                HealthInfo(
                    model        = o.getString("model"),
                    modelLabel   = o.getString("model_label"),
                    voice        = o.getBoolean("voice"),
                    voiceModel   = o.getString("voice_model"),
                    memories     = o.getInt("memories"),
                    sessions     = o.getInt("sessions"),
                    histLen      = o.getInt("hist_len"),
                    uptimeSeconds = o.getInt("uptime"),
                )
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
    data class SessionSummary(
        val sessionId: String,
        val started:   String,
        val source:    String,
        val title:     String,
        val preview:   List<SessionMessage>,
    )

    fun getHistorySessions(baseUrl: String, token: String): List<SessionSummary> {
        val req = Request.Builder().url("$baseUrl/api/history/sessions").withBearer(token).get().build()
        return try {
            standardClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return emptyList()
                val arr = JSONObject(resp.body!!.string()).getJSONArray("sessions")
                (0 until arr.length()).map {
                    val o    = arr.getJSONObject(it)
                    val prev = o.getJSONArray("preview")
                    SessionSummary(
                        sessionId = o.getString("session_id"),
                        started   = o.getString("started"),
                        source    = o.optString("source", "web"),
                        title     = o.optString("title", ""),
                        preview   = (0 until prev.length()).map { i ->
                            val m = prev.getJSONObject(i)
                            SessionMessage(m.getString("role"), m.getString("content"))
                        },
                    )
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

    // ── Schedules ─────────────────────────────────────────────────────────────

    data class ScheduleEntry(
        val id:      Int,
        val name:    String,
        val expr:    String,
        val prompt:  String,
        val active:  Boolean,
        val lastRun: String?,
    )

    fun getSchedules(baseUrl: String, token: String): List<ScheduleEntry> {
        val req = Request.Builder().url("$baseUrl/api/schedules").withBearer(token).get().build()
        return try {
            standardClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return emptyList()
                val arr = JSONObject(resp.body!!.string()).getJSONArray("schedules")
                (0 until arr.length()).map {
                    val o = arr.getJSONObject(it)
                    ScheduleEntry(
                        id      = o.getInt("id"),
                        name    = o.optString("name", ""),
                        expr    = o.optString("expr", ""),
                        prompt  = o.optString("prompt", ""),
                        active  = o.optBoolean("active", true),
                        lastRun = if (o.isNull("last_run")) null else o.optString("last_run"),
                    )
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

    /** Clears the active conversation on the server (keeps memories/history). */
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
}
