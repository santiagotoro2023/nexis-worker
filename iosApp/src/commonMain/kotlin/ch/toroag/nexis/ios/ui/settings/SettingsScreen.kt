package ch.toroag.nexis.ios.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ch.toroag.nexis.ios.data.NexisApiService
import ch.toroag.nexis.ios.data.PreferencesRepository
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(prefs: PreferencesRepository, onLogout: () -> Unit) {
    val scope   = rememberCoroutineScope()
    val api     = remember { NexisApiService() }
    val baseUrl by prefs.baseUrl.collectAsState()
    val token   by prefs.token.collectAsState()
    val username by prefs.username.collectAsState()
    val role     by prefs.role.collectAsState()
    var models   by remember { mutableStateOf(listOf<NexisApiService.ModelInfo>()) }
    var health   by remember { mutableStateOf<NexisApiService.HealthInfo?>(null) }
    var loading  by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        models  = api.getModels(baseUrl, token)
        health  = api.getHealth(baseUrl, token)
        loading = false
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title   = { Text("Settings") },
            actions = {
                IconButton(onClick = onLogout) { Icon(Icons.Default.Logout, "Logout") }
            },
        )
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Account", style = MaterialTheme.typography.titleSmall)
                    Text("User: $username", style = MaterialTheme.typography.bodyMedium)
                    Text("Role: $role",     style = MaterialTheme.typography.bodyMedium)
                    Text("Server: $baseUrl", style = MaterialTheme.typography.bodySmall,
                         color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                }
            }
            health?.let { h ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Controller Status", style = MaterialTheme.typography.titleSmall)
                        Text("Model: ${h.modelLabel}",   style = MaterialTheme.typography.bodyMedium)
                        Text("Voice: ${h.voiceModel}",   style = MaterialTheme.typography.bodyMedium)
                        Text("Memories: ${h.memories}",  style = MaterialTheme.typography.bodyMedium)
                        Text("History: ${h.histLen} messages", style = MaterialTheme.typography.bodyMedium)
                        val hrs = h.uptimeSeconds / 3600
                        val min = (h.uptimeSeconds % 3600) / 60
                        Text("Uptime: ${hrs}h ${min}m", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            if (models.isNotEmpty()) {
                Text("AI Models", style = MaterialTheme.typography.titleSmall)
                models.forEach { m ->
                    Card(Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(m.label, style = MaterialTheme.typography.bodyMedium)
                                Text(m.desc,  style = MaterialTheme.typography.bodySmall,
                                     color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                            }
                            if (m.current) {
                                Badge { Text("active") }
                            } else if (m.installed) {
                                OutlinedButton(onClick = {
                                    scope.launch { api.setModel(baseUrl, token, m.key) }
                                }) { Text("Use") }
                            }
                        }
                    }
                }
            }
        }
    }
}
