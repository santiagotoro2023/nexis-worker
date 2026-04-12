package ch.toroag.nexis.worker.ui.chat

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import ch.toroag.nexis.worker.util.SpeechRecognizerHelper
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    onNavigateToSettings: () -> Unit,
    chatVm: ChatViewModel = viewModel(),
) {
    val context      = LocalContext.current
    val messages     by chatVm.messages.collectAsState()
    val isStreaming  by chatVm.isStreaming.collectAsState()
    val error        by chatVm.errorMessage.collectAsState()
    val models       by chatVm.models.collectAsState()
    val currentModel by chatVm.currentModel.collectAsState()
    val voiceEnabled by chatVm.voiceEnabled.collectAsState()

    var inputText       by remember { mutableStateOf("") }
    var showModelSheet  by remember { mutableStateOf(false) }
    var isMicListening  by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()
    val scope     = rememberCoroutineScope()
    val speech    = remember { SpeechRecognizerHelper(context) }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startListening(speech, chatVm) { isMicListening = it }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }
    DisposableEffect(Unit) { onDispose { speech.destroy() } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("NeXiS", style = MaterialTheme.typography.titleMedium)
                        if (currentModel.isNotEmpty())
                            Text(currentModel,
                                 style = MaterialTheme.typography.labelSmall,
                                 color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                actions = {
                    IconButton(onClick = { chatVm.toggleVoice(!voiceEnabled) }) {
                        Icon(
                            Icons.Default.Mic,
                            contentDescription = "Toggle voice",
                            tint = if (voiceEnabled) MaterialTheme.colorScheme.primary
                                   else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    TextButton(onClick = { showModelSheet = true }) {
                        Text("Model", style = MaterialTheme.typography.labelMedium)
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, "Settings")
                    }
                },
            )
        },
        bottomBar = {
            Column {
                if (error != null) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.errorContainer)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            error!!,
                            Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        IconButton(onClick = { chatVm.clearError() }) {
                            Icon(Icons.Default.Close, "Dismiss",
                                 tint = MaterialTheme.colorScheme.onErrorContainer)
                        }
                    }
                }
                Surface(shadowElevation = 4.dp) {
                    Row(
                        Modifier.padding(8.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedTextField(
                            value         = inputText,
                            onValueChange = { inputText = it },
                            placeholder   = { Text("Message NeXiS…") },
                            modifier      = Modifier.weight(1f),
                            shape         = RoundedCornerShape(24.dp),
                            maxLines      = 5,
                        )
                        Spacer(Modifier.width(6.dp))
                        // Mic button
                        IconButton(onClick = {
                            if (isMicListening) {
                                speech.stopListening(); isMicListening = false
                            } else {
                                val granted = ContextCompat.checkSelfPermission(
                                    context, Manifest.permission.RECORD_AUDIO
                                ) == PackageManager.PERMISSION_GRANTED
                                if (granted) startListening(speech, chatVm) { isMicListening = it }
                                else permLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        }) {
                            Icon(
                                Icons.Default.Mic,
                                "Voice input",
                                tint = if (isMicListening) MaterialTheme.colorScheme.primary
                                       else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        Spacer(Modifier.width(4.dp))
                        // Send / Stop button
                        IconButton(onClick = {
                            if (isStreaming) chatVm.abortStreaming()
                            else if (inputText.isNotBlank()) {
                                chatVm.sendMessage(inputText); inputText = ""
                            }
                        }) {
                            Icon(
                                if (isStreaming) Icons.Default.Stop else Icons.Default.Send,
                                if (isStreaming) "Stop" else "Send",
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
        },
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding).fillMaxSize(),
            state             = listState,
            contentPadding    = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(messages, key = { it.id }) { msg -> MessageBubble(msg) }
            if (isStreaming && messages.lastOrNull()?.content?.isEmpty() == true) {
                item { TypingIndicator() }
            }
        }
    }

    if (showModelSheet) {
        ModalBottomSheet(onDismissRequest = { showModelSheet = false }) {
            Text("Select model", Modifier.padding(16.dp),
                 style = MaterialTheme.typography.titleMedium)
            models.filter { it.installed }.forEach { m ->
                ListItem(
                    headlineContent   = { Text(m.label) },
                    supportingContent = { Text(m.desc) },
                    trailingContent   = {
                        if (m.current)
                            Text("Active",
                                 color = MaterialTheme.colorScheme.primary,
                                 style = MaterialTheme.typography.labelMedium)
                    },
                    modifier = Modifier.clickable {
                        chatVm.selectModel(m.key)
                        showModelSheet = false
                    },
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun MessageBubble(msg: ChatMessage) {
    val isUser = msg.role == "user"
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart    = 16.dp, topEnd = 16.dp,
                bottomStart = if (isUser) 16.dp else 4.dp,
                bottomEnd   = if (isUser) 4.dp  else 16.dp,
            ),
            color    = if (isUser) MaterialTheme.colorScheme.primaryContainer
                       else MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.widthIn(max = 320.dp),
        ) {
            Text(
                msg.content,
                Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = if (!isUser) FontFamily.Monospace else null,
                    fontSize   = 14.sp,
                ),
            )
        }
    }
}

@Composable
private fun TypingIndicator() {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        Surface(shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant) {
            Text("●  ●  ●", Modifier.padding(12.dp, 8.dp),
                 style = MaterialTheme.typography.bodySmall,
                 color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun startListening(
    speech:  SpeechRecognizerHelper,
    chatVm:  ChatViewModel,
    setFlag: (Boolean) -> Unit,
) {
    setFlag(true)
    speech.startListening(
        onResult = { text -> setFlag(false); chatVm.sendMessage(text) },
        onError  = { setFlag(false) },
    )
}
