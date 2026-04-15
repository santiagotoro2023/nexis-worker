package ch.toroag.nexis.desktop.ui.settings

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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ch.toroag.nexis.desktop.ui.theme.*

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

    var reAuthPw by remember { mutableStateOf("") }

    LaunchedEffect(status) {
        if (status != null) {
            kotlinx.coroutines.delay(3000)
            vm.clearStatus()
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("settings", style = MaterialTheme.typography.titleMedium, color = NxFg)
        Spacer(Modifier.height(8.dp))

        // Controller URL
        SettingsSection("controller") {
            Text(baseUrl.ifEmpty { "not configured" },
                 style = MaterialTheme.typography.bodyMedium,
                 color = if (baseUrl.isEmpty()) NxFg2 else NxFg)
        }

        // Health
        SettingsSection("controller health") {
            if (healthLoading) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(14.dp), color = NxOrange, strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("checking...", style = MaterialTheme.typography.bodySmall, color = NxFg2)
                }
            } else if (health == null) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("unreachable", style = MaterialTheme.typography.bodySmall, color = NxFg2)
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
                Text("pinned — connection trusted", style = MaterialTheme.typography.bodySmall, color = NxOrange)
                Spacer(Modifier.height(4.dp))
                Text(certPin!!,
                     style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, fontSize = 10.sp),
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
                     style = MaterialTheme.typography.labelSmall, color = NxFg2)
            } else {
                Text("no certificate pinned — will pin on next connection.",
                     style = MaterialTheme.typography.bodySmall, color = NxFg2)
            }
        }

        // Re-authenticate
        SettingsSection("re-authenticate") {
            Text("use this if you changed your nexis password.",
                 style = MaterialTheme.typography.bodySmall, color = NxFg2)
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
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
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
                colors   = ButtonDefaults.buttonColors(containerColor = NxOrangeDim,
                                                       contentColor = MaterialTheme.colorScheme.background),
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
            colors   = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            border   = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
        ) { Text("disconnect") }
    }
}

@Composable
private fun SettingsSection(label: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = NxOrange,
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
