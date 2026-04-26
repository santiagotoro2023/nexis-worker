package ch.toroag.nexis.worker.ui.hypervisor

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import ch.toroag.nexis.worker.data.NexisApiService
import ch.toroag.nexis.worker.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HypervisorScreen(
    onBack: () -> Unit,
    vm:     HypervisorViewModel = viewModel(),
) {
    val vms        by vm.vms.collectAsState()
    val containers by vm.containers.collectAsState()
    val metrics    by vm.metrics.collectAsState()
    val cmdResult  by vm.cmdResult.collectAsState()
    val error      by vm.error.collectAsState()
    val loading    by vm.loading.collectAsState()
    val configured by vm.configured.collectAsState()

    Scaffold(
        containerColor = NxBg,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("HYPERVISOR NODE", style = MaterialTheme.typography.titleSmall,
                            color = NxOrange, fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp)
                        Text("NX-HV · VIRTUAL INFRASTRUCTURE", style = MaterialTheme.typography.labelSmall,
                            color = NxFg2, letterSpacing = 1.sp)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = NxFg)
                    }
                },
                actions = {
                    if (configured) {
                        IconButton(onClick = { vm.refresh() }) {
                            Icon(Icons.Default.Refresh, contentDescription = null, tint = NxFg2)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = NxBg2),
            )
        }
    ) { pad ->
        if (!configured) {
            ConnectPanel(
                modifier = Modifier.padding(pad),
                onConnect = { url, user, pw -> vm.connect(url, user, pw, onSuccess = {}, onError = {}) }
            )
        } else {
            Column(
                modifier = Modifier
                    .padding(pad)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                if (loading) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                        color = NxOrange,
                        trackColor = NxBg3,
                    )
                }
                if (error.isNotEmpty()) {
                    NxAlertBanner(error)
                    Spacer(Modifier.height(12.dp))
                }
                metrics?.let { MetricsCard(it) }
                Spacer(Modifier.height(12.dp))
                VmListCard(vms, onAction = { id, action -> vm.vmAction(id, action) })
                Spacer(Modifier.height(12.dp))
                ContainerListCard(containers, onAction = { name, action -> vm.containerAction(name, action) })
                Spacer(Modifier.height(12.dp))
                CommandCard(cmdResult, onSend = { vm.sendCommand(it) })
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = { vm.disconnect() },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = NxFg2),
                ) {
                    Text("DISCONNECT NODE", letterSpacing = 1.5.sp,
                        style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun ConnectPanel(
    modifier: Modifier = Modifier,
    onConnect: (String, String, String) -> Unit,
) {
    var url      by remember { mutableStateOf("https://") }
    var username by remember { mutableStateOf("") }
    var pw       by remember { mutableStateOf("") }
    var err      by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Default.Dns, contentDescription = null, tint = NxOrange,
            modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(16.dp))
        Text("HYPERVISOR NODE", color = NxOrange, fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp, style = MaterialTheme.typography.titleSmall)
        Text("No node connected. Enter the hypervisor address and credentials.",
            color = NxFg2, style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(vertical = 8.dp))
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = url, onValueChange = { url = it },
            label = { Text("Node URL", color = NxFg2) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = nxTextFieldColors(),
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = username, onValueChange = { username = it },
            label = { Text("Username", color = NxFg2) },
            placeholder = { Text("creator", color = NxFg2) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = nxTextFieldColors(),
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = pw, onValueChange = { pw = it },
            label = { Text("Password", color = NxFg2) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            colors = nxTextFieldColors(),
        )
        if (err.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text(err, color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.labelSmall)
        }
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = {
                if (url.isBlank() || username.isBlank() || pw.isBlank()) {
                    err = "URL, username and password are required"; return@Button
                }
                err = ""
                onConnect(url.trim(), username.trim(), pw)
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = NxOrange, contentColor = NxBg),
        ) {
            Text("CONNECT", letterSpacing = 1.5.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun MetricsCard(m: NexisApiService.HvMetrics) {
    NxCard(title = "NODE STATUS") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            MetricPill("CPU", "${m.cpu.toInt()}%")
            MetricPill("MEM", "${m.mem.toInt()}%")
            MetricPill("DISK", "${m.disk.toInt()}%")
            MetricPill("VMs", "${m.vmsActive}/${m.vmsTotal}")
            MetricPill("CTs", "${m.ctsActive}/${m.ctsTotal}")
        }
    }
}

@Composable
private fun MetricPill(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = NxOrange, fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleMedium)
        Text(label, color = NxFg2, style = MaterialTheme.typography.labelSmall,
            letterSpacing = 1.sp)
    }
}

@Composable
private fun VmListCard(
    vms: List<NexisApiService.HvVm>,
    onAction: (String, String) -> Unit,
) {
    NxCard(title = "VIRTUAL INSTANCES") {
        if (vms.isEmpty()) {
            Text("No instances provisioned.", color = NxFg2,
                style = MaterialTheme.typography.bodySmall)
        } else {
            vms.forEach { vm ->
                HvItemRow(
                    name    = vm.name,
                    status  = vm.status,
                    detail  = "${vm.vcpus} vCPU · ${vm.memoryMb / 1024} GiB",
                    running = vm.status == "running",
                    onStart  = { onAction(vm.id, "start") },
                    onStop   = { onAction(vm.id, "stop") },
                    onReboot = { onAction(vm.id, "reboot") },
                )
            }
        }
    }
}

@Composable
private fun ContainerListCard(
    containers: List<NexisApiService.HvContainer>,
    onAction: (String, String) -> Unit,
) {
    NxCard(title = "CONTAINERS") {
        if (containers.isEmpty()) {
            Text("No containers provisioned.", color = NxFg2,
                style = MaterialTheme.typography.bodySmall)
        } else {
            containers.forEach { ct ->
                HvItemRow(
                    name    = ct.name,
                    status  = ct.status,
                    detail  = "${ct.cpus} CPU · ${ct.memoryMb} MiB",
                    running = ct.status == "running",
                    onStart  = { onAction(ct.name, "start") },
                    onStop   = { onAction(ct.name, "stop") },
                    onReboot = { onAction(ct.name, "restart") },
                )
            }
        }
    }
}

@Composable
private fun HvItemRow(
    name: String, status: String, detail: String, running: Boolean,
    onStart: () -> Unit, onStop: () -> Unit, onReboot: () -> Unit,
) {
    Column(modifier = Modifier.padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                color = if (running) NxGreen.copy(alpha = .15f) else NxBg3,
                shape = MaterialTheme.shapes.small,
            ) {
                Text(
                    if (running) "ACTIVE" else status.uppercase(),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    color = if (running) NxGreen else NxFg2,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(name, color = NxFg, style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold)
                Text(detail, color = NxFg2, style = MaterialTheme.typography.labelSmall)
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!running) {
                OutlinedButton(
                    onClick = onStart, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = NxOrange),
                ) { Text("START", style = MaterialTheme.typography.labelSmall, letterSpacing = 1.sp) }
            } else {
                OutlinedButton(
                    onClick = onStop, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = NxFg2),
                ) { Text("STOP", style = MaterialTheme.typography.labelSmall, letterSpacing = 1.sp) }
                OutlinedButton(
                    onClick = onReboot, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = NxFg2),
                ) { Text("REBOOT", style = MaterialTheme.typography.labelSmall, letterSpacing = 1.sp) }
            }
        }
        HorizontalDivider(modifier = Modifier.padding(top = 8.dp), color = NxBorder)
    }
}

