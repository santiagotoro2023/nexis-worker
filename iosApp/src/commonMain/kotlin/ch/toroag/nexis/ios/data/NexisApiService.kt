package ch.toroag.nexis.ios.data

import io.ktor.client.*
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.timeout
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*

class NexisApiService {
    private val client = HttpClient {
        expectSuccess = false
        install(HttpTimeout)
    }

    data class TokenResult(val token: String, val role: String)
    data class ModelInfo(val key: String, val label: String, val desc: String, val installed: Boolean, val current: Boolean)
    data class HealthInfo(val model: String, val modelLabel: String, val voice: Boolean, val voiceModel: String, val memories: Int, val histLen: Int, val uptimeSeconds: Int)
    data class SessionMessage(val role: String, val content: String)
    data class SessionSummary(val sessionId: String, val started: String, val title: String, val preview: List<SessionMessage>)
    data class MemoryEntry(val id: Int, val content: String, val createdAt: String)
    data class ScheduleEntry(val id: Int, val name: String, val expr: String, val prompt: String, val active: Boolean, val lastRun: String?)
    data class CommandEntry(val id: Int, val label: String, val cmd: String, val icon: String)
    data class PersonalityInfo(val name: String, val systemPrompt: String, val voiceEnabled: Boolean)
    data class DeviceInfo(val deviceId: String, val hostname: String, val os: String, val deviceType: String, val online: Boolean, val batteryPct: Int?, val charging: Boolean?, val ip: String, val mac: String, val role: String?, val lastSeen: String)
    data class HvVm(val id: String, val name: String, val status: String, val vcpus: Int, val memoryMb: Long)
    data class HvContainer(val name: String, val status: String, val memoryMb: Long)
    data class HvMetrics(val cpu: Double, val mem: Double, val disk: Double, val vmsTotal: Int, val vmsActive: Int, val ctsTotal: Int, val ctsActive: Int)
    data class HaStatus(val main: String, val computer: String, val busy: Boolean)
    data class MonitorStats(val cpu: Float, val mem: Float, val disk: Float)

    suspend fun getToken(baseUrl: String, username: String, password: String): TokenResult {
        val resp = client.post("$baseUrl/api/token") {
            contentType(ContentType.Application.Json)
            setBody("""{"username":"${username.esc}","password":"${password.esc}"}""")
        }
        if (!resp.status.isSuccess()) throw Exception("Invalid username or password")
        val obj = Json.parseToJsonElement(resp.bodyAsText()).jsonObject
        return TokenResult(obj.str("token"), obj["role"]?.jsonPrimitive?.content ?: "user")
    }

    suspend fun streamChat(baseUrl: String, token: String, msg: String,
                           onToken: (String) -> Unit, onClear: () -> Unit = {},
                           onDone: () -> Unit, onError: (String) -> Unit) = withContext(Dispatchers.IO) {
        try {
            val resp = client.post("$baseUrl/api/chat") {
                contentType(ContentType.Application.Json)
                header("Authorization", "Bearer $token")
                setBody("""{"msg":"${msg.esc}"}""") 
                timeout { requestTimeoutMillis = 300_000 }
            }
            if (resp.status.value == 401) { onError("401"); return@withContext }
            if (!resp.status.isSuccess()) { onError("HTTP ${resp.status.value}"); return@withContext }
            val channel = resp.bodyAsChannel()
            while (!channel.isClosedForRead) {
                val line = channel.readUTF8Line() ?: break
                if (!line.startsWith("data: ")) continue
                val data = line.removePrefix("data: ")
                when {
                    data == "[DONE]"            -> { onDone(); return@withContext }
                    data == "[CLEAR]"           -> onClear()
                    data.startsWith("[STATUS:") -> {}
                    data.startsWith("[AUDIO")   -> {}
                    else                         -> onToken(data.replace(' ', '\n'))
                }
            }
            onDone()
        } catch (e: Exception) { onError(e.message ?: "stream error") }
    }

