package ch.toroag.nexis.worker.ui.hypervisor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import ch.toroag.nexis.worker.data.NexisApiService
import ch.toroag.nexis.worker.ui.theme.*

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

    val filteredVms = if (selectedNode == null) vms else vms.filter { it.nodeId == selectedNode }
    val filteredCts = if (selectedNode == null) containers else containers.filter { it.nodeId == selectedNode }

    Column(Modifier.fillMaxSize().background(NxBg).systemBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(32.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = NxFg2, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text("HYPERVISORS", fontFamily = FontFamily.Monospace, fontSize = 10.sp,
                     fontWeight = FontWeight.Bold, letterSpacing = 0.2.sp, color = NxFg2)
                Text("${nodes.size} NODE${if (nodes.size != 1) "S" else ""} · ${vms.size} INSTANCE${if (vms.size != 1) "S" else ""}",
                     fontFamily = FontFamily.Monospace, fontSize = 9.sp, color = NxFg2.copy(alpha = 0.6f))
            }
            if (loading) {
                CircularProgressIndicator(Modifier.size(16.dp), color = NxOrange, strokeWidth = 2.dp)
            } else {
                IconButton(onClick = { vm.refresh() }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Refresh, null, tint = NxFg2, modifier = Modifier.size(18.dp))
                }
            }
        }
        HorizontalDivider(color = NxBorder, thickness = 1.dp)

        if (!connected) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Default.CloudOff, null, tint = NxFg2, modifier = Modifier.size(40.dp))
                    Text("NOT CONNECTED TO CONTROLLER", fontFamily = FontFamily.Monospace,
                         fontSize = 10.sp, letterSpacing = 0.15.sp, color = NxFg2)
                }
            }
            return@Column
        }

        if (nodes.isNotEmpty()) {
            ScrollableTabRow(
                selectedTabIndex = if (selectedNode == null) 0
                                   else nodes.indexOfFirst { it.id == selectedNode } + 1,
                containerColor = NxBg2,
                contentColor   = NxOrange,
                edgePadding    = 12.dp,
                indicator      = { tabPositions ->
                    val idx = if (selectedNode == null) 0 else nodes.indexOfFirst { it.id == selectedNode } + 1
                    if (idx in tabPositions.indices) {
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tabPositions[idx]),
                            color = NxOrange,
                        )
                    }
                },
            ) {
                Tab(selected = selectedNode == null, onClick = { selectedNode = null }) {
                    Text("ALL", fontFamily = FontFamily.Monospace, fontSize = 9.sp,
                         fontWeight = FontWeight.Bold, letterSpacing = 0.2.sp,
                         color = if (selectedNode == null) NxOrange else NxFg2,
                         modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp))
                }
                nodes.forEach { node ->
                    Tab(selected = selectedNode == node.id, onClick = { selectedNode = node.id }) {
                        Text(node.name.uppercase(), fontFamily = FontFamily.Monospace, fontSize = 9.sp,
                             fontWeight = FontWeight.Bold, letterSpacing = 0.2.sp,
                             color = if (selectedNode == node.id) NxOrange else NxFg2,
                             modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp))
                    }
                }
            }
            HorizontalDivider(color = NxBorder, thickness = 1.dp)
        }

        if (error.isNotEmpty()) {
            Text(error, fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = NxRed,
                 modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        }

        LazyColumn(
            contentPadding      = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (filteredVms.isEmpty() && filteredCts.isEmpty() && !loading) {
                item {
                    Box(Modifier.fillMaxWidth().padding(vertical = 40.dp), contentAlignment = Alignment.Center) {
                        Text("NO INSTANCES FOUND", fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = NxFg2)
                    }
                }
            }

            if (filteredVms.isNotEmpty()) {
                item {
                    Text("VIRTUAL MACHINES", fontFamily = FontFamily.Monospace, fontSize = 9.sp,
                         fontWeight = FontWeight.Bold, letterSpacing = 0.2.sp, color = NxFg2,
                         modifier = Modifier.padding(vertical = 4.dp))
                }
                items(filteredVms) { hvVm ->
                    HvVmCard(
                        vm          = hvVm,
                        onStart     = { vm.vmAction(hvVm.nodeId, hvVm.id, "start") },
                        onStop      = { vm.vmAction(hvVm.nodeId, hvVm.id, "stop") },
                        onReboot    = { vm.vmAction(hvVm.nodeId, hvVm.id, "reboot") },
                        onForceStop = { vm.vmAction(hvVm.nodeId, hvVm.id, "force-stop") },
                    )
                }
            }

            if (filteredCts.isNotEmpty()) {
                item {
                    Text("CONTAINERS", fontFamily = FontFamily.Monospace, fontSize = 9.sp,
                         fontWeight = FontWeight.Bold, letterSpacing = 0.2.sp, color = NxFg2,
                         modifier = Modifier.padding(vertical = 4.dp))
                }
                items(filteredCts) { ct ->
                    HvContainerCard(
                        ct        = ct,
                        onStart   = { vm.containerAction(ct.nodeId, ct.name, "start") },
                        onStop    = { vm.containerAction(ct.nodeId, ct.name, "stop") },
                        onRestart = { vm.containerAction(ct.nodeId, ct.name, "restart") },
                    )
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
        "paused"  -> NxOrangeDim
        else      -> NxFg2
    }
    Column(
        Modifier
            .fillMaxWidth()
            .background(NxBg3, RoundedCornerShape(12.dp))
            .border(1.dp, NxBorder, RoundedCornerShape(12.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .background(statusColor.copy(alpha = 0.12f), RoundedCornerShape(999.dp))
                    .border(1.dp, statusColor.copy(alpha = 0.25f), RoundedCornerShape(999.dp))
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(vm.status.uppercase(), fontFamily = FontFamily.Monospace, fontSize = 9.sp,
                     letterSpacing = 0.1.sp, color = statusColor)
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(vm.name, fontFamily = FontFamily.Monospace, fontSize = 13.sp,
                     fontWeight = FontWeight.Bold, color = NxFg)
                Text("${vm.vcpus} vCPU · ${vm.memoryMb / 1024} GiB · ${vm.nodeName}",
                     fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = NxFg2)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (!running) {
                Button(onClick = onStart, modifier = Modifier.height(32.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = NxGreen.copy(alpha = 0.15f), contentColor = NxGreen)) {
                    Text("START", fontFamily = FontFamily.Monospace, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            } else {
                OutlinedButton(onClick = onStop, modifier = Modifier.height(32.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = NxFg2),
                    border = androidx.compose.foundation.BorderStroke(1.dp, NxBorder)) {
                    Text("STOP", fontFamily = FontFamily.Monospace, fontSize = 9.sp)
                }
                OutlinedButton(onClick = onReboot, modifier = Modifier.height(32.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = NxFg2),
                    border = androidx.compose.foundation.BorderStroke(1.dp, NxBorder)) {
                    Text("REBOOT", fontFamily = FontFamily.Monospace, fontSize = 9.sp)
                }
                OutlinedButton(onClick = onForceStop, modifier = Modifier.height(32.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = NxRed),
                    border = androidx.compose.foundation.BorderStroke(1.dp, NxRed.copy(alpha = 0.4f))) {
                    Text("FORCE", fontFamily = FontFamily.Monospace, fontSize = 9.sp)
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
    Column(
        Modifier
            .fillMaxWidth()
            .background(NxBg3, RoundedCornerShape(12.dp))
            .border(1.dp, NxBorder, RoundedCornerShape(12.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .background(statusColor.copy(alpha = 0.12f), RoundedCornerShape(999.dp))
                    .border(1.dp, statusColor.copy(alpha = 0.25f), RoundedCornerShape(999.dp))
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(ct.status.uppercase(), fontFamily = FontFamily.Monospace, fontSize = 9.sp,
                     letterSpacing = 0.1.sp, color = statusColor)
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(ct.name, fontFamily = FontFamily.Monospace, fontSize = 13.sp,
                     fontWeight = FontWeight.Bold, color = NxFg)
                Text("${ct.cpus} CPU · ${ct.memoryMb} MiB · ${ct.nodeName}",
                     fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = NxFg2)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (!running) {
                Button(onClick = onStart, modifier = Modifier.height(32.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = NxGreen.copy(alpha = 0.15f), contentColor = NxGreen)) {
                    Text("START", fontFamily = FontFamily.Monospace, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            } else {
                OutlinedButton(onClick = onStop, modifier = Modifier.height(32.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = NxFg2),
                    border = androidx.compose.foundation.BorderStroke(1.dp, NxBorder)) {
                    Text("STOP", fontFamily = FontFamily.Monospace, fontSize = 9.sp)
                }
                OutlinedButton(onClick = onRestart, modifier = Modifier.height(32.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = NxFg2),
                    border = androidx.compose.foundation.BorderStroke(1.dp, NxBorder)) {
                    Text("RESTART", fontFamily = FontFamily.Monospace, fontSize = 9.sp)
                }
            }
        }
    }
}
