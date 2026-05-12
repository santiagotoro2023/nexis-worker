package ch.toroag.nexis.ios.ui.hypervisor

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
fun HypervisorScreen(prefs: PreferencesRepository) {
    val scope = rememberCoroutineScope(); val api = remember { NexisApiService() }
    val hvUrl by prefs.hvUrl.collectAsState(); val hvToken by prefs.hvToken.collectAsState()
    var vms by remember { mutableStateOf(listOf<NexisApiService.HvVm>()) }
    var cts by remember { mutableStateOf(listOf<NexisApiService.HvContainer>()) }
    var metrics by remember { mutableStateOf<NexisApiService.HvMetrics?>(null) }
    var loading by remember { mutableStateOf(false) }
    var loginUrl by remember { mutableStateOf("") }; var loginUser by remember { mutableStateOf("") }; var loginPass by remember { mutableStateOf("") }; var loginErr by remember { mutableStateOf("") }
    fun load() { if (hvToken.isEmpty()) return; loading = true; scope.launch { vms = api.listHvVms(hvUrl, hvToken); cts = api.listHvContainers(hvUrl, hvToken); metrics = api.getHvMetrics(hvUrl, hvToken); loading = false } }
    LaunchedEffect(hvToken) { load() }
    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("Hypervisor") }, actions = { IconButton(onClick = { load() }) { Icon(Icons.Default.Refresh, "Refresh") } })
        if (hvToken.isEmpty()) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Connect to Hypervisor", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(value = loginUrl, onValueChange = { loginUrl = it }, label = { Text("Hypervisor URL") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(value = loginUser, onValueChange = { loginUser = it }, label = { Text("Username") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(value = loginPass, onValueChange = { loginPass = it }, label = { Text("Password") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                if (loginErr.isNotEmpty()) Text(loginErr, color = MaterialTheme.colorScheme.error)
                Button(onClick = { scope.launch { runCatching { api.getHvToken(loginUrl, loginUser, loginPass) }.onSuccess { tok -> prefs.saveHvCredentials(loginUrl, tok); load() }.onFailure { loginErr = it.message ?: "Login failed" } } }, Modifier.fillMaxWidth()) { Text("Connect") }
            }
        } else {
            metrics?.let { m -> Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("CPU" to "${m.cpu.toInt()}%", "MEM" to "${m.mem.toInt()}%", "DISK" to "${m.disk.toInt()}%").forEach { (l, v) -> Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceVariant) { Column(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) { Text(l, style = MaterialTheme.typography.labelSmall); Text(v, style = MaterialTheme.typography.bodyMedium) } } } } }
            LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (vms.isNotEmpty()) { item { Text("Virtual Machines", style = MaterialTheme.typography.labelMedium) }; items(vms) { vm -> Card(Modifier.fillMaxWidth()) { Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(vm.name, style = MaterialTheme.typography.titleSmall); Text("${vm.status} • ${vm.vcpus}vCPU", style = MaterialTheme.typography.bodySmall) }; Row { if (vm.status == "running") { IconButton(onClick = { scope.launch { api.hvVmAction(hvUrl, hvToken, vm.id, "stop"); load() } }) { Icon(Icons.Default.Stop, "Stop") } } else { IconButton(onClick = { scope.launch { api.hvVmAction(hvUrl, hvToken, vm.id, "start"); load() } }) { Icon(Icons.Default.PlayArrow, "Start", tint = MaterialTheme.colorScheme.primary) } } } } } } }
                if (cts.isNotEmpty()) { item { Text("Containers", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 8.dp)) }; items(cts) { ct -> Card(Modifier.fillMaxWidth()) { Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(ct.name, style = MaterialTheme.typography.titleSmall); Text(ct.status, style = MaterialTheme.typography.bodySmall) }; Row { if (ct.status == "running") { IconButton(onClick = { scope.launch { api.hvContainerAction(hvUrl, hvToken, ct.name, "stop"); load() } }) { Icon(Icons.Default.Stop, "Stop") } } else { IconButton(onClick = { scope.launch { api.hvContainerAction(hvUrl, hvToken, ct.name, "start"); load() } }) { Icon(Icons.Default.PlayArrow, "Start", tint = MaterialTheme.colorScheme.primary) } } } } } } }
            }
        }
    }
}