    suspend fun abortChat(baseUrl: String, token: String) = runCatching {
        client.post("$baseUrl/api/chat/abort") { contentType(ContentType.Application.Json); header("Authorization", "Bearer $token"); setBody("{}") }
    }

    suspend fun getModels(baseUrl: String, token: String): List<ModelInfo> = runCatching {
        val resp = client.get("$baseUrl/api/models") { header("Authorization", "Bearer $token") }
        if (!resp.status.isSuccess()) return emptyList()
        val arr = Json.parseToJsonElement(resp.bodyAsText()).jsonObject["models"]?.jsonArray ?: return emptyList()
        arr.map { el -> val o = el.jsonObject; ModelInfo(o.str("key"), o.str("label"), o.str("desc"), o["installed"]?.jsonPrimitive?.boolean ?: true, o["current"]?.jsonPrimitive?.boolean ?: false) }
    }.getOrDefault(emptyList())

    suspend fun setModel(baseUrl: String, token: String, modelKey: String) = runCatching {
        client.post("$baseUrl/api/model") { contentType(ContentType.Application.Json); header("Authorization", "Bearer $token"); setBody("""{"model":"${modelKey.esc}"}""") }
    }

    suspend fun getHealth(baseUrl: String, token: String): HealthInfo? = runCatching {
        val resp = client.get("$baseUrl/api/health") { header("Authorization", "Bearer $token") }
        if (!resp.status.isSuccess()) return null
        val o = Json.parseToJsonElement(resp.bodyAsText()).jsonObject
        HealthInfo(o.str("model"), o.str("model_label"), o["voice"]?.jsonPrimitive?.boolean ?: false,
                   o.str("voice_model"), o["memories"]?.jsonPrimitive?.int ?: 0,
                   o["hist_len"]?.jsonPrimitive?.int ?: 0, o["uptime"]?.jsonPrimitive?.int ?: 0)
    }.getOrNull()

    suspend fun getHistorySessions(baseUrl: String, token: String): List<SessionSummary> = runCatching {
        val resp = client.get("$baseUrl/api/history/sessions") { header("Authorization", "Bearer $token") }
        if (!resp.status.isSuccess()) return emptyList()
        val arr = Json.parseToJsonElement(resp.bodyAsText()).jsonObject["sessions"]?.jsonArray ?: return emptyList()
        arr.map { el ->
            val o = el.jsonObject
            val prev = o["preview"]?.jsonArray ?: JsonArray(emptyList())
            SessionSummary(o.str("session_id"), o.str("started"), o["title"]?.jsonPrimitive?.content ?: "",
                prev.map { m -> val mo = m.jsonObject; SessionMessage(mo.str("role"), mo.str("content")) })
        }
    }.getOrDefault(emptyList())

    suspend fun deleteHistorySession(baseUrl: String, token: String, sessionId: String) = runCatching {
        client.post("$baseUrl/api/history/sessions") { contentType(ContentType.Application.Json); header("Authorization", "Bearer $token"); setBody("""{"action":"delete","session_id":"${sessionId.esc}"}""") }
    }

    suspend fun loadHistorySession(baseUrl: String, token: String, sessionId: String) = runCatching {
        client.post("$baseUrl/api/history/load") { contentType(ContentType.Application.Json); header("Authorization", "Bearer $token"); setBody("""{"session_id":"${sessionId.esc}"}""") }
    }

    suspend fun clearConversation(baseUrl: String, token: String) = runCatching {
        client.post("$baseUrl/api/clear") { contentType(ContentType.Application.Json); header("Authorization", "Bearer $token"); setBody("{}") }
    }

