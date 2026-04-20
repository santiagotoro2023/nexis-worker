package ch.toroag.nexis.worker.ui.remote

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import ch.toroag.nexis.worker.data.NexisApiService
import ch.toroag.nexis.worker.data.PreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

class RemoteViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = PreferencesRepository.get(app)
    private val api   = NexisApiService(prefs, app)

    private val _result         = MutableStateFlow("")
    val result: StateFlow<String> = _result

    private val _isLoading      = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _devices        = MutableStateFlow<List<NexisApiService.DeviceInfo>>(emptyList())
    val devices: StateFlow<List<NexisApiService.DeviceInfo>> = _devices

    private val _selectedDevice = MutableStateFlow<NexisApiService.DeviceInfo?>(null)
    val selectedDevice: StateFlow<NexisApiService.DeviceInfo?> = _selectedDevice

    private val _devicesLoading = MutableStateFlow(false)
    val devicesLoading: StateFlow<Boolean> = _devicesLoading

    private val _haStatus      = MutableStateFlow<NexisApiService.HaStatus?>(null)
    val haStatus: StateFlow<NexisApiService.HaStatus?> = _haStatus

    private val _haLog         = MutableStateFlow<List<NexisApiService.HaLogEntry>>(emptyList())
    val haLog: StateFlow<List<NexisApiService.HaLogEntry>> = _haLog

    private val _haStatusBusy  = MutableStateFlow(false)
    val haStatusBusy: StateFlow<Boolean> = _haStatusBusy

    private var haPollingJob: Job? = null

    private var baseUrl = ""
    private var token   = ""

    init {
        viewModelScope.launch {
            combine(prefs.baseUrl, prefs.token) { u, t -> Pair(u, t) }.collect { (u, t) ->
                baseUrl = u; token = t
                if (u.isNotEmpty() && t.isNotEmpty()) loadDevices()
            }
        }
    }

    fun loadDevices() {
        viewModelScope.launch(Dispatchers.IO) {
            _devicesLoading.value = true
            // Show cached devices immediately — WOL button is available even before
            // the network call completes (or when the server is unreachable).
            val cached = parseCachedDevices(prefs.getCachedDevices())
            if (cached.isNotEmpty() && _devices.value.isEmpty()) {
                _devices.value = cached
                val cur = _selectedDevice.value
                if (cur == null || cur !in cached) {
                    _selectedDevice.value = cached.firstOrNull { it.role == "primary_pc" }
                        ?: cached.firstOrNull()
                }
            }
            // Try to refresh from server
            val live = runCatching { api.getDevices(baseUrl, token) }.getOrNull()
            val all = if (!live.isNullOrEmpty()) {
                prefs.saveCachedDevices(live.toJson())
                live
            } else {
                cached   // server unreachable — stick with cached (already shown)
            }
            _devices.value = all
            val cur = _selectedDevice.value
            if (cur == null || cur !in all) {
                _selectedDevice.value = all.firstOrNull { it.role == "primary_pc" }
                    ?: all.firstOrNull()
            }
            _devicesLoading.value = false
        }
    }

    fun selectDevice(device: NexisApiService.DeviceInfo?) {
        _selectedDevice.value = device
        _result.value = ""
    }

    fun action(action: String, arg: String = "") {
        if (_isLoading.value || baseUrl.isEmpty() || token.isEmpty()) return
        val dev = _selectedDevice.value ?: return
        // For unlock, auto-inject stored password if caller passed none
        viewModelScope.launch(Dispatchers.IO) {
            val resolvedArg = if (action == "unlock" && arg.isEmpty())
                prefs.getDevicePassword(dev.deviceId)
            else arg
            _isLoading.value = true
            _result.value    = ""
            _result.value    = api.desktopAction(baseUrl, token, action, resolvedArg, dev.deviceId)
            _isLoading.value = false
        }
    }

    fun mobileCommand(action: String, arg: String = "") {
        if (_isLoading.value || baseUrl.isEmpty() || token.isEmpty()) return
        val dev = _selectedDevice.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            _result.value    = ""
            _result.value    = api.sendDeviceCommand(baseUrl, token, dev.deviceId, action, arg)
            _isLoading.value = false
        }
    }

    fun pasteFromPc() {
        if (_isLoading.value || baseUrl.isEmpty() || token.isEmpty()) return
        val devId = _selectedDevice.value?.deviceId ?: ""
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            _result.value    = ""
            val text = api.desktopAction(baseUrl, token, "clip_read", "", devId)
            if (!text.startsWith("(")) {
                val cm = getApplication<Application>()
                    .getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("NeXiS PC Clipboard", text))
                _result.value = "pasted: ${text.take(80)}${if (text.length > 80) "…" else ""}"
            } else {
                _result.value = text
            }
            _isLoading.value = false
        }
    }

    /**
     * Send a Wake-on-LAN magic packet directly from the phone as UDP —
     * no server connection needed. Sends to both LAN broadcast and the
     * server hostname (for port-forwarded WOL via the router).
     */
    fun wakeOnLan() {
        val mac  = _selectedDevice.value?.mac?.ifEmpty { null } ?: run {
            _result.value = "(no MAC address stored for this device)"
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            _result.value    = ""
            _result.value    = sendWolDirectly(mac)
            _isLoading.value = false
        }
    }

    private fun sendWolDirectly(mac: String): String {
        val clean = mac.replace(":", "").replace("-", "").replace(".", "")
        if (clean.length != 12) return "(invalid MAC: $mac)"
        return try {
            val macBytes = clean.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            val packet = ByteArray(102)
            repeat(6)  { i -> packet[i] = 0xFF.toByte() }
            repeat(16) { i -> System.arraycopy(macBytes, 0, packet, 6 + i * 6, 6) }

            // Host from baseUrl (strips https:// and :port) for directed broadcast
            val host = baseUrl.removePrefix("https://").removePrefix("http://")
                .substringBefore(":").substringBefore("/").ifEmpty { null }

            DatagramSocket().use { socket ->
                socket.broadcast = true
                // LAN broadcast — works on the local network
                socket.send(DatagramPacket(packet, 102,
                    InetAddress.getByName("255.255.255.255"), 9))
                // Directed to server host on port 9 — works if router forwards UDP 9
                if (host != null) {
                    runCatching {
                        socket.send(DatagramPacket(packet, 102,
                            InetAddress.getByName(host), 9))
                    }
                }
            }
            "WOL sent to $mac — PC should wake up in a few seconds"
        } catch (e: Exception) {
            "(WOL failed: ${e.message})"
        }
    }

    fun startHaPolling() {
        if (haPollingJob?.isActive == true) return
        haPollingJob = viewModelScope.launch(Dispatchers.IO) {
            while (true) {
                if (baseUrl.isNotEmpty() && token.isNotEmpty()) {
                    _haStatus.value = runCatching { api.getHaStatus(baseUrl, token) }.getOrNull()
                    _haLog.value    = runCatching { api.getHaLog(baseUrl, token) }.getOrDefault(emptyList())
                }
                delay(2500)
            }
        }
    }

    fun stopHaPolling() { haPollingJob?.cancel(); haPollingJob = null }

    fun haAction(action: String) {
        if (baseUrl.isEmpty() || token.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            _haStatusBusy.value = true
            runCatching { api.haAction(baseUrl, token, action) }
            // Refresh status right away
            _haStatus.value = runCatching { api.getHaStatus(baseUrl, token) }.getOrNull()
            _haLog.value    = runCatching { api.getHaLog(baseUrl, token) }.getOrDefault(emptyList())
            _haStatusBusy.value = false
        }
    }

    fun probeSelectedDevice() {
        if (_isLoading.value || baseUrl.isEmpty() || token.isEmpty()) return
        val dev = _selectedDevice.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            _result.value    = ""
            _result.value    = if (dev.deviceType == "desktop" && dev.online)
                api.probeController(baseUrl, token)
            else
                api.probeDevice(baseUrl, token, dev.deviceId)
            _isLoading.value = false
        }
    }

    // ── Serialisation helpers for device cache ────────────────────────────────

    private fun List<NexisApiService.DeviceInfo>.toJson(): String {
        val arr = JSONArray()
        forEach { d ->
            arr.put(JSONObject().apply {
                put("device_id",   d.deviceId)
                put("hostname",    d.hostname)
                put("model",       d.model)
                put("os",          d.os)
                put("arch",        d.arch)
                put("device_type", d.deviceType)
                put("ip",          d.ip)
                put("mac",         d.mac)
                put("role",        d.role ?: "")
                put("online",      d.online)
                put("last_seen",   d.lastSeen)
                if (d.batteryPct != null) put("battery_pct", d.batteryPct)
                if (d.charging   != null) put("charging",    d.charging)
            })
        }
        return arr.toString()
    }

    private fun parseCachedDevices(json: String): List<NexisApiService.DeviceInfo> = try {
        val arr = JSONArray(json)
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            NexisApiService.DeviceInfo(
                deviceId     = o.getString("device_id"),
                hostname     = o.optString("hostname", ""),
                model        = o.optString("model",    ""),
                os           = o.optString("os",       ""),
                arch         = o.optString("arch",     ""),
                deviceType   = o.optString("device_type", "desktop"),
                capabilities = emptyList(),
                ip           = o.optString("ip",  ""),
                mac          = o.optString("mac", ""),
                role         = o.optString("role").takeIf { it.isNotEmpty() && it != "null" },
                online       = false,   // cached = assume offline until server confirms
                batteryPct   = if (o.isNull("battery_pct")) null else o.optInt("battery_pct"),
                charging     = if (o.isNull("charging"))    null else o.optBoolean("charging"),
                lastSeen     = o.optString("last_seen", ""),
            )
        }
    } catch (_: Exception) { emptyList() }
}
