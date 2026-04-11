package ch.toroag.nexis.worker.data

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

class NexisApiService(private val prefs: PreferencesRepository) {

    // Two clients:
    //   - streaming: long read timeout for SSE chat (5 min)
    //   - standard:  short timeout for regular API calls
    private fun makeClient(readTimeoutSec: Long): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .readTimeout(readTimeoutSec, TimeUnit.SECONDS)
            .build()

    private val streamClient  = makeClient(300)
    private val standardClient = makeClient(30)

    private fun Request.Builder.withBearer(token: String) =
        addHeader("Authorization", "Bearer $token")

    // ── Auth ─────────────────────────────────────────────────────────────────

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

    /**
     * Stream a chat message via SSE.
     * Calls back on the calling thread (run in IO dispatcher).
     */
    fun streamChat(
        baseUrl:      String,
        token:        String,
        msg:          String,
        onToken:      (String) -> Unit,
        onAudioReady: (Int) -> Unit,
        onDone:       () -> Unit,
        onError:      (String) -> Unit,
    ) {
        val bodyJson = JSONObject().put("msg", msg).toString()
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
                        data.startsWith("[STATUS:") || data == "[CLEAR]" -> {}
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
