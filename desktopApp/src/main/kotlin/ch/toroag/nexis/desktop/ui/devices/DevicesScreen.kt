package ch.toroag.nexis.desktop.ui.devices

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import ch.toroag.nexis.desktop.data.NexisApiService
import ch.toroag.nexis.desktop.ui.theme.*

@Composable
fun DevicesScreen(vm: DevicesViewModel) {
    val devices      by vm.devices.collectAsState()
    val isLoading    by vm.isLoading.collectAsState()
    val probeOutput  by vm.probeOutput.collectAsState()
    val probeLoading by vm.probeLoading.collectAsState()
    val passwords    by vm.passwords.collectAsState()

    LaunchedEffect(Unit) { vm.loadDevices() }

    Column(Modifier.fillMaxSize().background(NxBg).padding(24.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("device inventory", fontFamily = FontFamily.Monospace, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = NxFg,
                 modifier = Modifier.weight(1f))
            IconButton(onClick = { vm.loadDevices() }) {
                Icon(Icons.Default.Refresh, "Refresh", tint = NxFg2)
            }
        }
        Spacer(Modifier.height(16.dp))

        if (isLoading && devices.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = NxOrange)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(devices) { dev ->
                    DeviceCard(
                        dev            = dev,
                        savedPassword  = passwords[dev.deviceId] ?: "",
                        onSetRole      = { role -> vm.setRole(dev.deviceId, role) },
                        onProbe        = { vm.probeDevice(dev) },
                        onSavePassword = { pw -> vm.saveDevicePassword(dev.deviceId, pw) },
                        onDelete       = { vm.deleteDevice(dev.deviceId) },
                    )
                }
                if (devices.isEmpty()) {
                    item {
                        Text("no devices registered",
                             fontFamily = FontFamily.Monospace, fontSize = 11.sp,
                             color = NxFg2,
                             modifier = Modifier.padding(top = 32.dp))
                    }
                }
                if (probeOutput != null || probeLoading) {
                    item {
                        ProbePanel(output = probeOutput, loading = probeLoading,
                                   onDismiss = { vm.clearProbe() })
                    }
                }
            }
        }
    }
}

@Composable
private fun ProbePanel(output: String?, loading: Boolean, onDismiss: () -> Unit) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(4.dp),
        border   = androidx.compose.foundation.BorderStroke(0.5.dp, NxOrangeDim),
        colors   = CardDefaults.outlinedCardColors(containerColor = NxBg3),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Text("probe output", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = NxOrange)
                TextButton(onClick = onDismiss, contentPadding = PaddingValues(0.dp)) {
                    Text("dismiss", fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = NxFg2)
                }
            }
            if (loading) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(14.dp), color = NxOrange, strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("probing…", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = NxFg2)
                }
            } else if (output != null) {
                Text(output,
                     fontFamily = FontFamily.Monospace, fontSize = 10.sp,
                     color = NxFg)
            }
        }
    }
}

