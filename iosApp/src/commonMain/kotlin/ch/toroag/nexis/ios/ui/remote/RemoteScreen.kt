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

private data class RA(val label: String, val action: String, val icon: ImageVector)
private val actions = listOf(RA("Screenshot","screenshot",Icons.Default.Screenshot),RA("Lock","lock",Icons.Default.Lock),RA("Sleep","sleep",Icons.Default.DarkMode),RA("Shutdown","shutdown",Icons.Default.PowerSettingsNew),RA("Reboot","reboot",Icons.Default.RestartAlt),RA("Mute","mute",Icons.Default.VolumeOff),RA("Unmute","unmute",Icons.Default.VolumeUp),RA("Vol +","volume_up",Icons.Default.Add),RA("Vol -","volume_down",Icons.Default.Remove))

@Composable
fun RemoteScreen(prefs: PreferencesRepository) {
    val scope = rememberCoroutineScope(); val api = remember { NexisApiService() }
    val baseUrl by prefs.baseUrl.collectAsState(); val token by prefs.token.collectAsState()
    var result by remember { mutableStateOf("") }; var loading by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("Remote Control") })
        if (result.isNotEmpty()) Surface(Modifier.fillMaxWidth().padding(12.dp), color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium) { Text(result, Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall) }
        if (loading) Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        LazyVerticalGrid(columns = GridCells.Fixed(3), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(actions) { act ->
                ElevatedCard(onClick = { loading = true; scope.launch { result = api.desktopAction(baseUrl, token, act.action); loading = false } }) {
                    Column(Modifier.fillMaxWidth().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(act.icon, act.label, tint = MaterialTheme.colorScheme.primary)
                        Text(act.label, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}
