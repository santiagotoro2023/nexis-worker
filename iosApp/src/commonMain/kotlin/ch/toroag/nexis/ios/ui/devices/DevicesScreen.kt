package ch.toroag.nexis.ios.ui.devices

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ch.toroag.nexis.ios.data.NexisApiService
import ch.toroag.nexis.ios.data.PreferencesRepository
import kotlinx.coroutines.launch

@Composable
fun DevicesScreen(prefs: PreferencesRepository) {
    val scope = rememberCoroutineScope(); val api = remember { NexisApiService() }
    val baseUrl by prefs.baseUrl.collectAsState(); val token by prefs.token.collectAsState()
    var devices by remember { mutableStateOf(listOf<NexisApiService.DeviceInfo>()) }
    var loading by remember { mutableStateOf(false) }; var feedback by remember { mutableStateOf("") }
    fun load() { loading = true; scope.launch { devices = api.getDevices(baseUrl, token); loading = false } }
    LaunchedEffect(Unit) { load() }
    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("Devices") }, actions = { IconButton(onClick = { load() }) { Icon(Icons.Default.Refresh, "Refresh") } })
        if (feedback.isNotEmpty()) Text(feedback, Modifier.padding(12.dp), color = MaterialTheme.colorScheme.primary)
        if (loading) Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        else LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(devices) { dev ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(when (dev.deviceType) { "mobile" -> Icons.Default.PhoneAndroid; "server" -> Icons.Default.Dns; else -> Icons.Default.Computer }, null, tint = if (dev.online) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(dev.hostname, style = MaterialTheme.typography.titleSmall)
                                Text("${dev.os} • ${dev.ip}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                            }
                            if (dev.batteryPct != null) Text("${dev.batteryPct}%", style = MaterialTheme.typography.bodySmall)
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { scope.launch { feedback = api.sendDeviceCommand(baseUrl, token, dev.deviceId, "lock") } }, Modifier.weight(1f)) { Text("Lock") }
                            OutlinedButton(onClick = { scope.launch { feedback = api.wakeOnLan(baseUrl, token, dev.mac) } }, Modifier.weight(1f)) { Text("WOL") }
                        }
                    }
                }
            }
        }
    }
}
