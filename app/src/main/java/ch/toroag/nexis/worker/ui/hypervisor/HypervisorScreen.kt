package ch.toroag.nexis.worker.ui.hypervisor

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import ch.toroag.nexis.worker.data.NexisApiService
import ch.toroag.nexis.worker.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HypervisorScreen(
    onBack: () -> Unit,
    vm: HypervisorViewModel = viewModel(),
) {
    val vms        by vm.vms.collectAsState()
    val containers by vm.containers.collectAsState()
    val nodes      by vm.nodes.collectAsState()
    val error      by vm.error.collectAsState()
    val loading    by vm.loading.collectAsState()
    val connected  by vm.connected.collectAsState()

    var selectedNode by remember { mutableStateOf<String?>(null) }

    val filteredVms = if (selectedNode == null) vms
                      else vms.filter { it.nodeId == selectedNode }
    val filteredCts = if (selectedNode == null) containers
                      else containers.filter { it.nodeId == selectedNode }

    Scaffold(
        containerColor = NxBg,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("HYPERVISORS", style = MaterialTheme.typography.titleSmall,
                            color = NxOrange, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                        val nodeCount = nodes.size
                        val vmCount   = vms.size
                        Text("$nodeCount NODE${if (nodeCount != 1) "S" else ""} · $vmCount INSTANCE${if (vmCount != 1) "S" else ""}",
                            style = MaterialTheme.typography.labelSmall, color = NxFg2, letterSpacing = 1.sp)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = NxFg)
                    }
                },
                actions = {
                    IconButton(onClick = { vm.refresh() }, enabled = !loading) {
                        if (loading) CircularProgressIndicator(Modifier.size(18.dp), color = NxOrange, strokeWidth = 2.dp)
                        else Icon(Icons.Default.Refresh, contentDescription = null, tint = NxFg2)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = NxBg2),
            )
        }
    ) { pad ->
        Column(Modifier.padding(pad)) {
            if (!connected) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.CloudOff, null, tint = NxFg2, modifier = Modifier.size(48.dp))
                        Spacer(Modifier.height(12.dp))
                        Text("Not connected to Controller", color = NxFg2,
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
                return@Scaffold
            }

            // Node filter row
            if (nodes.isNotEmpty()) {
                ScrollableTabRow(
                    selectedTabIndex = if (selectedNode == null) 0
                                       else nodes.indexOfFirst { it.id == selectedNode } + 1,
                    containerColor = NxBg2,
                    contentColor   = NxOrange,
                    edgePadding    = 8.dp,
                ) {
                    Tab(selected = selectedNode == null, onClick = { selectedNode = null },
                        text = { Text("ALL", style = MaterialTheme.typography.labelSmall, letterSpacing = 1.sp) })
                    nodes.forEach { node ->
                        Tab(selected = selectedNode == node.id, onClick = { selectedNode = node.id },
                            text = { Text(node.name.uppercase(), style = MaterialTheme.typography.labelSmall, letterSpacing = 1.sp) })
                    }
                }
            }

            if (error.isNotEmpty()) {
                Surface(color = MaterialTheme.colorScheme.errorContainer,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
                    Text(error, modifier = Modifier.padding(10.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall)
                }
            }

            LazyColumn(
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (filteredVms.isEmpty() && filteredCts.isEmpty() && !loading) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(vertical = 40.dp), contentAlignment = Alignment.Center) {
                            Text("No instances found.", color = NxFg2,
                                style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                if (filteredVms.isNotEmpty()) {
                    item {
                        Text("VIRTUAL MACHINES", color = NxFg2,
                            style = MaterialTheme.typography.labelSmall,
                            letterSpacing = 1.5.sp,
                            modifier = Modifier.padding(vertical = 4.dp))
                    }
                    items(filteredVms) { hvVm ->
                        HvVmCard(vm = hvVm,
                            onStart      = { vm.vmAction(hvVm.nodeId, hvVm.id, "start") },
                            onStop       = { vm.vmAction(hvVm.nodeId, hvVm.id, "stop") },
                            onReboot     = { vm.vmAction(hvVm.nodeId, hvVm.id, "reboot") },
                            onForceStop  = { vm.vmAction(hvVm.nodeId, hvVm.id, "force-stop") },
                        )
                    }
                }

                if (filteredCts.isNotEmpty()) {
                    item {
                        Text("CONTAINERS", color = NxFg2,
                            style = MaterialTheme.typography.labelSmall,
                            letterSpacing = 1.5.sp,
                            modifier = Modifier.padding(vertical = 4.dp))
                    }
                    items(filteredCts) { ct ->
                        HvContainerCard(ct = ct,
                            onStart  = { vm.containerAction(ct.nodeId, ct.name, "start") },
                            onStop   = { vm.containerAction(ct.nodeId, ct.name, "stop") },
                            onRestart = { vm.containerAction(ct.nodeId, ct.name, "restart") },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HvVmCard(
    vm: NexisApiService.HvVm,
    onStart: () -> Unit, onStop: () -> Unit,
    onReboot: () -> Unit, onForceStop: () -> Unit,
) {
    val running = vm.status == "running"
    val statusColor = when (vm.status) {
        "running" -> NxGreen
        "stopped" -> NxRed
        "paused"  -> NxYellow
        else      -> NxFg2
    }
    Surface(color = NxBg3, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(color = statusColor.copy(alpha = .15f), shape = MaterialTheme.shapes.extraSmall) {
                    Text(vm.status.uppercase(),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        color = statusColor, style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(vm.name, color = NxFg, style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold)
                    Text("${vm.vcpus} vCPU · ${vm.memoryMb / 1024} GiB · ${vm.nodeName}",
                        color = NxFg2, style = MaterialTheme.typography.labelSmall)
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (!running) {
                    OutlinedButton(onClick = onStart,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = NxGreen)) {
                        Text("START", style = MaterialTheme.typography.labelSmall, letterSpacing = 1.sp)
                    }
                } else {
                    OutlinedButton(onClick = onStop,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = NxFg2)) {
                        Text("STOP", style = MaterialTheme.typography.labelSmall, letterSpacing = 1.sp)
                    }
                    OutlinedButton(onClick = onReboot,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = NxFg2)) {
                        Text("REBOOT", style = MaterialTheme.typography.labelSmall, letterSpacing = 1.sp)
                    }
                    OutlinedButton(onClick = onForceStop,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = NxRed)) {
                        Text("FORCE STOP", style = MaterialTheme.typography.labelSmall, letterSpacing = 1.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun HvContainerCard(
    ct: NexisApiService.HvContainer,
    onStart: () -> Unit, onStop: () -> Unit, onRestart: () -> Unit,
) {
    val running = ct.status == "running"
    val statusColor = if (running) NxGreen else NxFg2
    Surface(color = NxBg3, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(color = statusColor.copy(alpha = .15f), shape = MaterialTheme.shapes.extraSmall) {
                    Text(ct.status.uppercase(),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        color = statusColor, style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(ct.name, color = NxFg, style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold)
                    Text("${ct.cpus} CPU · ${ct.memoryMb} MiB · ${ct.nodeName}",
                        color = NxFg2, style = MaterialTheme.typography.labelSmall)
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (!running) {
                    OutlinedButton(onClick = onStart,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = NxGreen)) {
                        Text("START", style = MaterialTheme.typography.labelSmall, letterSpacing = 1.sp)
                    }
                } else {
                    OutlinedButton(onClick = onStop,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = NxFg2)) {
                        Text("STOP", style = MaterialTheme.typography.labelSmall, letterSpacing = 1.sp)
                    }
                    OutlinedButton(onClick = onRestart,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = NxFg2)) {
                        Text("RESTART", style = MaterialTheme.typography.labelSmall, letterSpacing = 1.sp)
                    }
                }
            }
        }
    }
}
