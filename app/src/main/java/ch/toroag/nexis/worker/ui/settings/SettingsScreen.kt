package ch.toroag.nexis.worker.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import ch.toroag.nexis.worker.ui.theme.*
import ch.toroag.nexis.worker.util.UpdateChecker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(
    onBack:                 () -> Unit,
    onLogout:               () -> Unit,
    onNavigateToMemories:   () -> Unit = {},
    onNavigateToHistory:    () -> Unit = {},
    onNavigateToSchedules:  () -> Unit = {},
    onNavigateToDevices:    () -> Unit = {},
    onNavigateToHypervisor: () -> Unit = {},
    vm: SettingsViewModel = viewModel(),
) {
    val baseUrl       by vm.baseUrl.collectAsState()
    val certPin       by vm.certPin.collectAsState()
    val status        by vm.status.collectAsState()
    val health        by vm.health.collectAsState()
    val healthLoading by vm.healthLoading.collectAsState()
    val haConfig      by vm.haConfig.collectAsState()
    val haTestResult  by vm.haTestResult.collectAsState()
    val haTestLoading by vm.haTestLoading.collectAsState()

    val context = LocalContext.current
    val scope   = rememberCoroutineScope()

    var updateState    by remember { mutableStateOf<String?>(null) }
    var updateChecking by remember { mutableStateOf(false) }
    var updateRelease  by remember { mutableStateOf<UpdateChecker.Release?>(null) }
    var reAuthPw       by remember { mutableStateOf("") }

    var haUrlInput        by remember(haConfig) { mutableStateOf(haConfig?.url      ?: "") }
    var haUsernameInput   by remember(haConfig) { mutableStateOf(haConfig?.username  ?: "") }
    var haPasswordInput   by remember(haConfig) { mutableStateOf(haConfig?.password  ?: "") }
    var haPasswordVisible by remember { mutableStateOf(false) }
    var haMainInput       by remember(haConfig) { mutableStateOf(haConfig?.mainSwitch     ?: "switch.homelab_main_switch") }
    var haCompInput       by remember(haConfig) { mutableStateOf(haConfig?.computerSwitch ?: "switch.homelab_computer_switch") }

    LaunchedEffect(status) {
        if (status != null) { kotlinx.coroutines.delay(3000); vm.clearStatus() }
    }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedContainerColor   = NxBg2,
        unfocusedContainerColor = NxBg2,
        focusedBorderColor      = NxOrangeDim,
        unfocusedBorderColor    = NxBorder,
        focusedTextColor        = NxFg,
        unfocusedTextColor      = NxFg,
        cursorColor             = NxOrange,
    )

    Column(Modifier.fillMaxSize().background(NxBg).systemBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(32.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = NxFg2, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(8.dp))
            Text("SETTINGS", fontFamily = FontFamily.Monospace, fontSize = 10.sp,
                 fontWeight = FontWeight.Bold, letterSpacing = 0.2.sp, color = NxFg2)
        }
        HorizontalDivider(color = NxBorder, thickness = 1.dp)

        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Controller
            NxCard("CONTROLLER") {
                Text(baseUrl.ifEmpty { "not configured" }, fontFamily = FontFamily.Monospace,
                     fontSize = 12.sp, color = if (baseUrl.isEmpty()) NxFg2 else NxFg)
            }

            // Health
            NxCard("CONTROLLER HEALTH") {
                if (healthLoading) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(14.dp), color = NxOrange, strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("checking…", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = NxFg2)
                    }
                } else if (health == null) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically) {
                        Text("UNREACHABLE", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = NxFg2)
                        IconButton(onClick = { vm.refreshHealth() }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Refresh, null, tint = NxFg2, modifier = Modifier.size(16.dp))
                        }
                    }
                } else {
                    val h = health!!
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            HealthRow("model",    h.modelLabel)
                            HealthRow("voice",    if (h.voice) "on [${h.voiceModel}]" else "off")
                            HealthRow("memories", h.memories.toString())
                            HealthRow("sessions", h.sessions.toString())
                            HealthRow("context",  "${h.histLen} messages")
                            HealthRow("uptime",   formatUptime(h.uptimeSeconds))
                        }
                        IconButton(onClick = { vm.refreshHealth() }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Refresh, null, tint = NxFg2, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            // Certificate
            NxCard("CERTIFICATE") {
                if (certPin != null) {
                    Text("PINNED — CONNECTION TRUSTED", fontFamily = FontFamily.Monospace,
                         fontSize = 10.sp, letterSpacing = 0.15.sp, color = NxOrange)
                    Spacer(Modifier.height(6.dp))
                    Text(certPin!!, fontFamily = FontFamily.Monospace, fontSize = 9.sp,
                         lineHeight = 14.sp, color = NxFg2)
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(
                        onClick  = { vm.forgetCertificate() },
                        modifier = Modifier.fillMaxWidth().height(40.dp),
                        shape    = RoundedCornerShape(8.dp),
                        colors   = ButtonDefaults.outlinedButtonColors(contentColor = NxFg2),
                        border   = androidx.compose.foundation.BorderStroke(1.dp, NxBorder),
                    ) { Text("FORGET CERTIFICATE", fontFamily = FontFamily.Monospace, fontSize = 10.sp) }
                    Spacer(Modifier.height(6.dp))
                    Text("Use this if the server cert was regenerated — next connection will re-pair automatically.",
                         fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = NxFg2, lineHeight = 15.sp)
                } else {
                    Text("No certificate pinned — will pin on next connection.",
                         fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = NxFg2)
                }
            }

            // Tools navigation
            NxCard("TOOLS") {
                NavRow("MEMORIES",            onNavigateToMemories)
                HorizontalDivider(color = NxBorder, thickness = 1.dp, modifier = Modifier.padding(vertical = 4.dp))
                NavRow("CONVERSATION HISTORY", onNavigateToHistory)
                HorizontalDivider(color = NxBorder, thickness = 1.dp, modifier = Modifier.padding(vertical = 4.dp))
                NavRow("SCHEDULES",            onNavigateToSchedules)
                HorizontalDivider(color = NxBorder, thickness = 1.dp, modifier = Modifier.padding(vertical = 4.dp))
                NavRow("DEVICE INVENTORY",     onNavigateToDevices)
                HorizontalDivider(color = NxBorder, thickness = 1.dp, modifier = Modifier.padding(vertical = 4.dp))
                NavRow("HYPERVISOR NODE",      onNavigateToHypervisor)
            }

            // Home Assistant
            NxCard("HOME ASSISTANT") {
                Text("Credentials for local Home Assistant instance.",
                     fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = NxFg2,
                     modifier = Modifier.padding(bottom = 10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    NxField("HA URL", haUrlInput, { haUrlInput = it }, fieldColors)
                    NxField("USERNAME", haUsernameInput, { haUsernameInput = it }, fieldColors)
                    OutlinedTextField(
                        value         = haPasswordInput,
                        onValueChange = { haPasswordInput = it },
                        modifier      = Modifier.fillMaxWidth(),
                        singleLine    = true,
                        shape         = RoundedCornerShape(10.dp),
                        label         = { Text("PASSWORD", fontFamily = FontFamily.Monospace, fontSize = 9.sp, color = NxFg2) },
                        colors        = fieldColors,
                        textStyle     = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = NxFg),
                        visualTransformation = if (haPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon  = {
                            IconButton(onClick = { haPasswordVisible = !haPasswordVisible }, modifier = Modifier.size(24.dp)) {
                                Icon(if (haPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                     null, Modifier.size(16.dp), tint = NxFg2)
                            }
                        },
                    )
                    NxField("MAIN SWITCH ENTITY", haMainInput, { haMainInput = it }, fieldColors)
                    NxField("COMPUTER SWITCH ENTITY", haCompInput, { haCompInput = it }, fieldColors)
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick  = { vm.testHaConnection() },
                        modifier = Modifier.weight(1f).height(40.dp),
                        shape    = RoundedCornerShape(8.dp),
                        enabled  = !haTestLoading,
                        colors   = ButtonDefaults.outlinedButtonColors(contentColor = NxFg),
                        border   = androidx.compose.foundation.BorderStroke(1.dp, NxBorder),
                    ) {
                        if (haTestLoading) CircularProgressIndicator(Modifier.size(14.dp), color = NxOrange, strokeWidth = 2.dp)
                        else Text("TEST", fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                    }
                    Button(
                        onClick  = { vm.saveHaConfig(haUrlInput, haUsernameInput, haPasswordInput, haMainInput, haCompInput) },
                        modifier = Modifier.weight(1f).height(40.dp),
                        shape    = RoundedCornerShape(8.dp),
                        colors   = ButtonDefaults.buttonColors(containerColor = NxOrange, contentColor = NxBg),
                    ) { Text("SAVE", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 10.sp) }
                }
                if (haTestResult != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(haTestResult!!, fontFamily = FontFamily.Monospace, fontSize = 10.sp,
                         color = if (haTestResult!!.startsWith("✓")) NxGreen else NxRed)
                }
            }

            // Re-authenticate
            NxCard("RE-AUTHENTICATE") {
                Text("Use this if you changed your Nexis password.",
                     fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = NxFg2,
                     modifier = Modifier.padding(bottom = 10.dp))
                OutlinedTextField(
                    value         = reAuthPw,
                    onValueChange = { reAuthPw = it },
                    modifier      = Modifier.fillMaxWidth(),
                    singleLine    = true,
                    shape         = RoundedCornerShape(10.dp),
                    label         = { Text("NEW PASSWORD", fontFamily = FontFamily.Monospace, fontSize = 9.sp, color = NxFg2) },
                    colors        = fieldColors,
                    textStyle     = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = NxFg),
                    visualTransformation = PasswordVisualTransformation(),
                )
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick  = { vm.reAuthenticate(reAuthPw) { reAuthPw = "" } },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    enabled  = reAuthPw.isNotBlank(),
                    shape    = RoundedCornerShape(12.dp),
                    colors   = ButtonDefaults.buttonColors(containerColor = NxOrange, contentColor = NxBg),
                ) {
                    Text("RE-AUTHENTICATE", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,
                         letterSpacing = 0.15.sp, fontSize = 11.sp)
                }
            }

            if (status != null) {
                Text(status!!, fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = NxOrange)
            }

            // App update
            NxCard("APP UPDATE") {
                Button(
                    onClick  = {
                        scope.launch {
                            updateChecking = true; updateState = null; updateRelease = null
                            val release = withContext(Dispatchers.IO) { UpdateChecker.checkForUpdate() }
                            updateRelease  = release
                            updateState    = if (release == null) "Up to date" else "Update available: ${release.tag}"
                            updateChecking = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    enabled  = !updateChecking,
                    shape    = RoundedCornerShape(12.dp),
                    colors   = ButtonDefaults.buttonColors(containerColor = NxOrangeDim, contentColor = NxBg),
                ) {
                    if (updateChecking) CircularProgressIndicator(Modifier.size(16.dp), color = NxBg, strokeWidth = 2.dp)
                    else Text("CHECK FOR UPDATE", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,
                              letterSpacing = 0.15.sp, fontSize = 11.sp)
                }
                if (updateState != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(updateState!!, fontFamily = FontFamily.Monospace, fontSize = 11.sp,
                         color = if (updateRelease != null) NxOrange else NxFg2)
                }
                if (updateRelease != null) {
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick  = {
                            val release = updateRelease ?: return@Button
                            scope.launch {
                                updateChecking = true; updateState = "Downloading…"
                                val apk = withContext(Dispatchers.IO) {
                                    UpdateChecker.downloadApk(context, release) { pct ->
                                        scope.launch(Dispatchers.Main) { updateState = "Downloading… $pct%" }
                                    }
                                }
                                if (apk != null) {
                                    updateState = "Installing…"
                                    withContext(Dispatchers.IO) { runCatching { UpdateChecker.installSilently(context, apk) } }
                                } else {
                                    updateState = "Download failed"
                                }
                                updateChecking = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        enabled  = !updateChecking,
                        shape    = RoundedCornerShape(12.dp),
                        colors   = ButtonDefaults.buttonColors(containerColor = NxOrange, contentColor = NxBg),
                    ) {
                        Text("DOWNLOAD & INSTALL", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,
                             letterSpacing = 0.15.sp, fontSize = 11.sp)
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            OutlinedButton(
                onClick  = { vm.logout(onLogout) },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape    = RoundedCornerShape(12.dp),
                colors   = ButtonDefaults.outlinedButtonColors(contentColor = NxRed),
                border   = androidx.compose.foundation.BorderStroke(1.dp, NxRed.copy(alpha = 0.4f)),
            ) {
                Text("DISCONNECT", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,
                     letterSpacing = 0.15.sp, fontSize = 11.sp)
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun NxCard(label: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(NxBg3, RoundedCornerShape(16.dp))
            .border(1.dp, NxBorder, RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Text(label, fontFamily = FontFamily.Monospace, fontSize = 9.sp,
             fontWeight = FontWeight.Bold, letterSpacing = 0.2.sp, color = NxFg2,
             modifier = Modifier.padding(bottom = 12.dp))
        content()
    }
}

@Composable
private fun NxField(
    label: String, value: String, onValueChange: (String) -> Unit,
    colors: TextFieldColors,
) {
    OutlinedTextField(
        value         = value,
        onValueChange = onValueChange,
        modifier      = Modifier.fillMaxWidth(),
        singleLine    = true,
        shape         = RoundedCornerShape(10.dp),
        label         = { Text(label, fontFamily = FontFamily.Monospace, fontSize = 9.sp, color = NxFg2) },
        colors        = colors,
        textStyle     = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = NxFg),
    )
}

@Composable
private fun NavRow(label: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically,
    ) {
        Text(label, fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = NxFg)
        Icon(Icons.Default.KeyboardArrowRight, null, tint = NxFg2, modifier = Modifier.size(18.dp))
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
    return when { h > 0 -> "${h}h ${m}m"; m > 0 -> "${m}m ${s}s"; else -> "${s}s" }
}
