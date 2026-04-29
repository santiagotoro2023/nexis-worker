package ch.toroag.nexis.worker.ui.devices

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import ch.toroag.nexis.worker.data.NexisApiService
import ch.toroag.nexis.worker.ui.theme.*

@Composable
fun DevicesScreen(
    onBack: () -> Unit,
    vm:     DevicesViewModel = viewModel(),
) {
    val devices      by vm.devices.collectAsState()
    val isLoading    by vm.isLoading.collectAsState()
    val probeOutput  by vm.probeOutput.collectAsState()
    val probeLoading by vm.probeLoading.collectAsState()
    val passwords    by vm.passwords.collectAsState()

    LaunchedEffect(devices) {
        if (devices.isNotEmpty()) vm.loadPasswords(devices.map { it.deviceId })
    }

    Column(Modifier.fillMaxSize().background(NxBg).systemBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(32.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = NxFg2, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(8.dp))
            Text("DEVICE INVENTORY", fontFamily = FontFamily.Monospace, fontSize = 10.sp,
                 fontWeight = FontWeight.Bold, letterSpacing = 0.2.sp, color = NxFg2,
                 modifier = Modifier.weight(1f))
            IconButton(onClick = { vm.loadDevices() }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Refresh, null, tint = NxFg2, modifier = Modifier.size(18.dp))
            }
        }
        HorizontalDivider(color = NxBorder, thickness = 1.dp)

        if (isLoading && devices.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = NxOrange, strokeWidth = 2.dp)
            }
        } else {
            LazyColumn(
                contentPadding      = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(devices) { dev ->
                    DeviceCard(
                        dev           = dev,
                        savedPassword = passwords[dev.deviceId] ?: "",
                        onSetRole     = { role -> vm.setRole(dev.deviceId, role) },
                        onProbe       = { vm.probeDevice(dev) },
                        onSavePassword = { pw -> vm.saveDevicePassword(dev.deviceId, pw) },
                        onDelete      = { vm.deleteDevice(dev.deviceId) },
                    )
                }
                if (devices.isEmpty()) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(vertical = 40.dp), contentAlignment = Alignment.Center) {
                            Text("NO DEVICES REGISTERED", fontFamily = FontFamily.Monospace,
                                 fontSize = 12.sp, color = NxFg2)
                        }
                    }
                }
                if (probeOutput != null || probeLoading) {
                    item {
                        ProbePanel(output = probeOutput, loading = probeLoading, onDismiss = { vm.clearProbe() })
                    }
                }
            }
        }
    }
}