@Composable
private fun CommandCard(result: String, onSend: (String) -> Unit) {
    var cmd by remember { mutableStateOf("") }
    NxCard(title = "COMMAND RELAY") {
        Text("Relay natural language commands directly to the hypervisor node.",
            color = NxFg2, style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(bottom = 8.dp))
        OutlinedTextField(
            value = cmd, onValueChange = { cmd = it },
            label = { Text("Command", color = NxFg2) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = nxTextFieldColors(),
        )
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = { onSend(cmd); cmd = "" },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = NxOrange, contentColor = NxBg),
        ) { Text("EXECUTE", letterSpacing = 1.5.sp, fontWeight = FontWeight.Bold) }
        if (result.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Surface(color = NxBg3, shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth()) {
                Text(result, modifier = Modifier.padding(10.dp),
                    color = NxFg2, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun NxCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(color = NxBg3, shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(title, color = NxOrange, fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelSmall,
                letterSpacing = 1.5.sp, modifier = Modifier.padding(bottom = 10.dp))
            content()
        }
    }
}

@Composable
private fun NxAlertBanner(msg: String) {
    Surface(color = MaterialTheme.colorScheme.errorContainer,
        shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth()) {
        Text(msg, modifier = Modifier.padding(10.dp),
            color = MaterialTheme.colorScheme.onErrorContainer,
            style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun nxTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor   = NxOrange,
    unfocusedBorderColor = NxBorder,
    focusedLabelColor    = NxOrange,
    unfocusedLabelColor  = NxFg2,
    cursorColor          = NxOrange,
    focusedTextColor     = NxFg,
    unfocusedTextColor   = NxFg,
)