@Composable
private fun DeviceCard(
    dev:            NexisApiService.DeviceInfo,
    savedPassword:  String,
    onSetRole:      (String) -> Unit,
    onProbe:        () -> Unit,
    onSavePassword: (String) -> Unit,
    onDelete:       () -> Unit,
) {
    val dotColor = if (dev.online) NxGreen else NxFg2
    var passwordInput    by remember(savedPassword) { mutableStateOf(savedPassword) }
    var passwordVisible  by remember { mutableStateOf(false) }
    var confirmDelete    by remember { mutableStateOf(false) }

    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(4.dp),
        border   = androidx.compose.foundation.BorderStroke(0.5.dp, NxBorder),
        colors   = CardDefaults.outlinedCardColors(containerColor = NxBg3),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (dev.online) "●" else "○", color = dotColor, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                Spacer(Modifier.width(8.dp))
                Text(dev.hostname, fontFamily = FontFamily.Monospace, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = NxFg)
                Spacer(Modifier.weight(1f))
                Icon(if (dev.deviceType == "mobile") Icons.Default.PhoneAndroid else Icons.Default.Computer,
                     null, Modifier.size(16.dp), tint = NxFg2)
            }

            Text("${dev.os} · ${dev.arch}", fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = NxFg2)

            if (dev.ip.isNotEmpty()) {
                Text("ip: ${dev.ip}  ·  last seen: ${dev.lastSeen.take(16)}",
                     fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = NxFg2)
            }

            Text("id: ${dev.deviceId.take(8)}…", fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = NxFg2)

            if (!dev.mac.isNullOrEmpty()) {
                Text("mac: ${dev.mac}", fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = NxFg2)
            }

            if (dev.batteryPct != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val battIcon = when {
                        dev.charging == true  -> Icons.Default.BatteryChargingFull
                        dev.batteryPct > 50   -> Icons.Default.BatteryFull
                        dev.batteryPct > 20   -> Icons.Default.Battery3Bar
                        else                  -> Icons.Default.Battery1Bar
                    }
                    Icon(battIcon, null, Modifier.size(14.dp), tint = NxFg2)
                    Spacer(Modifier.width(4.dp))
                    Text("${dev.batteryPct}%${if (dev.charging == true) " charging" else ""}",
                         fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = NxFg2)
                }
            }

            if (dev.role != null) {
                Text(dev.role, fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = NxOrange)
            }

            if (dev.deviceType == "desktop") {
                HorizontalDivider(color = NxBorder, thickness = 0.5.dp)
                Text("unlock password", fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = NxFg2)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value         = passwordInput,
                        onValueChange = { passwordInput = it },
                        modifier      = Modifier.weight(1f),
                        singleLine    = true,
                        shape         = RoundedCornerShape(4.dp),
                        textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = NxFg),
                        placeholder   = { Text("leave blank if none", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = NxFg2.copy(alpha = 0.5f)) },
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }, modifier = Modifier.size(20.dp)) {
                                Icon(if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                     null, Modifier.size(14.dp), tint = NxFg2)
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NxOrangeDim, unfocusedBorderColor = NxBorder,
                            focusedTextColor = NxFg, unfocusedTextColor = NxFg, cursorColor = NxOrange,
                            focusedContainerColor = NxBg2,
                            unfocusedContainerColor = NxBg2,
                        ),
                    )
                    OutlinedButton(
                        onClick  = { onSavePassword(passwordInput) },
                        modifier = Modifier.height(40.dp),
                        shape    = RoundedCornerShape(4.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                        colors   = ButtonDefaults.outlinedButtonColors(contentColor = NxOrange),
                        border   = androidx.compose.foundation.BorderStroke(1.dp, NxOrangeDim),
                    ) {
                        Icon(Icons.Default.Save, null, Modifier.size(14.dp), tint = NxOrange)
                        Spacer(Modifier.width(4.dp))
                        Text("save", fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                    }
                }
                if (savedPassword.isNotEmpty()) {
                    Text("password saved — used automatically for unlock",
                         fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = NxGreen.copy(alpha = 0.8f))
                }
            }

            HorizontalDivider(color = NxBorder, thickness = 0.5.dp)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (dev.deviceType == "desktop" && dev.role != "primary_pc") {
                    OutlinedButton(
                        onClick = { onSetRole("primary_pc") },
                        modifier = Modifier.height(32.dp),
                        shape = RoundedCornerShape(4.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = NxFg),
                        border = androidx.compose.foundation.BorderStroke(1.dp, NxBorder),
                    ) { Text("set primary PC", fontFamily = FontFamily.Monospace, fontSize = 10.sp) }
                }
                if (dev.deviceType == "mobile" && dev.role != "primary_mobile") {
                    OutlinedButton(
                        onClick = { onSetRole("primary_mobile") },
                        modifier = Modifier.height(32.dp),
                        shape = RoundedCornerShape(4.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = NxFg),
                        border = androidx.compose.foundation.BorderStroke(1.dp, NxBorder),
                    ) { Text("set primary mobile", fontFamily = FontFamily.Monospace, fontSize = 10.sp) }
                }
                OutlinedButton(
                    onClick = onProbe,
                    modifier = Modifier.height(32.dp),
                    shape = RoundedCornerShape(4.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = NxOrange),
                    border = androidx.compose.foundation.BorderStroke(1.dp, NxOrangeDim),
                ) { Text("probe", fontFamily = FontFamily.Monospace, fontSize = 10.sp) }

                Spacer(Modifier.weight(1f))

                IconButton(
                    onClick  = { confirmDelete = true },
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(Icons.Default.Delete, "Remove device",
                         tint = NxRed.copy(alpha = 0.7f),
                         modifier = Modifier.size(16.dp))
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest  = { confirmDelete = false },
            containerColor    = NxBg2,
            titleContentColor = NxFg,
            textContentColor  = NxFg2,
            title   = { Text("remove device", fontFamily = FontFamily.Monospace, fontSize = 13.sp, fontWeight = FontWeight.Bold) },
            text    = { Text("Remove \"${dev.hostname}\" from the device list? It will re-appear if the app reconnects.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete() }) {
                    Text("remove", color = NxRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("cancel", color = NxFg2) }
            },
        )
    }
}
