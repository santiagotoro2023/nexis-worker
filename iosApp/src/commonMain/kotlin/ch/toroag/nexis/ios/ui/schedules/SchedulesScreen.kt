package ch.toroag.nexis.ios.ui.schedules

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
fun SchedulesScreen(prefs: PreferencesRepository) {
    val scope = rememberCoroutineScope(); val api = remember { NexisApiService() }
    val baseUrl by prefs.baseUrl.collectAsState(); val token by prefs.token.collectAsState()
    var schedules by remember { mutableStateOf(listOf<NexisApiService.ScheduleEntry>()) }
    var loading by remember { mutableStateOf(false) }; var showAdd by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }; var newExpr by remember { mutableStateOf("") }; var newPrompt by remember { mutableStateOf("") }
    fun load() { loading = true; scope.launch { schedules = api.getSchedules(baseUrl, token); loading = false } }
    LaunchedEffect(Unit) { load() }
    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("Schedules") }, actions = { IconButton(onClick = { showAdd = !showAdd }) { Icon(Icons.Default.Add, "Add") }; IconButton(onClick = { load() }) { Icon(Icons.Default.Refresh, "Refresh") } })
        if (showAdd) {
            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = newName, onValueChange = { newName = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(value = newExpr, onValueChange = { newExpr = it }, label = { Text("Cron Expression") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(value = newPrompt, onValueChange = { newPrompt = it }, label = { Text("Prompt") }, modifier = Modifier.fillMaxWidth(), maxLines = 3)
                Button(onClick = { scope.launch { api.scheduleAction(baseUrl, token, "add", name = newName, expr = newExpr, prompt = newPrompt); newName = ""; newExpr = ""; newPrompt = ""; showAdd = false; load() } }, Modifier.fillMaxWidth()) { Text("Add Schedule") }
            }
            HorizontalDivider()
        }
        if (loading) Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        else LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(schedules, key = { it.id }) { sched ->
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(sched.name, style = MaterialTheme.typography.titleSmall)
                            Text(sched.expr, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                            Text(sched.prompt, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Switch(checked = sched.active, onCheckedChange = { on -> scope.launch { api.scheduleAction(baseUrl, token, "update", id = sched.id, active = on); load() } })
                            IconButton(onClick = { scope.launch { api.scheduleAction(baseUrl, token, "delete", id = sched.id); load() } }) { Icon(Icons.Default.Delete, "Delete", tint = MaterialTheme.colorScheme.error) }
                        }
                    }
                }
            }
        }
    }
}
