package ch.toroag.nexis.worker.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import ch.toroag.nexis.worker.ui.theme.NxBorder
import ch.toroag.nexis.worker.ui.theme.NxFg
import ch.toroag.nexis.worker.ui.theme.NxFg2
import ch.toroag.nexis.worker.ui.theme.NxOrange
import ch.toroag.nexis.worker.ui.theme.NxOrangeDim

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack:           () -> Unit,
    onLogout:         () -> Unit,
    onNavigateToMemories:  () -> Unit = {},
    onNavigateToHistory:   () -> Unit = {},
    onNavigateToSchedules: () -> Unit = {},
    vm: SettingsViewModel = viewModel(),
) {
    val baseUrl       by vm.baseUrl.collectAsState()
    val certPin       by vm.certPin.collectAsState()
    val status        by vm.status.collectAsState()
    val health        by vm.health.collectAsState()
    val healthLoading by vm.healthLoading.collectAsState()
    val ntfyTopic     by vm.ntfyTopic.collectAsState(initial = "")

    var reAuthPw    by remember { mutableStateOf("") }
    var ntfyInput   by remember(ntfyTopic) { mutableStateOf(ntfyTopic) }

    LaunchedEffect(status) {
        if (status != null) {
            kotlinx.coroutines.delay(3000)
            vm.clearStatus()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text("settings", style = MaterialTheme.typography.titleMedium, color = NxFg)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = NxFg2)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {

            // Controller URL
            SettingsCard(label = "controller") {
                Text(
                    baseUrl.ifEmpty { "not configured" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (baseUrl.isEmpty()) NxFg2 else NxFg,
                )
            }

            // Controller health / dashboard
            SettingsCard(label = "controller health") {
                if (healthLoading) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier  = Modifier.size(14.dp),
                            color     = NxOrange,
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("checking...", style = MaterialTheme.typography.bodySmall, color = NxFg2)
                    }
                } else if (health == null) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment     = Alignment.CenterVertically,
                    ) {
                        Text("unreachable", style = MaterialTheme.typography.bodySmall, color = NxFg2)
                        IconButton(onClick = { vm.refreshHealth() }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Refresh, "Retry", tint = NxFg2, modifier = Modifier.size(16.dp))
                        }
                    }
                } else {
                    val h = health!!
                    val uptimeStr = formatUptime(h.uptimeSeconds)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            HealthRow("model",    h.modelLabel)
                            HealthRow("voice",    if (h.voice) "on  [${h.voiceModel}]" else "off")
                            HealthRow("memories", h.memories.toString())
                            HealthRow("sessions", h.sessions.toString())
                            HealthRow("context",  "${h.histLen} messages")
                            HealthRow("uptime",   uptimeStr)
                        }
                        IconButton(onClick = { vm.refreshHealth() }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Refresh, "Refresh", tint = NxFg2, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            // Certificate info
            SettingsCard(label = "certificate") {
                if (certPin != null) {
                    Text(
                        "pinned -connection trusted",
                        style = MaterialTheme.typography.bodySmall,
                        color = NxOrange,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        certPin!!,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize   = 10.sp,
                        ),
                        color = NxFg2,
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(
                        onClick  = { vm.forgetCertificate() },
                        modifier = Modifier.fillMaxWidth(),
                        shape    = RoundedCornerShape(4.dp),
                        colors   = ButtonDefaults.outlinedButtonColors(contentColor = NxFg2),
                        border   = androidx.compose.foundation.BorderStroke(1.dp, NxBorder),
                    ) { Text("forget certificate") }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "use this if the server cert was regenerated -next connection will re-pair automatically.",
                        style = MaterialTheme.typography.labelSmall,
                        color = NxFg2,
                    )
                } else {
                    Text(
                        "no certificate pinned -will pin on next connection.",
                        style = MaterialTheme.typography.bodySmall,
                        color = NxFg2,
                    )
                }
            }

            // Tools navigation
            SettingsCard(label = "tools") {
                NavRow("memories", onNavigateToMemories)
                Spacer(Modifier.height(8.dp))
                NavRow("conversation history", onNavigateToHistory)
                Spacer(Modifier.height(8.dp))
                NavRow("schedules", onNavigateToSchedules)
            }

            // Push notifications (ntfy)
            SettingsCard(label = "push notifications") {
                Text(
                    "get push notifications when scheduled tasks complete. " +
                    "install the ntfy app and subscribe to the same topic configured on the controller.",
                    style = MaterialTheme.typography.bodySmall,
                    color = NxFg2,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value         = ntfyInput,
                    onValueChange = { ntfyInput = it },
                    label         = { Text("ntfy topic (e.g. nexis-abc123)", color = NxFg2) },
                    modifier      = Modifier.fillMaxWidth(),
                    singleLine    = true,
                    shape         = RoundedCornerShape(4.dp),
                    colors        = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor      = NxOrangeDim,
                        unfocusedBorderColor    = NxBorder,
                        focusedTextColor        = NxFg,
                        unfocusedTextColor      = NxFg,
                        cursorColor             = NxOrange,
                        focusedContainerColor   = MaterialTheme.colorScheme.surfaceVariant,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
                    textStyle = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick  = { vm.saveNtfyTopic(ntfyInput) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled  = ntfyInput != ntfyTopic,
                    shape    = RoundedCornerShape(4.dp),
                    colors   = ButtonDefaults.buttonColors(
                        containerColor = NxOrangeDim,
                        contentColor   = MaterialTheme.colorScheme.background,
                    ),
                ) { Text("save") }
            }

            // Re-authenticate
            SettingsCard(label = "re-authenticate") {
                Text(
                    "use this if you changed your nexis password.",
                    style = MaterialTheme.typography.bodySmall,
                    color = NxFg2,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value         = reAuthPw,
                    onValueChange = { reAuthPw = it },
                    label         = { Text("new password", color = NxFg2) },
                    modifier      = Modifier.fillMaxWidth(),
                    singleLine    = true,
                    shape         = RoundedCornerShape(4.dp),
                    colors        = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor      = NxOrangeDim,
                        unfocusedBorderColor    = NxBorder,
                        focusedTextColor        = NxFg,
                        unfocusedTextColor      = NxFg,
                        cursorColor             = NxOrange,
                        focusedContainerColor   = MaterialTheme.colorScheme.surfaceVariant,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
                    textStyle = MaterialTheme.typography.bodyMedium,
                    visualTransformation = PasswordVisualTransformation(),
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick  = { vm.reAuthenticate(reAuthPw) { reAuthPw = "" } },
                    modifier = Modifier.fillMaxWidth(),
                    enabled  = reAuthPw.isNotBlank(),
                    shape    = RoundedCornerShape(4.dp),
                    colors   = ButtonDefaults.buttonColors(
                        containerColor = NxOrangeDim,
                        contentColor   = MaterialTheme.colorScheme.background,
                    ),
                ) { Text("re-authenticate") }
            }

            if (status != null) {
                Text(status!!, color = NxOrange, style = MaterialTheme.typography.bodySmall)
            }

            Spacer(Modifier.height(16.dp))

            OutlinedButton(
                onClick  = { vm.logout(onLogout) },
                modifier = Modifier.fillMaxWidth(),
                shape    = RoundedCornerShape(4.dp),
                colors   = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error),
                border   = androidx.compose.foundation.BorderStroke(
                    1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
            ) { Text("disconnect") }
        }
    }
}

@Composable
private fun NavRow(label: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = NxFg)
        Icon(
            Icons.Default.KeyboardArrowRight,
            contentDescription = null,
            tint     = NxFg2,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun SettingsCard(label: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(0.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = NxOrange,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        HorizontalDivider(color = NxBorder, thickness = 0.5.dp)
        Spacer(Modifier.height(8.dp))
        content()
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun HealthRow(label: String, value: String) {
    Row {
        Text("$label  ", style = MaterialTheme.typography.labelSmall, color = NxFg2)
        Text(value, style = MaterialTheme.typography.labelSmall, color = NxFg)
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
