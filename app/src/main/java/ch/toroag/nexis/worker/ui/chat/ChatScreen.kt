package ch.toroag.nexis.worker.ui.chat

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import ch.toroag.nexis.worker.R
import ch.toroag.nexis.worker.ui.theme.NxBg3
import ch.toroag.nexis.worker.ui.theme.NxBorder
import ch.toroag.nexis.worker.ui.theme.NxDim
import ch.toroag.nexis.worker.ui.theme.NxFg
import ch.toroag.nexis.worker.ui.theme.NxFg2
import ch.toroag.nexis.worker.ui.theme.NxOrange
import ch.toroag.nexis.worker.ui.theme.NxOrangeDim
import ch.toroag.nexis.worker.MainActivity
import ch.toroag.nexis.worker.SharePayload
import ch.toroag.nexis.worker.util.SoundFx
import ch.toroag.nexis.worker.util.SpeechRecognizerHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val TIME_FMT = SimpleDateFormat("HH:mm", Locale.getDefault())

private val QUICK_ACTIONS = listOf(
    "Brief me" to "Give me a quick morning briefing — time, anything relevant you know about me, and one thing worth knowing today.",
    "What time is it?" to "What time and date is it right now?",
    "Summarize conversation" to "Summarize our conversation so far in a few sentences.",
    "What do you remember?" to "What do you remember about me? List your most relevant memories.",
    "What's the weather?" to "What's the current weather where I am?",
)

