package ch.toroag.nexis.ios.ui.remote

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import ch.toroag.nexis.ios.data.NexisApiService
import ch.toroag.nexis.ios.data.PreferencesRepository
import kotlinx.coroutines.launch

private data class RemoteAction(val label: String, val action: String, val icon: ImageVector)

private val remoteActions = listOf(
    RemoteAction("Screenshot", "screenshot",  Icons.Default.Screenshot),
    RemoteAction("Lock",       "lock",        Icons.Default.Lock),
    RemoteAction("Sleep",      "sleep",       Icons.Default.DarkMode),
    RemoteAction("Shutdown",   "shutdown",    Icons.Default.PowerSettingsNew),
    RemoteAction("Reboot",     "reboot",      Icons.Default.RestartAlt),
    RemoteAction("Mute",       "mute",        Icons.Default.VolumeOff),
    RemoteAction("Unmute",     "unmute",      Icons.Default.VolumeUp),
    RemoteAction("Vol +",      "volume_up",   Icons.Default.Add),
    RemoteAction("Vol -",      "volume_down", Icons.Default.Remove),
)

@Composable
fun RemoteScreen(prefs: PreferencesRepository) {
    val scope   = rememberCoroutineScope()
    val api     = remember { NexisApiService() }
    val baseUrl by prefs.baseUrl.collectAsState()
    val token   by prefs.token.collectAsState()
    var result  by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("Remote Control") })
        if (result.isNotEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                color    = MaterialTheme.colorScheme.surfaceVariant,
                shape    = MaterialTheme.shapes.medium,
            ) {
                Text(result, modifier = Modifier.padding(12.dp),
                     style = MaterialTheme.typography.bodySmall)
            }
        }
        if (loading) {
            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement   = Arrangement.spacedBy(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(remoteActions) { act ->
                ElevatedCard(
                    onClick = {
                        loading = true
                        scope.launch {
                            result  = api.desktopAction(baseUrl, token, act.action)
                            loading = false
                        }
                    },
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(act.icon, contentDescription = act.label,
                             tint = MaterialTheme.colorScheme.primary)
                        Text(act.label, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}