    suspend fun getMemories(baseUrl: String, token: String): List<MemoryEntry> = runCatching {
        val resp = client.get("$baseUrl/api/memories") { header("Authorization", "Bearer $token") }
        if (!resp.status.isSuccess()) return emptyList()
        val arr = Json.parseToJsonElement(resp.bodyAsText()).jsonObject["memories"]?.jsonArray ?: return emptyList()
        arr.map { el -> val o = el.jsonObject; MemoryEntry(o["id"]?.jsonPrimitive?.int ?: 0, o.str("content"), o["created_at"]?.jsonPrimitive?.content ?: "") }
    }.getOrDefault(emptyList())

    suspend fun deleteMemory(baseUrl: String, token: String, id: Int) = runCatching {
        client.post("$baseUrl/api/memories") { contentType(ContentType.Application.Json); header("Authorization", "Bearer $token"); setBody("""{"action":"delete","id":$id}""") }
    }

    suspend fun getSchedules(baseUrl: String, token: String): List<ScheduleEntry> = runCatching {
        val resp = client.get("$baseUrl/api/schedules") { header("Authorization", "Bearer $token") }
        if (!resp.status.isSuccess()) return emptyList()
        val arr = Json.parseToJsonElement(resp.bodyAsText()).jsonObject["schedules"]?.jsonArray ?: return emptyList()
        arr.map { el -> val o = el.jsonObject; ScheduleEntry(o["id"]?.jsonPrimitive?.int ?: 0, o.str("name"), o.str("expr"), o.str("prompt"), o["active"]?.jsonPrimitive?.boolean ?: true, o["last_run"]?.jsonPrimitive?.contentOrNull) }
    }.getOrDefault(emptyList())

    suspend fun scheduleAction(baseUrl: String, token: String, action: String, id: Int? = null, name: String? = null, expr: String? = null, prompt: String? = null, active: Boolean? = null) = runCatching {
        val obj = buildJsonObject { put("action", action); if (id != null) put("id", id); if (name != null) put("name", name); if (expr != null) put("expr", expr); if (prompt != null) put("prompt", prompt); if (active != null) put("active", active) }
        client.post("$baseUrl/api/schedules") { contentType(ContentType.Application.Json); header("Authorization", "Bearer $token"); setBody(obj.toString()) }
    }

    suspend fun getCommands(baseUrl: String, token: String): List<CommandEntry> = runCatching {
        val resp = client.get("$baseUrl/api/commands") { header("Authorization", "Bearer $token") }
        if (!resp.status.isSuccess()) return emptyList()
        val arr = Json.parseToJsonElement(resp.bodyAsText()).jsonObject["commands"]?.jsonArray ?: return emptyList()
        arr.map { el -> val o = el.jsonObject; CommandEntry(o["id"]?.jsonPrimitive?.int ?: 0, o.str("label"), o.str("cmd"), o.str("icon")) }
    }.getOrDefault(emptyList())

    suspend fun runCommand(baseUrl: String, token: String, cmd: String): String = runCatching {
        val resp = client.post("$baseUrl/api/command") { contentType(ContentType.Application.Json); header("Authorization", "Bearer $token"); setBody("""{"cmd":"${cmd.esc}"}""") }
        Json.parseToJsonElement(resp.bodyAsText()).jsonObject["result"]?.jsonPrimitive?.content ?: ""
    }.getOrDefault("")

    suspend fun getPersonality(baseUrl: String, token: String): PersonalityInfo? = runCatching {
        val resp = client.get("$baseUrl/api/personality") { header("Authorization", "Bearer $token") }
        if (!resp.status.isSuccess()) return null
        val o = Json.parseToJsonElement(resp.bodyAsText()).jsonObject
        PersonalityInfo(o.str("name"), o.str("system_prompt"), o["voice_enabled"]?.jsonPrimitive?.boolean ?: false)
    }.getOrNull()

