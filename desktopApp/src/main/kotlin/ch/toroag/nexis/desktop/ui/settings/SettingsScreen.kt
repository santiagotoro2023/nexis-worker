package ch.toroag.nexis.desktop.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import ch.toroag.nexis.desktop.ui.theme.*
import ch.toroag.nexis.desktop.util.DesktopUpdateChecker

@Composable
fun SettingsScreen(
    vm:       SettingsViewModel,
    onLogout: () -> Unit,
) {
    val baseUrl       by vm.baseUrl.collectAsState()
    val certPin       by vm.certPin.collectAsState()
    val status        by vm.status.collectAsState()
    val health        by vm.health.collectAsState()
    val healthLoading by vm.healthLoading.collectAsState()
    val updateState   by vm.updateState.collectAsState()
    val haConfig      by vm.haConfig.collectAsState()
    val haTestResult  by vm.haTestResult.collectAsState()
    val haTestLoading by vm.haTestLoading.collectAsState()

    var reAuthPw by remember { mutableStateOf("") }

    var haUrlInput        by remember(haConfig) { mutableStateOf(haConfig?.url      ?: "") }
    var haUsernameInput   by remember(haConfig) { mutableStateOf(haConfig?.username  ?: "") }
    var haPasswordInput   by remember(haConfig) { mutableStateOf(haConfig?.password  ?: "") }
    var haPasswordVisible by remember { mutableStateOf(false) }
    var haMainInput       by remember(haConfig) { mutableStateOf(haConfig?.mainSwitch     ?: "switch.homelab_main_switch") }
    var haCompInput    by remember(haConfig) { mutableStateOf(haConfig?.computerSwitch ?: "switch.homelab_computer_switch") }

    LaunchedEffect(status) {
        if (status != null) {
            kotlinx.coroutines.delay(3000)
            vm.clearStatus()
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(NxBg)
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("SETTINGS", fontFamily = FontFamily.Monospace, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.2.sp, color = NxFg2)
        Spacer(Modifier.height(8.dp))

        // Controller URL
        SettingsSection("controller") {
            Text(baseUrl.ifEmpty { "not configured" },
                 fontFamily = FontFamily.Monospace, fontSize = 12.sp,
                 color = if (baseUrl.isEmpty()) NxFg2 else NxFg)
        }

        // Health
        SettingsSection("controller health") {
            if (healthLoading) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(14.dp), color = NxOrange, strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("checking...", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = NxFg2)
                }
            } else if (health == null) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("unreachable", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = NxFg2)
                    IconButton(onClick = { vm.refreshHealth() }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Refresh, "Retry", tint = NxFg2, modifier = Modifier.size(16.dp))
                    }
                }
            } else {
                val h = health!!
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        HealthRow("model",    h.modelLabel)
                        HealthRow("voice",    if (h.voice) "on  [${h.voiceModel}]" else "off")
                        HealthRow("memories", h.memories.toString())
                        HealthRow("sessions", h.sessions.toString())
                        HealthRow("context",  "${h.histLen} messages")
                        HealthRow("uptime",   formatUptime(h.uptimeSeconds))
                    }
                    IconButton(onClick = { vm.refreshHealth() }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Refresh, "Refresh", tint = NxFg2, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

        // Certificate
        SettingsSection("certificate") {
            if (certPin != null) {
                Text("pinned — connection trusted", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = NxOrange)
                Spacer(Modifier.height(4.dp))
                Text(certPin!!,
                     fontFamily = FontFamily.Monospace, fontSize = 10.sp,
                     color = NxFg2)
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick  = { vm.forgetCertificate() },
                    modifier = Modifier.fillMaxWidth(),
                    shape    = RoundedCornerShape(4.dp),
                    colors   = ButtonDefaults.outlinedButtonColors(contentColor = NxFg2),
                    border   = androidx.compose.foundation.BorderStroke(1.dp, NxBorder),
                ) { Text("forget certificate") }
                Spacer(Modifier.height(4.dp))
                Text("use this if the server cert was regenerated — next connection will re-pair automatically.",
                     fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = NxFg2)
            } else {
                Text("no certificate pinned — will pin on next connection.",
                     fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = NxFg2)
            }
        }

        // Home Assistant
        val fieldColors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = NxOrangeDim, unfocusedBorderColor = NxBorder,
            focusedTextColor = NxFg, unfocusedTextColor = NxFg, cursorColor = NxOrange,
            focusedContainerColor = NxBg2,
            unfocusedContainerColor = NxBg2,
        )
        SettingsSection("home assistant") {
            Text("credentials for local Home Assistant instance.",
                 fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = NxFg2)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(haUrlInput, { haUrlInput = it },
                label = { Text("HA URL (e.g. http://192.168.1.61:8123)", color = NxFg2) },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
                shape = RoundedCornerShape(4.dp), colors = fieldColors,
                textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = NxFg))
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(haUsernameInput, { haUsernameInput = it },
                label = { Text("username", color = NxFg2) },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
                shape = RoundedCornerShape(4.dp), colors = fieldColors,
                textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = NxFg))
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(haPasswordInput, { haPasswordInput = it },
                label = { Text("password", color = NxFg2) },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
                shape = RoundedCornerShape(4.dp), colors = fieldColors,
                textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = NxFg),
                visualTransformation = if (haPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { haPasswordVisible = !haPasswordVisible }, modifier = Modifier.size(24.dp)) {
                        Icon(if (haPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                             null, Modifier.size(16.dp), tint = NxFg2)
                    }
                })
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(haMainInput, { haMainInput = it },
                label = { Text("main switch entity ID", color = NxFg2) },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
                shape = RoundedCornerShape(4.dp), colors = fieldColors,
                textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = NxFg))
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(haCompInput, { haCompInput = it },
                label = { Text("computer switch entity ID", color = NxFg2) },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
                shape = RoundedCornerShape(4.dp), colors = fieldColors,
                textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = NxFg))
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick  = { vm.testHaConnection() },
                    modifier = Modifier.weight(1f).height(40.dp),
                    shape    = RoundedCornerShape(4.dp),
                    enabled  = !haTestLoading,
                    colors   = ButtonDefaults.outlinedButtonColors(contentColor = NxFg),
                    border   = androidx.compose.foundation.BorderStroke(1.dp, NxBorder),
                ) {
                    if (haTestLoading) CircularProgressIndicator(Modifier.size(14.dp), color = NxOrange, strokeWidth = 2.dp)
                    else Text("test connection", fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                }
                Button(
                    onClick = {
                        vm.saveHaConfig(haUrlInput, haUsernameInput, haPasswordInput, haMainInput, haCompInput)
                    },
                    modifier = Modifier.weight(1f).height(40.dp),
                    shape    = RoundedCornerShape(4.dp),
                    colors   = ButtonDefaults.buttonColors(containerColor = NxOrangeDim,
                        contentColor = NxBg),
                ) { Text("save", fontFamily = FontFamily.Monospace, fontSize = 11.sp) }
            }
            if (haTestResult != null) {
                Spacer(Modifier.height(4.dp))
                Text(haTestResult!!,
                     fontFamily = FontFamily.Monospace, fontSize = 10.sp,
                     color = if (haTestResult!!.startsWith("✓")) NxOrange else NxRed)
            }
        }

        // Re-authenticate
        SettingsSection("re-authenticate") {
            Text("use this if you changed your nexis password.",
                 fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = NxFg2)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value         = reAuthPw,
                onValueChange = { reAuthPw = it },
                label         = { Text("new password", color = NxFg2) },
                modifier      = Modifier.fillMaxWidth(),
                singleLine    = true,
                shape         = RoundedCornerShape(4.dp),
                colors        = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = NxOrangeDim, unfocusedBorderColor = NxBorder,
                    focusedTextColor = NxFg, unfocusedTextColor = NxFg, cursorColor = NxOrange,
                    focusedContainerColor = NxBg2,
                    unfocusedContainerColor = NxBg2,
                ),
                textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = NxFg),
                visualTransformation = PasswordVisualTransformation(),
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick  = { vm.reAuthenticate(reAuthPw) { reAuthPw = "" } },
                modifier = Modifier.fillMaxWidth(),
                enabled  = reAuthPw.isNotBlank(),
                shape    = RoundedCornerShape(4.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = NxOrangeDim,
                                                       contentColor = NxBg),
            ) { Text("re-authenticate") }
        }

        if (status != null) {
            Text(status!!, color = NxOrange, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
        }

        // App update
        SettingsSection("app update") {
            UpdateSection(updateState, vm::checkForUpdate, vm::downloadAndInstall, vm::dismissUpdate)
        }

        Spacer(Modifier.height(16.dp))

        OutlinedButton(
            onClick  = { vm.logout(onLogout) },
            modifier = Modifier.fillMaxWidth(),
            shape    = RoundedCornerShape(4.dp),
            colors   = ButtonDefaults.outlinedButtonColors(contentColor = NxRed),
            border   = androidx.compose.foundation.BorderStroke(1.dp, NxRed.copy(alpha = 0.5f)),
        ) { Text("disconnect") }
    }
}

