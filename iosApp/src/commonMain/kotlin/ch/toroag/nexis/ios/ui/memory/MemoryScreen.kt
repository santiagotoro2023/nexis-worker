package ch.toroag.nexis.ios.ui.memory

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
fun MemoryScreen(prefs: PreferencesRepository) {
    val scope = rememberCoroutineScope(); val api = remember { NexisApiService() }
    val baseUrl by prefs.baseUrl.collectAsState(); val token by prefs.token.collectAsState()
    var memories by remember { mutableStateOf(listOf<NexisApiService.MemoryEntry>()) }
    var loading  by remember { mutableStateOf(false) }
    fun load() { loading = true; scope.launch { memories = api.getMemories(baseUrl, token); loading = false } }
    LaunchedEffect(Unit) { load() }
    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("Memories (${memories.size})") }, actions = { IconButton(onClick = { load() }) { Icon(Icons.Default.Refresh, "Refresh") } })
        if (loading) Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        else LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(memories, key = { it.id }) { mem ->
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                        Column(Modifier.weight(1f)) {
                            Text(mem.content, style = MaterialTheme.typography.bodyMedium)
                            if (mem.createdAt.isNotEmpty()) Text(mem.createdAt, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                        }
                        IconButton(onClick = { scope.launch { api.deleteMemory(baseUrl, token, mem.id); load() } }) { Icon(Icons.Default.Delete, "Delete", tint = MaterialTheme.colorScheme.error) }
                    }
                }
            }
        }
    }
}