    suspend fun savePersonality(baseUrl: String, token: String, name: String, systemPrompt: String, voiceEnabled: Boolean) = runCatching {
        client.post("$baseUrl/api/personality") { contentType(ContentType.Application.Json); header("Authorization", "Bearer $token"); setBody("""{"name":"${name.esc}","system_prompt":"${systemPrompt.esc}","voice_enabled":$voiceEnabled}""") }
    }

    suspend fun getDevices(baseUrl: String, token: String): List<DeviceInfo> = runCatching {
        val resp = client.get("$baseUrl/api/devices") { header("Authorization", "Bearer $token") }
        if (!resp.status.isSuccess()) return emptyList()
        val arr = Json.parseToJsonElement(resp.bodyAsText()).jsonObject["devices"]?.jsonArray ?: return emptyList()
        arr.map { el ->
            val o = el.jsonObject
            DeviceInfo(o.str("device_id"), o.str("hostname"), o.str("os"), o["device_type"]?.jsonPrimitive?.content ?: "desktop",
                o["online"]?.jsonPrimitive?.boolean ?: false, o["battery_pct"]?.jsonPrimitive?.intOrNull,
                o["charging"]?.jsonPrimitive?.booleanOrNull, o.str("ip"), o.str("mac"),
                o["role"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotEmpty() && it != "null" }, o.str("last_seen"))
        }
    }.getOrDefault(emptyList())

    suspend fun sendDeviceCommand(baseUrl: String, token: String, deviceId: String, action: String, arg: String = ""): String = runCatching {
        val resp = client.post("$baseUrl/api/device/command") { contentType(ContentType.Application.Json); header("Authorization", "Bearer $token"); setBody("""{"device_id":"${deviceId.esc}","action":"${action.esc}","arg":"${arg.esc}"}""") }
        val o = Json.parseToJsonElement(resp.bodyAsText()).jsonObject
        if (o["ok"]?.jsonPrimitive?.boolean == true) "queued" else "failed"
    }.getOrDefault("error")

    suspend fun wakeOnLan(baseUrl: String, token: String, mac: String): String = runCatching {
        val resp = client.post("$baseUrl/api/wol") { contentType(ContentType.Application.Json); header("Authorization", "Bearer $token"); setBody("""{"mac":"${mac.esc}"}""") }
        Json.parseToJsonElement(resp.bodyAsText()).jsonObject["result"]?.jsonPrimitive?.content ?: "sent"
    }.getOrDefault("error")

