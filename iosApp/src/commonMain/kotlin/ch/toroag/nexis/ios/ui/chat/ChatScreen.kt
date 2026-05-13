@file:OptIn(ExperimentalMaterial3Api::class)

package ch.toroag.nexis.ios.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import ch.toroag.nexis.ios.data.NexisApiService
import ch.toroag.nexis.ios.data.PreferencesRepository
import kotlinx.coroutines.launch

data class ChatMessage(val role: String, val content: String)

@Composable
fun ChatScreen(prefs: PreferencesRepository) {
    val scope   = rememberCoroutineScope()
    val api     = remember { NexisApiService() }
    val baseUrl by prefs.baseUrl.collectAsState()
    val token   by prefs.token.collectAsState()
    var messages by remember { mutableStateOf(listOf<ChatMessage>()) }
    var input    by remember { mutableStateOf("") }
    var typing   by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    LaunchedEffect(messages.size) { if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1) }
    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("Chat") }, actions = {
            IconButton(onClick = { scope.launch { api.clearConversation(baseUrl, token) }; messages = emptyList() }) { Icon(Icons.Default.DeleteSweep, "Clear") }
        })
        LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp), state = listState, verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
            items(messages) { msg ->
                val isUser = msg.role == "user"
                Row(Modifier.fillMaxWidth(), horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start) {
                    Box(Modifier.widthIn(max = 280.dp).clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = if (isUser) 16.dp else 4.dp, bottomEnd = if (isUser) 4.dp else 16.dp))
                        .background(if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant).padding(12.dp)) {
                        Text(msg.content, color = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
            if (typing) item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) { Box(Modifier.clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(12.dp)) { CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp) } } }
        }
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.Bottom) {
            OutlinedTextField(value = input, onValueChange = { input = it }, modifier = Modifier.weight(1f), placeholder = { Text("Message NeXiS...") }, maxLines = 4)
            Spacer(Modifier.width(8.dp))
            IconButton(onClick = {
                val msg = input.trim(); if (msg.isEmpty()) return@IconButton
                input = ""; typing = true; messages = messages + ChatMessage("user", msg); var reply = ""
                scope.launch { api.streamChat(baseUrl = baseUrl, token = token, msg = msg,
                    onToken = { chunk -> reply += chunk; messages = messages.dropLast(1) + ChatMessage("assistant", reply) },
                    onClear = { messages = emptyList(); reply = "" }, onDone = { typing = false }, onError = { typing = false }) }
            }, enabled = !typing && input.isNotBlank(), modifier = Modifier.size(48.dp)) {
                Icon(Icons.Default.Send, "Send", tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
