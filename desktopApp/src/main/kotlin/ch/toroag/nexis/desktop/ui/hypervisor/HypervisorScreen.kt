package ch.toroag.nexis.desktop.ui.hypervisor

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ch.toroag.nexis.desktop.data.NexisApiService
import ch.toroag.nexis.desktop.ui.theme.*

@Composable
fun HypervisorScreen(vm: HypervisorViewModel) {
    if (!vm.configured) {
        ConnectPanel(onConnect = { url, pw ->
            vm.connect(url, pw, onSuccess = {}, onError = { vm.error = it })
        })
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()) {
                Column {
                    Text("HYPERVISOR NODE", color = NxOrange, fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall, letterSpacing = 2.sp)
                    Text("NX-HV · VIRTUAL INFRASTRUCTURE", color = NxFg2,
                        style = MaterialTheme.typography.labelSmall, letterSpacing = 1.sp)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { vm.refresh() },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = NxFg2)) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("REFRESH", style = MaterialTheme.typography.labelSmall, letterSpacing = 1.sp)
                    }
                    OutlinedButton(onClick = { vm.disconnect() },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = NxFg2)) {
                        Text("DISCONNECT", style = MaterialTheme.typography.labelSmall, letterSpacing = 1.sp)
                    }
                }
            }

            if (vm.loading) LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(), color = NxOrange, trackColor = NxBg3)

            if (vm.error.isNotEmpty()) {
                Surface(color = MaterialTheme.colorScheme.errorContainer,
                    shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth()) {
                    Text(vm.error, modifier = Modifier.padding(10.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall)
                }
            }

            vm.metrics?.let { MetricsCard(it) }
            VmListCard(vm.vms, onAction = { id, action -> vm.vmAction(id, action) })
            ContainerListCard(vm.containers, onAction = { name, action -> vm.containerAction(name, action) })
            CommandCard(vm.cmdResult, onSend = { vm.sendCommand(it) })
        }
    }
}

@Composable
private fun ConnectPanel(onConnect: (String, String) -> Unit) {
    var url by remember { mutableStateOf("https://") }
    var pw  by remember { mutableStateOf("") }
    var err by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxSize().padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Default.Dns, contentDescription = null, tint = NxOrange, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(16.dp))
        Text("HYPERVISOR NODE", color = NxOrange, fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleSmall, letterSpacing = 2.sp)
        Text("No node connected. Enter the hypervisor address and access code.",
            color = NxFg2, style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(vertical = 8.dp))
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(value = url, onValueChange = { url = it },
            label = { Text("Node URL", color = NxFg2) }, singleLine = true,
            modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth(),
            colors = nxFieldColors())
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(value = pw, onValueChange = { pw = it },
            label = { Text("Access Code", color = NxFg2) }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth(),
            colors = nxFieldColors())
        if (err.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text(err, color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.labelSmall)
        }
        Spacer(Modifier.height(12.dp))
        Button(onClick = {
            if (url.isBlank() || pw.isBlank()) { err = "URL and access code required"; return@Button }
            err = ""; onConnect(url.trim(), pw)
        }, modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = NxOrange, contentColor = NxBg)) {
            Text("CONNECT", letterSpacing = 1.5.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun MetricsCard(m: NexisApiService.HvMetrics) {
    NxCard("NODE STATUS") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            MetricPill("CPU",  "${m.cpu.toInt()}%")
            MetricPill("MEM",  "${m.mem.toInt()}%")
            MetricPill("DISK", "${m.disk.toInt()}%")
            MetricPill("VMs",  "${m.vmsActive}/${m.vmsTotal}")
            MetricPill("CTs",  "${m.ctsActive}/${m.ctsTotal}")
        }
    }
}

@Composable
private fun MetricPill(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = NxOrange, fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleMedium)
        Text(label, color = NxFg2, style = MaterialTheme.typography.labelSmall, letterSpacing = 1.sp)
    }
}

