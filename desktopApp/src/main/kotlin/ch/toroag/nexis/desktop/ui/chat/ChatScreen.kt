package ch.toroag.nexis.desktop.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.awtTransferable
import java.awt.datatransfer.DataFlavor
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import ch.toroag.nexis.desktop.util.MicRecorder
import ch.toroag.nexis.desktop.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Base64
import java.util.Date
import java.util.Locale

private val TIME_FMT = SimpleDateFormat("HH:mm", Locale.getDefault())

private val QUICK_ACTIONS = listOf(
    "//brief"   to "//brief",
    "//compact" to "//compact",
    "//status"  to "//status",
)

private data class PendingAttachment(
    val name:     String,
    val b64:      String,   // "data:mime;base64,..." for images, raw text for docs
    val mimeType: String,
    val isImage:  Boolean,
)

@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
fun ChatScreen(vm: ChatViewModel) {
    val messages       by vm.messages.collectAsState()
    val isStreaming    by vm.isStreaming.collectAsState()
    val error          by vm.errorMessage.collectAsState()
    val models         by vm.models.collectAsState()
    val currentModel   by vm.currentModel.collectAsState()
    val externalTyping by vm.externalTyping.collectAsState()
    val connStatus     by vm.connectionStatus.collectAsState()
    val voiceEnabled   by vm.voiceEnabled.collectAsState()
    val monitorAlert   by vm.monitorAlert.collectAsState()

    var inputText         by remember { mutableStateOf("") }
    var showModelSheet    by remember { mutableStateOf(false) }
    var showClearConfirm  by remember { mutableStateOf(false) }
    var showQuickActions  by remember { mutableStateOf(false) }
    var showAttachMenu    by remember { mutableStateOf(false) }
    var pendingAttach     by remember { mutableStateOf<PendingAttachment?>(null) }
    var isDragOver        by remember { mutableStateOf(false) }
    var isRecording       by remember { mutableStateOf(false) }
    val listState          = rememberLazyListState()
    val scope              = rememberCoroutineScope()
    val recorder           = remember { MicRecorder() }

    // File picker via AWT (no platform-specific dependency needed)
    fun openFilePicker() {
        scope.launch(Dispatchers.IO) {
            val fc = java.awt.FileDialog(null as java.awt.Frame?, "Attach file", java.awt.FileDialog.LOAD)
            fc.isVisible = true
            val dir  = fc.directory ?: return@launch
            val file = fc.file      ?: return@launch
            val f    = File(dir, file)
            withContext(Dispatchers.Default) {
                val attach = fileToAttachment(f)
                withContext(Dispatchers.Main) { pendingAttach = attach }
            }
        }
    }

    // Drag-and-drop target
    val dndTarget = remember {
        object : DragAndDropTarget {
            override fun onDrop(event: DragAndDropEvent): Boolean {
                val transferable = event.awtTransferable
                if (transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                    @Suppress("UNCHECKED_CAST")
                    val files = transferable.getTransferData(DataFlavor.javaFileListFlavor) as? List<File>
                    val file  = files?.firstOrNull() ?: return false
                    scope.launch(Dispatchers.IO) {
                        val attach = fileToAttachment(file)
                        withContext(Dispatchers.Main) {
                            pendingAttach = attach
                            isDragOver    = false
                        }
                    }
                    return true
                }
                isDragOver = false
                return false
            }
            override fun onEntered(event: DragAndDropEvent) { isDragOver = true }
            override fun onExited(event: DragAndDropEvent)  { isDragOver = false }
            override fun onEnded(event: DragAndDropEvent)   { isDragOver = false }
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    Column(
        Modifier
            .fillMaxSize()
            .dragAndDropTarget(shouldStartDragAndDrop = { true }, target = dndTarget)
    ) {

        // ── Top bar ────────────────────────────────────────────────────────────
        Surface(color = NxBg2, shadowElevation = 1.dp) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(8.dp).clip(CircleShape).background(
                        when (connStatus) {
                            ConnectionStatus.Connected    -> NxOrange
                            ConnectionStatus.Connecting   -> NxOrangeDim
                            ConnectionStatus.Disconnected -> NxFg2
                        }
                    )
                )
                Spacer(Modifier.width(8.dp))
                Text("chat", fontFamily = FontFamily.Monospace, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = NxFg,
                     modifier = Modifier.weight(1f))
                // Voice toggle
                IconButton(onClick = { vm.toggleVoice(!voiceEnabled) }) {
                    Icon(
                        if (voiceEnabled) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                        contentDescription = "Toggle voice",
                        tint = if (voiceEnabled) NxOrange else NxFg2,
                    )
                }
                if (currentModel.isNotEmpty()) {
                    TextButton(onClick = { showModelSheet = true }) {
                        Text(currentModel, fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = NxFg2)
                    }
                }
                IconButton(onClick = { showClearConfirm = true }) {
                    Icon(Icons.Default.Edit, "New conversation", tint = NxFg2)
                }
            }
        }

        // ── Monitor alert banner ───────────────────────────────────────────────
        if (monitorAlert != null) {
            Row(
                Modifier.fillMaxWidth()
                    .background(NxRed.copy(alpha = 0.12f))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.Warning, null,
                     tint = NxRed,
                     modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(monitorAlert!!, Modifier.weight(1f),
                     color = NxRed,
                     fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                IconButton(onClick = { vm.dismissMonitorAlert() }) {
                    Icon(Icons.Default.Close, "Dismiss",
                         tint = NxRed)
                }
            }
        }

        // ── Error bar ──────────────────────────────────────────────────────────
        if (error != null) {
            Row(
                Modifier.fillMaxWidth()
                    .background(NxRed.copy(alpha = 0.12f))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(error!!, Modifier.weight(1f),
                     color = NxRed,
                     fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                IconButton(onClick = { vm.clearError() }) {
                    Icon(Icons.Default.Close, "Dismiss",
                         tint = NxRed)
                }
            }
        }

        // ── Drag-over overlay hint ─────────────────────────────────────────────
        if (isDragOver) {
            Box(
                Modifier.fillMaxWidth().height(36.dp)
                    .background(NxOrangeDim.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center,
            ) {
                Text("drop to attach", fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = NxOrange)
            }
        }

        // ── Messages ───────────────────────────────────────────────────────────
        val isAtBottom by remember {
            derivedStateOf {
                val info = listState.layoutInfo
                val last = info.visibleItemsInfo.lastOrNull()?.index ?: -1
                last >= info.totalItemsCount - 1
            }
        }
        Box(Modifier.weight(1f).background(NxBg)) {
            LazyColumn(
                Modifier.fillMaxSize(),
                state               = listState,
                contentPadding      = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(messages, key = { it.id }) { msg ->
                    val showLoading = isStreaming && msg.id == messages.lastOrNull()?.id && msg.content.isEmpty()
                    MessageBubble(msg, isLoading = showLoading)
                }
                if (externalTyping && !isStreaming) {
                    item { TypingBubble() }
                }
            }
            if (!isAtBottom && messages.isNotEmpty()) {
                SmallFloatingActionButton(
                    onClick        = { scope.launch { listState.animateScrollToItem(messages.size - 1) } },
                    modifier       = Modifier.align(Alignment.BottomEnd).padding(12.dp),
                    containerColor = NxBg2,
                    contentColor   = NxFg2,
                ) {
                    Icon(Icons.Default.KeyboardArrowDown, "Scroll to bottom",
                         modifier = Modifier.size(20.dp))
                }
            }
        }

        // ── Pending attachment preview ─────────────────────────────────────────
        if (pendingAttach != null) {
            Row(
                Modifier.fillMaxWidth()
                    .background(NxBg2)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.AttachFile, null, tint = NxOrange, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(pendingAttach!!.name, Modifier.weight(1f),
                     fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = NxFg)
                IconButton(onClick = { pendingAttach = null }, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Close, "Remove attachment", tint = NxFg2)
                }
            }
        }

        // ── Input bar ──────────────────────────────────────────────────────────
        Surface(color = NxBg2, shadowElevation = 2.dp) {
            Row(
                Modifier.padding(horizontal = 8.dp, vertical = 6.dp).fillMaxWidth(),
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
                    placeholder   = {
                        Text(if (isRecording) "listening…" else "message nexis…", color = NxFg2)
                    },
                    modifier      = Modifier.weight(1f).onPreviewKeyEvent { ev ->
                        if (ev.type == KeyEventType.KeyDown && ev.key == Key.Enter && !ev.isShiftPressed) {
                            if ((inputText.isNotBlank() || pendingAttach != null) && !isStreaming) {
                                val att = pendingAttach
                                vm.sendMessage(inputText.trim(), att?.b64, att?.mimeType, att?.name)
                                inputText    = ""
                                pendingAttach = null
                            }
                            true
                        } else false
                    },
                    shape    = RoundedCornerShape(4.dp),
                    maxLines = 5,
                    colors   = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor      = if (isRecording) NxOrange else NxOrangeDim,
                        unfocusedBorderColor    = NxBorder,
                        focusedTextColor        = NxFg,
                        unfocusedTextColor      = NxFg,
                        cursorColor             = NxOrange,
                        focusedContainerColor   = NxBg2,
                        unfocusedContainerColor = NxBg2,
                    ),
                    textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = NxFg),
                )

                Spacer(Modifier.width(4.dp))

                // Attach file / image
                Box {
                    IconButton(onClick = { showAttachMenu = true }) {
                        Icon(Icons.Default.AttachFile, "Attach file", tint = NxFg2)
                    }
                    DropdownMenu(
                        expanded         = showAttachMenu,
                        onDismissRequest = { showAttachMenu = false },
                        containerColor   = NxBg2,
                    ) {
                        DropdownMenuItem(
                            text    = { Text("Choose file…", color = NxFg) },
                            onClick = { showAttachMenu = false; openFilePicker() },
                        )
                        DropdownMenuItem(
                            text    = { Text("Screenshot", color = NxFg) },
                            onClick = {
                                showAttachMenu = false
                                vm.sendMessage("//screenshot")
                            },
                        )
                    }
                }

                // Mic button — hold to record, release to send
                IconButton(onClick = {
                    if (!isRecording) {
                        val started = recorder.start()
                        if (started) isRecording = true
                    } else {
                        isRecording = false
                        scope.launch(Dispatchers.IO) {
                            val wav = recorder.stopAndGetWav()
                            vm.transcribeAndSend(wav)
                        }
                    }
                }) {
                    Icon(
                        Icons.Default.Mic,
                        if (isRecording) "Stop recording" else "Voice input",
                        tint = if (isRecording) NxOrange else NxFg2,
                    )
                }

                // Send / stop
                IconButton(onClick = {
                    if (isStreaming) {
                        vm.abortStreaming()
                    } else if (inputText.isNotBlank() || pendingAttach != null) {
                        val att = pendingAttach
                        vm.sendMessage(inputText.trim(), att?.b64, att?.mimeType, att?.name)
                        inputText    = ""
                        pendingAttach = null
                    }
                }) {
                    Icon(
                        if (isStreaming) Icons.Default.Stop else Icons.Default.Send,
                        if (isStreaming) "Stop" else "Send",
                        tint = if (isStreaming) NxRed else NxOrange,
                    )
                }
            }
        }
    }

    // ── Dialogs ────────────────────────────────────────────────────────────────

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest  = { showClearConfirm = false },
            containerColor    = NxBg2,
            titleContentColor = NxFg,
            textContentColor  = NxFg2,
            title   = { Text("New conversation", fontFamily = FontFamily.Monospace, fontSize = 14.sp, fontWeight = FontWeight.Bold) },
            text    = { Text("Start a fresh conversation. Nexis keeps its memories and history for context.") },
            confirmButton = {
                TextButton(onClick = { showClearConfirm = false; vm.clearConversation() }) {
                    Text("Start fresh", color = NxOrange)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) { Text("Cancel", color = NxFg2) }
            },
        )
    }

    if (showQuickActions) {
        AlertDialog(
            onDismissRequest = { showQuickActions = false },
            containerColor   = NxBg2,
            title = { Text("quick actions", fontFamily = FontFamily.Monospace, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = NxFg) },
            text  = {
                Column {
                    QUICK_ACTIONS.forEach { (label, prompt) ->
                        Text(
                            label,
                            fontFamily = FontFamily.Monospace, fontSize = 12.sp,
                            color    = NxFg,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showQuickActions = false; vm.sendMessage(prompt) }
                                .padding(vertical = 8.dp),
                        )
                        HorizontalDivider(color = NxBorder, thickness = 0.5.dp)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showQuickActions = false }) { Text("close", color = NxFg2) }
            },
        )
    }

    if (showModelSheet) {
        AlertDialog(
            onDismissRequest = { showModelSheet = false },
            containerColor   = NxBg2,
            title = { Text("select model", fontFamily = FontFamily.Monospace, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = NxFg) },
            text  = {
                Column {
                    models.filter { it.installed }.forEach { m ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { vm.selectModel(m.key); showModelSheet = false }
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column {
                                Text(m.label, color = if (m.current) NxOrange else NxFg,
                                     fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                                Text(m.desc, color = NxFg2, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                            }
                            if (m.current) Text("active", color = NxOrange,
                                                fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                        }
                        HorizontalDivider(color = NxBorder, thickness = 0.5.dp)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showModelSheet = false }) { Text("close", color = NxFg2) }
            },
        )
    }
}

// ── Message bubble ─────────────────────────────────────────────────────────────

@Composable
private fun MessageBubble(msg: ChatMessage, isLoading: Boolean = false) {
    val isUser    = msg.role == "user"
    val timeLabel = TIME_FMT.format(Date(msg.id))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start) {
        if (!isUser) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.Start) {
                Text("nexis", fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = NxOrange,
                     modifier = Modifier.padding(start = 2.dp, bottom = 2.dp))
                Surface(shape = RoundedCornerShape(2.dp, 8.dp, 8.dp, 8.dp), color = NxBg3,
                        modifier = Modifier.fillMaxWidth()) {
                    if (isLoading) TypingIndicatorDots()
                    else Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                        RenderedMessage(msg.content)
                    }
                }
                Text(timeLabel, fontFamily = FontFamily.Monospace, fontSize = 10.sp,
                     color = NxFg2.copy(alpha = 0.5f),
                     modifier = Modifier.padding(start = 4.dp, top = 2.dp))
            }
        } else {
            Column(horizontalAlignment = Alignment.End) {
                Text("you", fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = NxFg2,
                     modifier = Modifier.padding(end = 2.dp, bottom = 2.dp))
                Surface(shape = RoundedCornerShape(8.dp, 2.dp, 8.dp, 8.dp), color = NxDim,
                        modifier = Modifier.widthIn(max = 400.dp)) {
                    Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                        if (msg.hasAttach) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.AttachFile, null,
                                     tint = NxOrange, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(msg.attachName.ifEmpty { "attachment" },
                                     fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = NxOrangeDim)
                            }
                            if (msg.content.isNotBlank()) Spacer(Modifier.height(4.dp))
                        }
                        if (msg.content.isNotBlank()) {
                            Text(msg.content,
                                 fontFamily = FontFamily.Monospace, fontSize = 12.sp,
                                 color = NxFg)
                        }
                    }
                }
                Text(timeLabel, fontFamily = FontFamily.Monospace, fontSize = 10.sp,
                     color = NxFg2.copy(alpha = 0.5f),
                     modifier = Modifier.padding(end = 4.dp, top = 2.dp))
            }
        }
    }
}