    suspend fun desktopAction(baseUrl: String, token: String, action: String, arg: String = "", deviceId: String = ""): String = runCatching {
        val obj = buildJsonObject { put("action", action); put("arg", arg); if (deviceId.isNotEmpty()) put("device_id", deviceId) }
        val resp = client.post("$baseUrl/api/desktop") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $token")
            setBody(obj.toString())
            timeout { requestTimeoutMillis = 60_000 }
        }
        if (!resp.status.isSuccess()) return "(error ${resp.status.value})"
        Json.parseToJsonElement(resp.bodyAsText()).jsonObject["result"]?.jsonPrimitive?.content ?: "(no result)"
    }.getOrDefault("(error)")

    suspend fun getHvToken(hvUrl: String, username: String, password: String): String {
        val resp = client.post("$hvUrl/api/auth/login") { contentType(ContentType.Application.Json); setBody("""{"username":"${username.esc}","password":"${password.esc}"}""") }
        if (!resp.status.isSuccess()) throw Exception("Invalid credentials")
        return Json.parseToJsonElement(resp.bodyAsText()).jsonObject["token"]?.jsonPrimitive?.content ?: throw Exception("No token")
    }

    suspend fun listHvVms(hvUrl: String, hvToken: String): List<HvVm> = runCatching {
        val resp = client.get("$hvUrl/api/vms") { header("Authorization", "Bearer $hvToken") }
        if (!resp.status.isSuccess()) return emptyList()
        val arr = Json.parseToJsonElement(resp.bodyAsText()).jsonObject["vms"]?.jsonArray ?: return emptyList()
        arr.map { el -> val o = el.jsonObject; HvVm(o.str("id"), o.str("name"), o.str("status"), o["vcpus"]?.jsonPrimitive?.int ?: 1, o["memory_mb"]?.jsonPrimitive?.long ?: 0) }
    }.getOrDefault(emptyList())

    suspend fun hvVmAction(hvUrl: String, hvToken: String, vmId: String, action: String) = runCatching {
        client.post("$hvUrl/api/vms/$vmId/$action") { contentType(ContentType.Application.Json); header("Authorization", "Bearer $hvToken"); setBody("{}") }
    }

    suspend fun listHvContainers(hvUrl: String, hvToken: String): List<HvContainer> = runCatching {
        val resp = client.get("$hvUrl/api/containers") { header("Authorization", "Bearer $hvToken") }
        if (!resp.status.isSuccess()) return emptyList()
        val arr = Json.parseToJsonElement(resp.bodyAsText()).jsonObject["containers"]?.jsonArray ?: return emptyList()
        arr.map { el -> val o = el.jsonObject; HvContainer(o.str("name"), o.str("status"), o["memory_mb"]?.jsonPrimitive?.long ?: 0) }
    }.getOrDefault(emptyList())

    suspend fun hvContainerAction(hvUrl: String, hvToken: String, ctName: String, action: String) = runCatching {
        client.post("$hvUrl/api/containers/$ctName/$action") { contentType(ContentType.Application.Json); header("Authorization", "Bearer $hvToken"); setBody("{}") }
    }

    suspend fun getHvMetrics(hvUrl: String, hvToken: String): HvMetrics? = runCatching {
        val resp = client.get("$hvUrl/api/metrics") { header("Authorization", "Bearer $hvToken") }
        if (!resp.status.isSuccess()) return null
        val o = Json.parseToJsonElement(resp.bodyAsText()).jsonObject
        HvMetrics(o["cpu"]?.jsonPrimitive?.double ?: 0.0, o["mem"]?.jsonPrimitive?.double ?: 0.0, o["disk"]?.jsonPrimitive?.double ?: 0.0,
                  o["vms_total"]?.jsonPrimitive?.int ?: 0, o["vms_active"]?.jsonPrimitive?.int ?: 0,
                  o["cts_total"]?.jsonPrimitive?.int ?: 0, o["cts_active"]?.jsonPrimitive?.int ?: 0)
    }.getOrNull()

    suspend fun getHaStatus(baseUrl: String, token: String): HaStatus? = runCatching {
        val resp = client.get("$baseUrl/api/ha/status") { header("Authorization", "Bearer $token") }
        if (!resp.status.isSuccess()) return null
        val o = Json.parseToJsonElement(resp.bodyAsText()).jsonObject
        HaStatus(o.str("main"), o.str("computer"), o["busy"]?.jsonPrimitive?.boolean ?: false)
    }.getOrNull()

    suspend fun haAction(baseUrl: String, token: String, action: String) = runCatching {
        client.post("$baseUrl/api/ha/action") { contentType(ContentType.Application.Json); header("Authorization", "Bearer $token"); setBody("""{"action":"${action.esc}"}""") }
    }

    suspend fun getMonitorStats(baseUrl: String, token: String): MonitorStats? = runCatching {
        val resp = client.get("$baseUrl/api/monitor") { header("Authorization", "Bearer $token") }
        if (!resp.status.isSuccess()) return null
        val o = Json.parseToJsonElement(resp.bodyAsText()).jsonObject
        MonitorStats(o["cpu"]?.jsonPrimitive?.float ?: 0f, o["mem"]?.jsonPrimitive?.float ?: 0f, o["disk"]?.jsonPrimitive?.float ?: 0f)
    }.getOrNull()

    private val String.esc get() = replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
    private fun JsonObject.str(key: String) = this[key]?.jsonPrimitive?.content ?: ""
}
