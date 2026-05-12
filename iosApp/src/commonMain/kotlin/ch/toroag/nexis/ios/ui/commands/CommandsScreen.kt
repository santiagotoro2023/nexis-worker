package ch.toroag.nexis.ios.ui.commands

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ch.toroag.nexis.ios.data.NexisApiService
import ch.toroag.nexis.ios.data.PreferencesRepository
import kotlinx.coroutines.launch

@Composable
fun CommandsScreen(prefs: PreferencesRepository) {
    val scope   = rememberCoroutineScope()
    val api     = remember { NexisApiService() }
    val baseUrl by prefs.baseUrl.collectAsState()
    val token   by prefs.token.collectAsState()
    var commands by remember { mutableStateOf(listOf<NexisApiService.CommandEntry>()) }
    var loading  by remember { mutableStateOf(false) }
    var result   by remember { mutableStateOf("") }

    fun load() {
        loading = true
        scope.launch {
            commands = api.getCommands(baseUrl, token)
            loading  = false
        }
    }

    LaunchedEffect(Unit) { load() }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title   = { Text("Commands") },
            actions = {
                IconButton(onClick = { load() }) { Icon(Icons.Default.Refresh, "Refresh") }
            },
        )
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
            Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = androidx.compose.ui.Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(12.dp),
                       verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(commands) { cmd ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(cmd.label, style = MaterialTheme.typography.titleSmall)
                                Text(cmd.cmd,   style = MaterialTheme.typography.bodySmall,
                                     color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                            }
                            IconButton(onClick = {
                                scope.launch {
                                    result = api.runCommand(baseUrl, token, cmd.cmd)
                                }
                            }) {
                                Icon(Icons.Default.PlayArrow, "Run",
                                     tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        }
    }
}
