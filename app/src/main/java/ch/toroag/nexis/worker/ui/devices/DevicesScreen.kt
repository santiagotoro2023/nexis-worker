package ch.toroag.nexis.worker.ui.devices

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import ch.toroag.nexis.worker.data.NexisApiService
import ch.toroag.nexis.worker.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevicesScreen(
    onBack: () -> Unit,
    vm:     DevicesViewModel = viewModel(),
) {
    val devices      by vm.devices.collectAsState()
    val isLoading    by vm.isLoading.collectAsState()
    val probeOutput  by vm.probeOutput.collectAsState()
    val probeLoading by vm.probeLoading.collectAsState()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("device inventory", style = MaterialTheme.typography.titleMedium, color = NxFg) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = NxFg2)
                    }
                },
                actions = {
                    IconButton(onClick = { vm.loadDevices() }) {
                        Icon(Icons.Default.Refresh, "Refresh", tint = NxFg2)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { padding ->
        if (isLoading && devices.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = NxOrange)
            }
        } else {
            LazyColumn(
                Modifier.padding(padding).padding(16.dp).fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(devices) { dev ->
                    DeviceCard(
                        dev       = dev,
                        onSetRole = { role -> vm.setRole(dev.deviceId, role) },
                        onProbe   = { vm.probeDevice() },
                    )
                }
                if (devices.isEmpty()) {
                    item {
                        Text(
                            "no devices registered",
                            style = MaterialTheme.typography.bodySmall,
                            color = NxFg2,
                            modifier = Modifier.padding(top = 32.dp).fillMaxWidth(),
                        )
                    }
                }
                // Probe output panel
                if (probeOutput != null || probeLoading) {
                    item {
                        ProbePanel(
                            output  = probeOutput,
                            loading = probeLoading,
                            onDismiss = { vm.clearProbe() },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProbePanel(
    output:    String?,
    loading:   Boolean,
    onDismiss: () -> Unit,
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(4.dp),
        border   = androidx.compose.foundation.BorderStroke(0.5.dp, NxOrangeDim),
        colors   = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Text("probe output", style = MaterialTheme.typography.labelMedium, color = NxOrange)
                TextButton(onClick = onDismiss, contentPadding = PaddingValues(0.dp)) {
                    Text("dismiss", style = MaterialTheme.typography.labelSmall, color = NxFg2)
                }
            }
            if (loading) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), color = NxOrange, strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("probing…", style = MaterialTheme.typography.bodySmall, color = NxFg2)
                }
            } else if (output != null) {
                Text(
                    output,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace, fontSize = 10.sp, lineHeight = 15.sp,
                    ),
                    color = NxFg,
                )
            }
        }
    }
}

@Composable
private fun DeviceCard(
    dev:       NexisApiService.DeviceInfo,
    onSetRole: (String) -> Unit,
    onProbe:   () -> Unit,
) {
    val dotColor = if (dev.online) NxGreen else NxFg2

    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(4.dp),
        border   = androidx.compose.foundation.BorderStroke(0.5.dp, NxBorder),
        colors   = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            // Header row
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (dev.online) "●" else "○", color = dotColor,
                     style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.width(8.dp))
                Text(dev.hostname, style = MaterialTheme.typography.titleSmall, color = NxFg)
                Spacer(Modifier.weight(1f))
                val typeIcon = if (dev.deviceType == "mobile") Icons.Default.PhoneAndroid
                               else Icons.Default.Computer
                Icon(typeIcon, null, Modifier.size(16.dp), tint = NxFg2)
            }

            // OS / arch
            Text("${dev.os} · ${dev.arch}",
                 style = MaterialTheme.typography.labelSmall, color = NxFg2)

            // IP + last seen
            if (dev.ip.isNotEmpty()) {
                Text("ip: ${dev.ip}  ·  last seen: ${dev.lastSeen.take(16)}",
                     style = MaterialTheme.typography.labelSmall, color = NxFg2)
            }

            // Device ID (truncated)
            Text("id: ${dev.deviceId.take(8)}…",
                 style = MaterialTheme.typography.labelSmall, color = NxFg2)

            // Battery (mobile)
            if (dev.batteryPct != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val battIcon = when {
                        dev.charging == true          -> Icons.Default.BatteryChargingFull
                        (dev.batteryPct ?: 0) > 50    -> Icons.Default.BatteryFull
                        (dev.batteryPct ?: 0) > 20    -> Icons.Default.Battery3Bar
                        else                          -> Icons.Default.Battery1Bar
                    }
                    Icon(battIcon, null, Modifier.size(14.dp), tint = NxFg2)
                    Spacer(Modifier.width(4.dp))
                    Text("${dev.batteryPct}%${if (dev.charging == true) " charging" else ""}",
                         style = MaterialTheme.typography.labelSmall, color = NxFg2)
                }
            }

            // Role badge
            if (dev.role != null) {
                Text(dev.role, style = MaterialTheme.typography.labelSmall, color = NxOrange)
            }

            HorizontalDivider(color = NxBorder, thickness = 0.5.dp)

            // Action buttons
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                if (dev.deviceType == "desktop" && dev.role != "primary_pc") {
                    OutlinedButton(
                        onClick = { onSetRole("primary_pc") },
                        modifier = Modifier.height(32.dp),
                        shape = RoundedCornerShape(4.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = NxFg),
                        border = androidx.compose.foundation.BorderStroke(1.dp, NxBorder),
                    ) {
                        Text("set primary PC", style = MaterialTheme.typography.labelSmall)
                    }
                }
                if (dev.deviceType == "mobile" && dev.role != "primary_mobile") {
                    OutlinedButton(
                        onClick = { onSetRole("primary_mobile") },
                        modifier = Modifier.height(32.dp),
                        shape = RoundedCornerShape(4.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = NxFg),
                        border = androidx.compose.foundation.BorderStroke(1.dp, NxBorder),
                    ) {
                        Text("set primary mobile", style = MaterialTheme.typography.labelSmall)
                    }
                }
                if (dev.deviceType == "desktop") {
                    OutlinedButton(
                        onClick = onProbe,
                        modifier = Modifier.height(32.dp),
                        shape = RoundedCornerShape(4.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = NxOrange),
                        border = androidx.compose.foundation.BorderStroke(1.dp, NxOrangeDim),
                    ) {
                        Text("probe", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}