@Composable
private fun VmListCard(vms: List<NexisApiService.HvVm>, onAction: (String, String) -> Unit) {
    NxCard("VIRTUAL INSTANCES") {
        if (vms.isEmpty()) {
            Text("No instances provisioned.", color = NxFg2, style = MaterialTheme.typography.bodySmall)
        } else {
            vms.forEach { vm ->
                HvItemRow(name = vm.name, status = vm.status,
                    detail = "${vm.vcpus} vCPU · ${vm.memoryMb / 1024} GiB",
                    running = vm.status == "running",
                    onStart = { onAction(vm.id, "start") },
                    onStop  = { onAction(vm.id, "stop") },
                    onSecondary = { onAction(vm.id, "reboot") },
                    secondaryLabel = "REBOOT")
            }
        }
    }
}

@Composable
private fun ContainerListCard(cts: List<NexisApiService.HvContainer>, onAction: (String, String) -> Unit) {
    NxCard("CONTAINERS") {
        if (cts.isEmpty()) {
            Text("No containers provisioned.", color = NxFg2, style = MaterialTheme.typography.bodySmall)
        } else {
            cts.forEach { ct ->
                HvItemRow(name = ct.name, status = ct.status,
                    detail = "${ct.cpus} CPU · ${ct.memoryMb} MiB",
                    running = ct.status == "running",
                    onStart = { onAction(ct.name, "start") },
                    onStop  = { onAction(ct.name, "stop") },
                    onSecondary = { onAction(ct.name, "restart") },
                    secondaryLabel = "RESTART")
            }
        }
    }
}

@Composable
private fun HvItemRow(
    name: String, status: String, detail: String, running: Boolean,
    onStart: () -> Unit, onStop: () -> Unit,
    onSecondary: () -> Unit, secondaryLabel: String,
) {
    Column(modifier = Modifier.padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(color = if (running) NxGreen.copy(alpha = .15f) else NxBg3,
                shape = MaterialTheme.shapes.extraSmall) {
                Text(if (running) "ACTIVE" else status.uppercase(),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    color = if (running) NxGreen else NxFg2,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(name, color = NxFg, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                Text(detail, color = NxFg2, style = MaterialTheme.typography.labelSmall)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (!running) {
                    OutlinedButton(onClick = onStart, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = NxOrange)) {
                        Text("START", style = MaterialTheme.typography.labelSmall, letterSpacing = 1.sp)
                    }
                } else {
                    OutlinedButton(onClick = onSecondary, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = NxFg2)) {
                        Text(secondaryLabel, style = MaterialTheme.typography.labelSmall, letterSpacing = 1.sp)
                    }
                    OutlinedButton(onClick = onStop, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = NxFg2)) {
                        Text("STOP", style = MaterialTheme.typography.labelSmall, letterSpacing = 1.sp)
                    }
                }
            }
        }
        HorizontalDivider(modifier = Modifier.padding(top = 8.dp), color = NxBorder)
    }
}

@Composable
private fun CommandCard(result: String, onSend: (String) -> Unit) {
    var cmd by remember { mutableStateOf("") }
    NxCard("COMMAND RELAY") {
        Text("Relay natural language commands directly to the hypervisor node.",
            color = NxFg2, style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(bottom = 8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(value = cmd, onValueChange = { cmd = it },
                label = { Text("Command", color = NxFg2) }, singleLine = true,
                modifier = Modifier.weight(1f), colors = nxFieldColors())
            Button(onClick = { onSend(cmd); cmd = "" },
                modifier = Modifier.align(Alignment.CenterVertically),
                colors = ButtonDefaults.buttonColors(containerColor = NxOrange, contentColor = NxBg)) {
                Text("EXECUTE", letterSpacing = 1.sp, fontWeight = FontWeight.Bold)
            }
        }
        if (result.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Surface(color = NxBg3, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth()) {
                Text(result, modifier = Modifier.padding(10.dp), color = NxFg2,
                    style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun NxCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(color = NxBg3, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(title, color = NxOrange, fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelSmall, letterSpacing = 1.5.sp,
                modifier = Modifier.padding(bottom = 10.dp))
            content()
        }
    }
}

@Composable
private fun nxFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor   = NxOrange,
    unfocusedBorderColor = NxBorder,
    focusedLabelColor    = NxOrange,
    unfocusedLabelColor  = NxFg2,
    cursorColor          = NxOrange,
    focusedTextColor     = NxFg,
    unfocusedTextColor   = NxFg,
)
