package ch.toroag.nexis.desktop.ui.commands

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import ch.toroag.nexis.desktop.data.PreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import java.security.cert.X509Certificate

data class WorkerCommand(
    val name:        String,
    val description: String,
    val isBuiltin:   Boolean,
    val id:          Int     = -1,
    val enabled:     Boolean = true,
    val commandType: String  = "",
)

class CommandsViewModel {
    private val prefs   = PreferencesRepository.get()
    private val baseUrl: String
    private val token:   String

    var builtinCommands by mutableStateOf<List<WorkerCommand>>(emptyList())
    var customCommands  by mutableStateOf<List<WorkerCommand>>(emptyList())
    var isLoading       by mutableStateOf(false)
    var statusMessage   by mutableStateOf("")
    var hasError        by mutableStateOf(false)

    private val trustAll: SSLContext by lazy {
        val tm = arrayOf<TrustManager>(object : X509TrustManager {
            override fun checkClientTrusted(c: Array<X509Certificate>, a: String) {}
            override fun checkServerTrusted(c: Array<X509Certificate>, a: String) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        })
        SSLContext.getInstance("TLS").also { it.init(null, tm, null) }
    }

    init {
        var url = ""
        var tok = ""
        runBlocking {
            url = prefs.baseUrl.first().trimEnd('/')
            tok = prefs.token.first()
        }
        baseUrl = url
        token   = tok
        load()
    }

    fun load() {
        CoroutineScope(Dispatchers.IO).launch {
            isLoading = true; hasError = false; statusMessage = ""
            try {
                val url  = URL("$baseUrl/api/tools")
                val conn = (if (url.protocol == "https")
                    (url.openConnection() as HttpsURLConnection).also { it.sslSocketFactory = trustAll.socketFactory }
                else url.openConnection() as HttpURLConnection).also {
                    it.setRequestProperty("Authorization", "Bearer $token")
                    it.connectTimeout = 8_000; it.readTimeout = 8_000
                }
                if (conn.responseCode == 200) {
                    val obj     = JSONObject(conn.inputStream.bufferedReader().readText())
                    val builtIn = obj.optJSONArray("builtin")
                    val custom  = obj.optJSONArray("custom")
                    builtinCommands = (0 until (builtIn?.length() ?: 0)).map { i ->
                        val item = builtIn!!.getJSONObject(i)
                        WorkerCommand(item.getString("name"), item.getString("description"), true)
                    }
                    customCommands = (0 until (custom?.length() ?: 0)).map { i ->
                        val item = custom!!.getJSONObject(i)
                        WorkerCommand(
                            name        = item.getString("name"),
                            description = item.getString("description"),
                            isBuiltin   = false,
                            id          = item.getInt("id"),
                            enabled     = item.getBoolean("enabled"),
                            commandType = item.optString("command_type", ""),
                        )
                    }
                } else {
                    hasError = true; statusMessage = "Failed to load (${conn.responseCode})."
                }
            } catch (e: Exception) {
                hasError = true; statusMessage = "Load failed: ${e.message}"
            } finally {
                isLoading = false
            }
        }
    }

    fun setEnabled(id: Int, enabled: Boolean) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val payload = JSONObject().apply {
                    put("action",  if (enabled) "enable" else "disable")
                    put("tool_id", id)
                }.toString().toByteArray()
                val url  = URL("$baseUrl/api/tools")
                val conn = (if (url.protocol == "https")
                    (url.openConnection() as HttpsURLConnection).also { it.sslSocketFactory = trustAll.socketFactory }
                else url.openConnection() as HttpURLConnection).also {
                    it.requestMethod = "POST"; it.doOutput = true
                    it.setRequestProperty("Content-Type",  "application/json")
                    it.setRequestProperty("Authorization", "Bearer $token")
                    it.connectTimeout = 8_000; it.readTimeout = 8_000
                }
                conn.outputStream.write(payload)
                if (conn.responseCode == 200) {
                    customCommands = customCommands.map {
                        if (it.id == id) it.copy(enabled = enabled) else it
                    }
                }
            } catch (_: Exception) { }
        }
    }
}
