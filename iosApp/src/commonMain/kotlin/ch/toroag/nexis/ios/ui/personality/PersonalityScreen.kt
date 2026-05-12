package ch.toroag.nexis.ios.ui.personality

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ch.toroag.nexis.ios.data.NexisApiService
import ch.toroag.nexis.ios.data.PreferencesRepository
import kotlinx.coroutines.launch

@Composable
fun PersonalityScreen(prefs: PreferencesRepository) {
    val scope   = rememberCoroutineScope()
    val api     = remember { NexisApiService() }
    val baseUrl by prefs.baseUrl.collectAsState()
    val token   by prefs.token.collectAsState()
    var name          by remember { mutableStateOf("") }
    var systemPrompt  by remember { mutableStateOf("") }
    var voiceEnabled  by remember { mutableStateOf(false) }
    var loading       by remember { mutableStateOf(true) }
    var saved         by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        api.getPersonality(baseUrl, token)?.let { p ->
            name         = p.name
            systemPrompt = p.systemPrompt
            voiceEnabled = p.voiceEnabled
        }
        loading = false
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title   = { Text("Personality") },
            actions = {
                IconButton(onClick = {
                    scope.launch {
                        api.savePersonality(baseUrl, token, name, systemPrompt, voiceEnabled)
                        saved = true
                    }
                }) { Icon(Icons.Default.Save, "Save") }
            },
        )
        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Column(Modifier.fillMaxWidth().padding(16.dp),
                   verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (saved) {
                    Text("Saved!", color = MaterialTheme.colorScheme.primary)
                }
                OutlinedTextField(
                    value = name, onValueChange = { name = it; saved = false },
                    label = { Text("Name") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                )
                OutlinedTextField(
                    value = systemPrompt, onValueChange = { systemPrompt = it; saved = false },
                    label = { Text("System Prompt") },
                    modifier = Modifier.fillMaxWidth().height(200.dp),
                    maxLines = 12,
                )
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Switch(checked = voiceEnabled,
                           onCheckedChange = { voiceEnabled = it; saved = false })
                    Spacer(Modifier.width(8.dp))
                    Text("Voice Enabled")
                }
            }
        }
    }
}
