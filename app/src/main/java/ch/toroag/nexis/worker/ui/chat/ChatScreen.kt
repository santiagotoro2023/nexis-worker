package ch.toroag.nexis.worker.ui.chat

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import ch.toroag.nexis.worker.R
import ch.toroag.nexis.worker.ui.theme.NxBorder
import ch.toroag.nexis.worker.ui.theme.NxBg3
import ch.toroag.nexis.worker.ui.theme.NxDim
import ch.toroag.nexis.worker.ui.theme.NxFg
import ch.toroag.nexis.worker.ui.theme.NxFg2
import ch.toroag.nexis.worker.ui.theme.NxOrange
import ch.toroag.nexis.worker.ui.theme.NxOrangeDim
import ch.toroag.nexis.worker.util.SoundFx
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

    var inputText      by remember { mutableStateOf("") }
    var showModelSheet by remember { mutableStateOf(false) }
    var isMicListening by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()
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
        containerColor      = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0),   // we control all insets manually
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor         = MaterialTheme.colorScheme.surface,
                    titleContentColor      = NxFg,
                    actionIconContentColor = NxFg2,
                ),
                windowInsets = WindowInsets.statusBars,
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter      = painterResource(R.drawable.ic_nexis_logo),
                            contentDescription = "NeXiS",
                            colorFilter  = ColorFilter.tint(NxOrange),
                            modifier     = Modifier.size(28.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(
                                "NeXiS",
                                style         = MaterialTheme.typography.titleMedium,
                                color         = NxOrange,
                                fontWeight    = FontWeight.Bold,
                                letterSpacing = 2.sp,
                            )
                            if (currentModel.isNotEmpty())
                                Text(currentModel,
                                     style = MaterialTheme.typography.labelSmall,
                                     color = NxFg2)
                        }
                    }
                },
                actions = {
                    // Speaker icon — toggles TTS voice output from controller
                    IconButton(onClick = { chatVm.toggleVoice(!voiceEnabled) }) {
                        Icon(
                            if (voiceEnabled) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                            contentDescription = "Toggle voice output",
                            tint = if (voiceEnabled) NxOrange else NxFg2,
                        )
                    }
                    TextButton(onClick = { showModelSheet = true }) {
                        Text("Model", style = MaterialTheme.typography.labelMedium, color = NxFg2)
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, "Settings", tint = NxFg2)
                    }
                },
            )
        },
        bottomBar = {
            // windowInsetsPadding union: when keyboard open = IME height, else = nav bar height
            Column(
                Modifier.windowInsetsPadding(
                    WindowInsets.ime.union(WindowInsets.navigationBars)
                )
            ) {
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
                Surface(
                    color          = MaterialTheme.colorScheme.surface,
                    shadowElevation = 0.dp,
                    tonalElevation = 0.dp,
                ) {
                    Row(
                        Modifier
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedTextField(
                            value         = inputText,
                            onValueChange = { inputText = it },
                            placeholder   = { Text("message nexis...", color = NxFg2) },
                            modifier      = Modifier.weight(1f),
                            shape         = RoundedCornerShape(4.dp),
                            maxLines      = 5,
                            colors        = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor      = NxOrangeDim,
                                unfocusedBorderColor    = NxBorder,
                                focusedTextColor        = NxFg,
                                unfocusedTextColor      = NxFg,
                                cursorColor             = NxOrange,
                                focusedContainerColor   = MaterialTheme.colorScheme.surfaceVariant,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            ),
                            textStyle = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(Modifier.width(4.dp))
                        // Mic button — push-to-talk voice input (phone-side STT)
                        IconButton(onClick = {
                            if (isMicListening) {
                                SoundFx.micDeactivate()
                                speech.stopListening()
                                isMicListening = false
                            } else {
                                val granted = ContextCompat.checkSelfPermission(
                                    context, Manifest.permission.RECORD_AUDIO
                                ) == PackageManager.PERMISSION_GRANTED
                                if (granted) {
                                    SoundFx.micActivate()
                                    startListening(speech, chatVm) { isMicListening = it }
                                } else {
                                    permLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            }
                        }) {
                            Icon(
                                Icons.Default.Mic,
                                "Voice input",
                                tint = if (isMicListening) NxOrange else NxFg2,
                            )
                        }
                        // Send / stop streaming
                        IconButton(onClick = {
                            if (isStreaming) {
                                chatVm.abortStreaming()
                                SoundFx.tap()
                            } else if (inputText.isNotBlank()) {
                                chatVm.sendMessage(inputText)
                                inputText = ""
                                SoundFx.send()
                            }
                        }) {
                            Icon(
                                if (isStreaming) Icons.Default.Stop else Icons.Default.Send,
                                if (isStreaming) "Stop" else "Send",
                                tint = if (isStreaming) MaterialTheme.colorScheme.error else NxOrange,
                            )
                        }
                    }
                }
            }
        },
    ) { padding ->
        LazyColumn(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            state               = listState,
            contentPadding      = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(messages, key = { it.id }) { msg -> MessageBubble(msg) }
            if (isStreaming && messages.lastOrNull()?.content?.isEmpty() == true) {
                item { TypingBubble() }
            }
        }
    }

    if (showModelSheet) {
        ModalBottomSheet(
            onDismissRequest = { showModelSheet = false },
            containerColor   = MaterialTheme.colorScheme.surface,
            contentColor     = NxFg,
        ) {
            Text(
                "select model",
                Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.labelMedium,
                color = NxFg2,
            )
            models.filter { it.installed }.forEach { m ->
                ListItem(
                    headlineContent   = {
                        Text(m.label,
                             color = if (m.current) NxOrange else NxFg,
                             style = MaterialTheme.typography.bodyMedium)
                    },
                    supportingContent = {
                        Text(m.desc, color = NxFg2, style = MaterialTheme.typography.labelSmall)
                    },
                    trailingContent   = {
                        if (m.current)
                            Text("active", color = NxOrange, style = MaterialTheme.typography.labelSmall)
                    },
                    colors   = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.clickable { chatVm.selectModel(m.key); showModelSheet = false },
                )
                HorizontalDivider(color = NxBorder, thickness = 0.5.dp)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

// ── Message bubble ─────────────────────────────────────────────────────────────

@Composable
private fun MessageBubble(msg: ChatMessage) {
    val isUser = msg.role == "user"
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        if (!isUser) {
            // AI messages: full width so code blocks have room
            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.Start,
            ) {
                Text(
                    "nexis",
                    style    = MaterialTheme.typography.labelSmall,
                    color    = NxOrange,
                    modifier = Modifier.padding(start = 2.dp, bottom = 2.dp),
                )
                Surface(
                    shape = RoundedCornerShape(2.dp, 8.dp, 8.dp, 8.dp),
                    color = NxBg3,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                        RenderedMessage(msg.content)
                    }
                }
            }
        } else {
            // User messages: right-aligned bubble with max width
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "you",
                    style    = MaterialTheme.typography.labelSmall,
                    color    = NxFg2,
                    modifier = Modifier.padding(end = 2.dp, bottom = 2.dp),
                )
                Surface(
                    shape    = RoundedCornerShape(8.dp, 2.dp, 8.dp, 8.dp),
                    color    = NxDim,
                    modifier = Modifier.widthIn(max = 280.dp),
                ) {
                    Text(
                        msg.content,
                        Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                        color = NxFg,
                    )
                }
            }
        }
    }
}

// ── Typing indicator ───────────────────────────────────────────────────────────

@Composable
private fun TypingBubble() {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        Column(horizontalAlignment = Alignment.Start) {
            Text(
                "nexis",
                style    = MaterialTheme.typography.labelSmall,
                color    = NxOrange,
                modifier = Modifier.padding(start = 2.dp, bottom = 2.dp),
            )
            Surface(
                shape = RoundedCornerShape(2.dp, 8.dp, 8.dp, 8.dp),
                color = NxBg3,
            ) {
                TypingIndicatorDots()
            }
        }
    }
}

// ── Helpers ────────────────────────────────────────────────────────────────────

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
