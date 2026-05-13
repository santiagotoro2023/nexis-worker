@file:OptIn(ExperimentalMaterial3Api::class)

package ch.toroag.nexis.ios.ui.history

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ch.toroag.nexis.ios.data.NexisApiService
import ch.toroag.nexis.ios.data.PreferencesRepository
import kotlinx.coroutines.launch

@Composable
fun HistoryScreen(prefs: PreferencesRepository) {
    val scope = rememberCoroutineScope(); val api = remember { NexisApiService() }
    val baseUrl by prefs.baseUrl.collectAsState(); val token by prefs.token.collectAsState()
    var sessions by remember { mutableStateOf(listOf<NexisApiService.SessionSummary>()) }
    var loading  by remember { mutableStateOf(false) }
    fun load() { loading = true; scope.launch { sessions = api.getHistorySessions(baseUrl, token); loading = false } }
    LaunchedEffect(Unit) { load() }
    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("History") }, actions = { IconButton(onClick = { load() }) { Icon(Icons.Default.Refresh, "Refresh") } })
        if (loading) Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        else LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(sessions) { session ->
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(session.title.ifEmpty { session.sessionId }, style = MaterialTheme.typography.titleSmall)
                            Text(session.started, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        }
                        Row {
                            IconButton(onClick = { scope.launch { api.loadHistorySession(baseUrl, token, session.sessionId) } }) { Icon(Icons.Default.Restore, "Load") }
                            IconButton(onClick = { scope.launch { api.deleteHistorySession(baseUrl, token, session.sessionId); load() } }) { Icon(Icons.Default.Delete, "Delete", tint = MaterialTheme.colorScheme.error) }
                        }
                    }
                }
            }
        }
    }
}