@Composable
private fun ProbePanel(output: String?, loading: Boolean, onDismiss: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(NxBg3, RoundedCornerShape(12.dp))
            .border(1.dp, NxOrangeDim, RoundedCornerShape(12.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            Text("PROBE OUTPUT", fontFamily = FontFamily.Monospace, fontSize = 9.sp,
                 fontWeight = FontWeight.Bold, letterSpacing = 0.15.sp, color = NxOrange)
            TextButton(onClick = onDismiss, contentPadding = PaddingValues(0.dp)) {
                Text("DISMISS", fontFamily = FontFamily.Monospace, fontSize = 9.sp, color = NxFg2)
            }
        }
        if (loading) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(14.dp), color = NxOrange, strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text("probing…", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = NxFg2)
            }
        } else if (output != null) {
            Text(output, fontFamily = FontFamily.Monospace, fontSize = 10.sp,
                 lineHeight = 15.sp, color = NxFg)
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
    var confirmDelete    by remember { mutableStateOf(false) }
    var passwordInput    by remember(savedPassword) { mutableStateOf(savedPassword) }
    var passwordVisible  by remember { mutableStateOf(false) }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedContainerColor   = NxBg2,
        unfocusedContainerColor = NxBg2,
        focusedBorderColor      = NxOrangeDim,
        unfocusedBorderColor    = NxBorder,
        focusedTextColor        = NxFg,
        unfocusedTextColor      = NxFg,
        cursorColor             = NxOrange,
    )

    if (confirmDelete) {
        Box(Modifier.fillMaxWidth().background(NxBg.copy(alpha = 0.9f)), contentAlignment = Alignment.Center) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(NxBg3, RoundedCornerShape(16.dp))
                    .border(1.dp, NxBorder, RoundedCornerShape(16.dp))
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("REMOVE DEVICE", fontFamily = FontFamily.Monospace, fontSize = 10.sp,
                     fontWeight = FontWeight.Bold, letterSpacing = 0.2.sp, color = NxRed)
                Text("Remove ${dev.hostname} from the device inventory?",
                     fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = NxFg2)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { confirmDelete = false },
                        modifier = Modifier.weight(1f).height(40.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = NxFg),
                        border = androidx.compose.foundation.BorderStroke(1.dp, NxBorder),
                    ) { Text("CANCEL", fontFamily = FontFamily.Monospace, fontSize = 10.sp) }
                    Button(
                        onClick = { confirmDelete = false; onDelete() },
                        modifier = Modifier.weight(1f).height(40.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = NxRed, contentColor = NxBg),
                    ) { Text("REMOVE", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 10.sp) }
                }
            }
        }
        return
    }

    val statusColor = if (dev.online) NxGreen else NxFg2

    Column(
        Modifier
            .fillMaxWidth()
            .background(NxBg3, RoundedCornerShape(16.dp))
            .border(1.dp, NxBorder, RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .background(statusColor.copy(alpha = 0.12f), RoundedCornerShape(999.dp))
                    .border(1.dp, statusColor.copy(alpha = 0.25f), RoundedCornerShape(999.dp))
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(if (dev.online) "ONLINE" else "OFFLINE",
                     fontFamily = FontFamily.Monospace, fontSize = 9.sp,
                     letterSpacing = 0.1.sp, color = statusColor)
            }
            Spacer(Modifier.width(8.dp))
            Text(dev.hostname, fontFamily = FontFamily.Monospace, fontSize = 13.sp,
                 fontWeight = FontWeight.Bold, color = NxFg, modifier = Modifier.weight(1f))
            Icon(
                if (dev.deviceType == "mobile") Icons.Default.PhoneAndroid else Icons.Default.Computer,
                null, Modifier.size(16.dp), tint = NxFg2
            )
            Spacer(Modifier.width(4.dp))
            IconButton(onClick = { confirmDelete = true }, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.Delete, null, Modifier.size(16.dp), tint = NxRed.copy(alpha = 0.7f))
            }
        }

        Text("${dev.os} · ${dev.arch}", fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = NxFg2)

        if (dev.ip.isNotEmpty()) {
            Text("IP ${dev.ip}  ·  ${dev.lastSeen.take(16)}",
                 fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = NxFg2)
        }

        Text("ID ${dev.deviceId.take(8)}…", fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = NxFg2)

        if (!dev.mac.isNullOrEmpty()) {
            Text("MAC ${dev.mac}", fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = NxFg2)
        }

        if (dev.batteryPct != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val battIcon = when {
                    dev.charging == true   -> Icons.Default.BatteryChargingFull
                    dev.batteryPct > 50    -> Icons.Default.BatteryFull
                    dev.batteryPct > 20    -> Icons.Default.Battery3Bar
                    else                   -> Icons.Default.Battery1Bar
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
            HorizontalDivider(color = NxBorder, thickness = 1.dp)
            Text("UNLOCK PASSWORD", fontFamily = FontFamily.Monospace, fontSize = 9.sp,
                 letterSpacing = 0.15.sp, color = NxFg2, modifier = Modifier.padding(bottom = 2.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value         = passwordInput,
                    onValueChange = { passwordInput = it },
                    modifier      = Modifier.weight(1f),
                    singleLine    = true,
                    shape         = RoundedCornerShape(10.dp),
                    placeholder   = { Text("leave blank if none", fontFamily = FontFamily.Monospace,
                                          fontSize = 12.sp, color = NxFg2.copy(alpha = 0.5f)) },
                    visualTransformation = if (passwordVisible) VisualTransformation.None
                                           else PasswordVisualTransformation(),
                    trailingIcon  = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }, modifier = Modifier.size(20.dp)) {
                            Icon(if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                 null, Modifier.size(14.dp), tint = NxFg2)
                        }
                    },
                    colors    = fieldColors,
                    textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = NxFg),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onSavePassword(passwordInput) }),
                )
                Button(
                    onClick  = { onSavePassword(passwordInput) },
                    modifier = Modifier.height(40.dp),
                    shape    = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    colors   = ButtonDefaults.buttonColors(containerColor = NxOrangeDim, contentColor = NxBg),
                ) {
                    Icon(Icons.Default.Save, null, Modifier.size(14.dp))
                }
            }
            if (savedPassword.isNotEmpty()) {
                Text("PASSWORD SAVED — USED AUTOMATICALLY FOR UNLOCK",
                     fontFamily = FontFamily.Monospace, fontSize = 9.sp, color = NxGreen)
            }
        }

        HorizontalDivider(color = NxBorder, thickness = 1.dp)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (dev.deviceType == "desktop" && dev.role != "primary_pc") {
                OutlinedButton(
                    onClick = { onSetRole("primary_pc") },
                    modifier = Modifier.height(32.dp),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = NxFg),
                    border = androidx.compose.foundation.BorderStroke(1.dp, NxBorder),
                ) { Text("SET PRIMARY PC", fontFamily = FontFamily.Monospace, fontSize = 9.sp) }
            }
            if (dev.deviceType == "mobile" && dev.role != "primary_mobile") {
                OutlinedButton(
                    onClick = { onSetRole("primary_mobile") },
                    modifier = Modifier.height(32.dp),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = NxFg),
                    border = androidx.compose.foundation.BorderStroke(1.dp, NxBorder),
                ) { Text("SET PRIMARY MOBILE", fontFamily = FontFamily.Monospace, fontSize = 9.sp) }
            }
            OutlinedButton(
                onClick = onProbe,
                modifier = Modifier.height(32.dp),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 10.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = NxOrange),
                border = androidx.compose.foundation.BorderStroke(1.dp, NxOrangeDim),
            ) { Text("PROBE", fontFamily = FontFamily.Monospace, fontSize = 9.sp) }
        }
    }
}