@Composable
private fun UpdateSection(
    state:             UpdateState,
    onCheck:           () -> Unit,
    onInstall:         (DesktopUpdateChecker.Release) -> Unit,
    onDismiss:         () -> Unit,
) {
    when (state) {
        is UpdateState.Idle -> {
            Button(
                onClick  = onCheck,
                modifier = Modifier.fillMaxWidth(),
                shape    = RoundedCornerShape(4.dp),
                colors   = ButtonDefaults.buttonColors(
                    containerColor = NxOrangeDim,
                    contentColor   = NxBg,
                ),
            ) { Text("check for updates") }
        }
        is UpdateState.Checking -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(14.dp), color = NxOrange, strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text("checking...", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = NxFg2)
            }
        }
        is UpdateState.UpToDate -> {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Text("up to date", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = NxFg2)
                TextButton(onClick = onDismiss) { Text("dismiss", color = NxFg2) }
            }
        }
        is UpdateState.Available -> {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("update available: ${state.release.tag}",
                     fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = NxOrange)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick  = { onInstall(state.release) },
                        modifier = Modifier.weight(1f),
                        shape    = RoundedCornerShape(4.dp),
                        colors   = ButtonDefaults.buttonColors(
                            containerColor = NxOrangeDim,
                            contentColor   = NxBg,
                        ),
                    ) { Text("download & install") }
                    OutlinedButton(
                        onClick  = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape    = RoundedCornerShape(4.dp),
                        colors   = ButtonDefaults.outlinedButtonColors(contentColor = NxFg2),
                        border   = androidx.compose.foundation.BorderStroke(1.dp, NxBorder),
                    ) { Text("later") }
                }
            }
        }
        is UpdateState.Downloading -> {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("downloading... ${state.progress}%",
                     fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = NxFg2)
                LinearProgressIndicator(
                    progress        = { state.progress / 100f },
                    modifier        = Modifier.fillMaxWidth(),
                    color           = NxOrange,
                    trackColor      = NxBorder,
                )
            }
        }
        is UpdateState.Installing -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(14.dp), color = NxOrange, strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text("installing — approve the system dialog...",
                     fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = NxFg2)
            }
        }
        is UpdateState.Done -> {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Text("installed — restart to apply update",
                     fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = NxOrange)
                TextButton(onClick = onDismiss) { Text("ok", color = NxFg2) }
            }
        }
        is UpdateState.Error -> {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(state.msg, fontFamily = FontFamily.Monospace, fontSize = 11.sp,
                     color = NxRed)
                TextButton(onClick = onDismiss) { Text("dismiss", color = NxFg2) }
            }
        }
    }
}

@Composable
private fun SettingsSection(label: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Text(label, fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = NxOrange,
             modifier = Modifier.padding(bottom = 6.dp))
        HorizontalDivider(color = NxBorder, thickness = 0.5.dp)
        Spacer(Modifier.height(8.dp))
        content()
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun HealthRow(label: String, value: String) {
    Row {
        Text("$label  ", fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = NxFg2)
        Text(value, fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = NxFg)
    }
}

private fun formatUptime(seconds: Int): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return when {
        h > 0  -> "${h}h ${m}m"
        m > 0  -> "${m}m ${s}s"
        else   -> "${s}s"
    }
}
