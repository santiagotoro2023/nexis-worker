package ch.toroag.nexis.desktop.ui.personality

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import ch.toroag.nexis.desktop.data.PreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.net.HttpURLConnection
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import java.security.cert.X509Certificate

class PersonalityViewModel {
    private val prefs = PreferencesRepository.get()
    private val baseUrl: String
    private val token: String

    var name          by mutableStateOf("")
    var style         by mutableStateOf("")
    var basePrompt    by mutableStateOf("")
    var customInstr   by mutableStateOf("")
    var isLoading     by mutableStateOf(false)
    var statusMessage by mutableStateOf("")
    var hasError      by mutableStateOf(false)

    private val trustAll: SSLContext by lazy {
        val tm = arrayOf<TrustManager>(object : X509TrustManager {
            override fun checkClientTrusted(c: Array<X509Certificate>, a: String) {}
            override fun checkServerTrusted(c: Array<X509Certificate>, a: String) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        })
        SSLContext.getInstance("TLS").also { it.init(null, tm, null) }
    }

    init {
        runBlocking {
            baseUrl = prefs.serverUrl.first().trimEnd('/')
            token   = prefs.token.first()
        }
        load()
    }

    fun load() {
        CoroutineScope(Dispatchers.IO).launch {
            isLoading = true
            hasError  = false
            statusMessage = ""
            try {
                val url  = URL("$baseUrl/api/personality")
                val conn = (if (url.protocol == "https")
                    (url.openConnection() as HttpsURLConnection).also { it.sslSocketFactory = trustAll.socketFactory }
                else url.openConnection() as HttpURLConnection).also {
                    it.setRequestProperty("Authorization", "Bearer $token")
                    it.connectTimeout = 8_000
                    it.readTimeout    = 8_000
                }
                val body = conn.inputStream.bufferedReader().readText()
                val obj  = org.json.JSONObject(body)
                name        = obj.optString("name",                "NeXiS")
                style       = obj.optString("style",               "")
                basePrompt  = obj.optString("base_prompt",         "")
                customInstr = obj.optString("custom_instructions", "")
            } catch (e: Exception) {
                hasError      = true
                statusMessage = "Load failed: ${e.message}"
            } finally {
                isLoading = false
            }
        }
    }

    fun save() {
        CoroutineScope(Dispatchers.IO).launch {
            isLoading = true
            hasError  = false
            statusMessage = ""
            try {
                val payload = org.json.JSONObject().apply {
                    put("action",               "save")
                    put("name",                 name)
                    put("style",                style)
                    put("base_prompt",          basePrompt)
                    put("custom_instructions",  customInstr)
                }.toString().toByteArray()
                val url  = URL("$baseUrl/api/personality")
                val conn = (if (url.protocol == "https")
                    (url.openConnection() as HttpsURLConnection).also { it.sslSocketFactory = trustAll.socketFactory }
                else url.openConnection() as HttpURLConnection).also {
                    it.requestMethod = "POST"
                    it.doOutput = true
                    it.setRequestProperty("Content-Type",  "application/json")
                    it.setRequestProperty("Authorization", "Bearer $token")
                    it.connectTimeout = 8_000; it.readTimeout = 8_000
                }
                conn.outputStream.write(payload)
                val ok = conn.responseCode == 200
                statusMessage = if (ok) "Saved." else "Save failed (${conn.responseCode})."
                hasError = !ok
            } catch (e: Exception) {
                hasError = true; statusMessage = "Save failed: ${e.message}"
            } finally {
                isLoading = false
            }
        }
    }

    fun resetToDefault() {
        CoroutineScope(Dispatchers.IO).launch {
            isLoading = true; hasError = false; statusMessage = ""
            try {
                val payload = """{"action":"reset"}""".toByteArray()
                val url  = URL("$baseUrl/api/personality")
                val conn = (if (url.protocol == "https")
                    (url.openConnection() as HttpsURLConnection).also { it.sslSocketFactory = trustAll.socketFactory }
                else url.openConnection() as HttpURLConnection).also {
                    it.requestMethod = "POST"; it.doOutput = true
                    it.setRequestProperty("Content-Type",  "application/json")
                    it.setRequestProperty("Authorization", "Bearer $token")
                    it.connectTimeout = 8_000; it.readTimeout = 8_000
                }
                conn.outputStream.write(payload)
                if (conn.responseCode == 200) { statusMessage = "Reset to defaults."; load() }
                else { hasError = true; statusMessage = "Reset failed (${conn.responseCode})." }
            } catch (e: Exception) {
                hasError = true; statusMessage = "Reset failed: ${e.message}"
            } finally {
                isLoading = false
            }
        }
    }
}