@Composable
private fun TypingBubble() {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        Column(horizontalAlignment = Alignment.Start) {
            Text("nexis", fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = NxOrange,
                 modifier = Modifier.padding(start = 2.dp, bottom = 2.dp))
            Surface(shape = RoundedCornerShape(2.dp, 8.dp, 8.dp, 8.dp), color = NxBg3) {
                TypingIndicatorDots()
            }
        }
    }
}

// ── Helpers ────────────────────────────────────────────────────────────────────

private fun fileToAttachment(file: File): PendingAttachment? {
    if (!file.exists() || !file.canRead()) return null
    val mime    = guessMime(file.name)
    val isImage = mime.startsWith("image/")
    val bytes   = file.readBytes()
    val b64     = if (isImage)
        "data:$mime;base64," + Base64.getEncoder().encodeToString(bytes)
    else
        bytes.decodeToString().take(8000)
    return PendingAttachment(file.name, b64, mime, isImage)
}

private fun guessMime(name: String): String = when (name.substringAfterLast('.').lowercase()) {
    "png"            -> "image/png"
    "jpg", "jpeg"    -> "image/jpeg"
    "gif"            -> "image/gif"
    "webp"           -> "image/webp"
    "pdf"            -> "application/pdf"
    "txt", "md"      -> "text/plain"
    "py"             -> "text/x-python"
    "js", "ts"       -> "text/javascript"
    "kt"             -> "text/x-kotlin"
    "java"           -> "text/x-java"
    "json"           -> "application/json"
    "sh", "bash"     -> "text/x-shellscript"
    "csv"            -> "text/csv"
    else             -> "application/octet-stream"
}