// ── Pending attachment ─────────────────────────────────────────────────────────
private data class PendingImage(
    val uri:      Uri,
    val base64:   String,
    val mimeType: String,
    val name:     String,
    val isImage:  Boolean = true,
)

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun ChatScreen(
    onNavigateToSettings: () -> Unit,
    onNavigateToVoice:    () -> Unit    = {},
    onNavigateToRemote:   () -> Unit    = {},
    sharePayload:         SharePayload? = null,
    onShareConsumed:      () -> Unit    = {},
    chatVm: ChatViewModel = viewModel(),
) {
    val context         = LocalContext.current
    val messages        by chatVm.messages.collectAsState()
    val isStreaming     by chatVm.isStreaming.collectAsState()
    val error           by chatVm.errorMessage.collectAsState()
    val models          by chatVm.models.collectAsState()
    val currentModel    by chatVm.currentModel.collectAsState()
    val voiceEnabled    by chatVm.voiceEnabled.collectAsState()
    val externalTyping  by chatVm.externalTyping.collectAsState()
    val connStatus      by chatVm.connectionStatus.collectAsState()

    var inputText          by remember { mutableStateOf("") }
    var partialText        by remember { mutableStateOf("") }   // live STT partial
    var showModelSheet     by remember { mutableStateOf(false) }
    var showClearConfirm   by remember { mutableStateOf(false) }
    var showQuickActions   by remember { mutableStateOf(false) }
    var showAttachMenu     by remember { mutableStateOf(false) }
    var isMicListening     by remember { mutableStateOf(false) }
    var pendingImage       by remember { mutableStateOf<PendingImage?>(null) }
    var cameraUri          by remember { mutableStateOf<Uri?>(null) }
    val scope              = rememberCoroutineScope()

    val listState = rememberLazyListState()
    val speech    = remember { SpeechRecognizerHelper(context) }

    // ── Permission / media launchers ──────────────────────────────────────────
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startListening(speech, chatVm, { isMicListening = it }) { partialText = it }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch(Dispatchers.IO) {
            val img = uriToPendingImage(context, uri) ?: return@launch
            withContext(Dispatchers.Main) { pendingImage = img }
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        val uri = cameraUri ?: return@rememberLauncherForActivityResult
        if (!success) return@rememberLauncherForActivityResult
        scope.launch(Dispatchers.IO) {
            val img = uriToPendingImage(context, uri) ?: return@launch
            withContext(Dispatchers.Main) { pendingImage = img }
        }
    }

    val docLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch(Dispatchers.IO) {
            val att = uriToAttachment(context, uri) ?: return@launch
            withContext(Dispatchers.Main) { pendingImage = att }
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }
    DisposableEffect(Unit) { onDispose { speech.destroy() } }

    // Handle share-from-other-app: pre-populate input or send image directly
    LaunchedEffect(sharePayload) {
        val p = sharePayload ?: return@LaunchedEffect
        onShareConsumed()
        when {
            p.imageB64 != null -> {
                // Image share: send immediately with "What is this?" prompt
                chatVm.sendMessage(
                    text          = "What is this?",
                    imageBase64   = p.imageB64,
                    imageMimeType = p.imageMime,
                    imageName     = "shared_image",
                )
                SoundFx.send()
            }
            p.text != null -> {
                // Text share: pre-fill the input so user can edit before sending
                inputText = p.text
            }
        }
    }

    Scaffold(
        containerColor      = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0),
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
                            painter     = painterResource(R.drawable.ic_nexis_logo),
                            contentDescription = "NeXiS",
                            colorFilter = ColorFilter.tint(NxOrange),
                            modifier    = Modifier.size(28.dp),
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
                        Spacer(Modifier.width(6.dp))
                        // Connection status dot
                        Box(
                            Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(
                                    when (connStatus) {
                                        ConnectionStatus.Connected    -> NxOrange
                                        ConnectionStatus.Connecting   -> NxOrangeDim
                                        ConnectionStatus.Disconnected -> NxFg2
                                    }
                                )
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showClearConfirm = true }) {
                        Icon(Icons.Default.Edit, "New conversation", tint = NxFg2)
                    }
                    IconButton(onClick = onNavigateToVoice) {
                        Icon(Icons.Default.RecordVoiceOver, "Voice conversation", tint = NxFg2)
                    }
                    IconButton(onClick = onNavigateToRemote) {
                        Icon(Icons.Default.Computer, "Remote control", tint = NxFg2)
                    }
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

                // Live STT partial transcript
                if (isMicListening && partialText.isNotEmpty()) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.Mic, null, tint = NxOrange, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            partialText,
                            Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic),
                            color = NxFg2,
                        )
                    }
                }

                // Pending attachment preview
                if (pendingImage != null) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (pendingImage!!.isImage) {
                            val bmp = remember(pendingImage!!.uri) {
                                try {
                                    val bytes = Base64.decode(
                                        pendingImage!!.base64.substringAfter(","), Base64.DEFAULT)
                                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                        ?.asImageBitmap()
                                } catch (_: Exception) { null }
                            }
                            if (bmp != null) {
                                Image(
                                    bitmap      = bmp,
                                    contentDescription = null,
                                    modifier    = Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(4.dp)),
                                    contentScale = ContentScale.Crop,
                                )
                                Spacer(Modifier.width(8.dp))
                            } else {
                                Icon(Icons.Default.AttachFile, null, tint = NxOrange,
                                     modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                            }
                        } else {
                            Icon(Icons.Default.AttachFile, null, tint = NxOrange,
                                 modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                        }
                        Text(pendingImage!!.name, Modifier.weight(1f),
                             style = MaterialTheme.typography.bodySmall, color = NxFg)
                        IconButton(onClick = { pendingImage = null }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Close, "Remove", tint = NxFg2)
                        }
                    }
                }

                Surface(
                    color           = MaterialTheme.colorScheme.surface,
                    shadowElevation = 0.dp,
                    tonalElevation  = 0.dp,
                ) {
                    Row(
                        Modifier
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Quick actions
                        Box {
                            IconButton(onClick = { showQuickActions = true }) {
                                Icon(Icons.Default.Add, "Quick actions", tint = NxFg2)
                            }
                        }
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
                        // Attach image button with dropdown
                        Box {
                            IconButton(onClick = { showAttachMenu = true }) {
                                Icon(Icons.Default.AttachFile, "Attach image", tint = NxFg2)
                            }
                            DropdownMenu(
                                expanded         = showAttachMenu,
                                onDismissRequest = { showAttachMenu = false },
                                containerColor   = MaterialTheme.colorScheme.surface,
                            ) {
                                DropdownMenuItem(
                                    text    = { Text("Camera", color = NxFg) },
                                    onClick = {
                                        showAttachMenu = false
                                        val tmp = File.createTempFile("nexis_", ".jpg", context.cacheDir)
                                        val uri = FileProvider.getUriForFile(
                                            context, "${context.packageName}.provider", tmp)
                                        cameraUri = uri
                                        cameraLauncher.launch(uri)
                                    },
                                )
                                DropdownMenuItem(
                                    text    = { Text("Gallery", color = NxFg) },
                                    onClick = {
                                        showAttachMenu = false
                                        galleryLauncher.launch("image/*")
                                    },
                                )
                                DropdownMenuItem(
                                    text    = { Text("File / Document", color = NxFg) },
                                    onClick = {
                                        showAttachMenu = false
                                        docLauncher.launch("*/*")
                                    },
                                )
                            }
                        }
                        // Mic button
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
                                    startListening(speech, chatVm, { isMicListening = it }) { partialText = it }
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
                            } else if (inputText.isNotBlank() || pendingImage != null) {
                                val img = pendingImage
                                chatVm.sendMessage(inputText, img?.base64, img?.mimeType, img?.name)
                                inputText    = ""
                                pendingImage = null
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
        val isAtBottom by remember {
            derivedStateOf {
                val info = listState.layoutInfo
                val last = info.visibleItemsInfo.lastOrNull()?.index ?: -1
                last >= info.totalItemsCount - 1
            }
        }
        Box(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            LazyColumn(
                Modifier.fillMaxSize(),
                state               = listState,
                contentPadding      = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(messages, key = { it.id }) { msg -> MessageBubble(msg, context) }
                if (isStreaming && messages.lastOrNull()?.content?.isEmpty() == true) {
                    item { TypingBubble() }
                }
                if (externalTyping && !isStreaming) {
                    item { TypingBubble() }
                }
            }
            if (!isAtBottom && messages.isNotEmpty()) {
                SmallFloatingActionButton(
                    onClick          = { scope.launch { listState.animateScrollToItem(messages.size - 1) } },
                    modifier         = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(12.dp),
                    containerColor   = MaterialTheme.colorScheme.surface,
                    contentColor     = NxFg2,
                ) {
                    Icon(Icons.Default.KeyboardArrowDown, "Scroll to bottom",
                         modifier = Modifier.size(20.dp))
                }
            }
        }
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest  = { showClearConfirm = false },
            containerColor    = MaterialTheme.colorScheme.surface,
            titleContentColor = NxFg,
            textContentColor  = NxFg2,
            title   = { Text("New conversation", style = MaterialTheme.typography.titleMedium) },
            text    = { Text("Start a fresh conversation. Nexis keeps its memories and history for context.") },
            confirmButton = {
                TextButton(onClick = {
                    showClearConfirm = false
                    chatVm.clearConversation()
                }) { Text("Start fresh", color = NxOrange) }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text("Cancel", color = NxFg2)
                }
            },
        )
    }

    if (showQuickActions) {
        ModalBottomSheet(
            onDismissRequest = { showQuickActions = false },
            containerColor   = MaterialTheme.colorScheme.surface,
            contentColor     = NxFg,
        ) {
            Text(
                "quick actions",
                Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.labelMedium,
                color = NxFg2,
            )
            QUICK_ACTIONS.forEach { (label, prompt) ->
                ListItem(
                    headlineContent = {
                        Text(label, color = NxFg, style = MaterialTheme.typography.bodyMedium)
                    },
                    colors   = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.clickable {
                        showQuickActions = false
                        chatVm.sendMessage(prompt)
                        SoundFx.send()
                    },
                )
                HorizontalDivider(color = NxBorder, thickness = 0.5.dp)
            }
            Spacer(Modifier.height(24.dp))
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

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(msg: ChatMessage, context: Context) {
    val isUser    = msg.role == "user"
    val timeLabel = TIME_FMT.format(Date(msg.id))
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        if (!isUser) {
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
                    shape    = RoundedCornerShape(2.dp, 8.dp, 8.dp, 8.dp),
                    color    = NxBg3,
                    modifier = Modifier
                        .fillMaxWidth()
                        .combinedClickable(
                            onClick     = {},
                            onLongClick = { copyToClipboard(context, msg.content) },
                        ),
                ) {
                    Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                        RenderedMessage(msg.content)
                    }
                }
                Text(
                    timeLabel,
                    style    = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color    = NxFg2.copy(alpha = 0.5f),
                    modifier = Modifier.padding(start = 4.dp, top = 2.dp),
                )
            }
        } else {
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
                    modifier = Modifier
                        .widthIn(max = 280.dp)
                        .combinedClickable(
                            onClick     = {},
                            onLongClick = { copyToClipboard(context, msg.content) },
                        ),
                ) {
                    Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                        if (msg.hasImage) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.AttachFile, null,
                                     tint = NxOrange, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("image attached",
                                     style = MaterialTheme.typography.labelSmall,
                                     color = NxOrangeDim)
                            }
                            if (msg.content.isNotBlank()) Spacer(Modifier.height(4.dp))
                        }
                        if (msg.content.isNotBlank()) {
                            Text(
                                msg.content,
                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                                color = NxFg,
                            )
                        }
                    }
                }
                Text(
                    timeLabel,
                    style    = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color    = NxFg2.copy(alpha = 0.5f),
                    modifier = Modifier.padding(end = 4.dp, top = 2.dp),
                )
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
    speech:    SpeechRecognizerHelper,
    chatVm:    ChatViewModel,
    setFlag:   (Boolean) -> Unit,
    onPartial: (String) -> Unit = {},
) {
    setFlag(true)
    speech.startListening(
        onResult  = { text -> setFlag(false); onPartial(""); chatVm.sendMessage(text) },
        onError   = { setFlag(false); onPartial("") },
        onPartial = onPartial,
    )
}

private fun copyToClipboard(context: Context, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("nexis", text))
}

private fun uriToPendingImage(context: Context, uri: Uri): PendingImage? = try {
    val bytes    = context.contentResolver.openInputStream(uri)?.readBytes() ?: return null
    val mimeType = context.contentResolver.getType(uri) ?: "image/jpeg"
    val b64      = "data:$mimeType;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
    val name     = uri.lastPathSegment ?: "image.jpg"
    PendingImage(uri = uri, base64 = b64, mimeType = mimeType, name = name, isImage = true)
} catch (e: Exception) { null }

private fun uriToAttachment(context: Context, uri: Uri): PendingImage? = try {
    val bytes    = context.contentResolver.openInputStream(uri)?.readBytes() ?: return null
    val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"
    val isImage  = mimeType.startsWith("image/")
    val b64      = if (isImage)
        "data:$mimeType;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
    else
        bytes.toString(Charsets.UTF_8).take(8000)   // server expects raw text for non-images
    val name     = uri.lastPathSegment ?: "file"
    PendingImage(uri = uri, base64 = b64, mimeType = mimeType, name = name, isImage = isImage)
} catch (e: Exception) { null }
