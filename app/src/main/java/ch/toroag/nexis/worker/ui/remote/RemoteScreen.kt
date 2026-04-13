package ch.toroag.nexis.worker.ui.remote

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import ch.toroag.nexis.worker.ui.theme.NxBorder
import ch.toroag.nexis.worker.ui.theme.NxFg
import ch.toroag.nexis.worker.ui.theme.NxFg2
import ch.toroag.nexis.worker.ui.theme.NxOrange
import ch.toroag.nexis.worker.ui.theme.NxOrangeDim

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemoteScreen(
    onBack: () -> Unit,
    vm:     RemoteViewModel = viewModel(),
) {
    val response  by vm.response.collectAsState()
    val isLoading by vm.isLoading.collectAsState()

    var appInput       by remember { mutableStateOf("") }
    var clipboardInput by remember { mutableStateOf("") }
    var volumeInput    by remember { mutableStateOf("50") }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text("remote control", style = MaterialTheme.typography.titleMedium, color = NxFg)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = NxFg2)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {

            // ── App control ───────────────────────────────────────────────────
            RemoteSection(label = "app control") {
                OutlinedTextField(
                    value         = appInput,
                    onValueChange = { appInput = it },
                    label         = { Text("app name", color = NxFg2) },
                    modifier      = Modifier.fillMaxWidth(),
                    singleLine    = true,
                    shape         = RoundedCornerShape(4.dp),
                    colors        = nxFieldColors(),
                    textStyle     = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RemoteButton(
                        label    = "open",
                        icon     = Icons.Default.OpenInNew,
                        modifier = Modifier.weight(1f),
                        enabled  = appInput.isNotBlank() && !isLoading,
                        onClick  = { vm.send("open $appInput on my computer") },
                    )
                    RemoteButton(
                        label    = "close",
                        icon     = Icons.Default.Close,
                        modifier = Modifier.weight(1f),
                        enabled  = appInput.isNotBlank() && !isLoading,
                        onClick  = { vm.send("close $appInput on my computer") },
                    )
                }
                Spacer(Modifier.height(4.dp))
                RemoteButton(
                    label   = "list open windows",
                    icon    = Icons.Default.List,
                    modifier = Modifier.fillMaxWidth(),
                    enabled  = !isLoading,
                    onClick  = { vm.send("what windows are currently open on my desktop?") },
                )
            }

            // ── Media ─────────────────────────────────────────────────────────
            RemoteSection(label = "media") {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    RemoteButton(
                        label    = "prev",
                        icon     = Icons.Default.SkipPrevious,
                        modifier = Modifier.weight(1f),
                        enabled  = !isLoading,
                        onClick  = { vm.send("skip to previous track") },
                    )
                    RemoteButton(
                        label    = "play / pause",
                        icon     = Icons.Default.PlayArrow,
                        modifier = Modifier.weight(2f),
                        enabled  = !isLoading,
                        onClick  = { vm.send("toggle media playback") },
                    )
                    RemoteButton(
                        label    = "next",
                        icon     = Icons.Default.SkipNext,
                        modifier = Modifier.weight(1f),
                        enabled  = !isLoading,
                        onClick  = { vm.send("skip to next track") },
                    )
                }
            }

            // ── Volume ────────────────────────────────────────────────────────
            RemoteSection(label = "volume") {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value         = volumeInput,
                        onValueChange = { if (it.length <= 3 && it.all(Char::isDigit)) volumeInput = it },
                        label         = { Text("level (0-100)", color = NxFg2) },
                        modifier      = Modifier.weight(1f),
                        singleLine    = true,
                        shape         = RoundedCornerShape(4.dp),
                        colors        = nxFieldColors(),
                        textStyle     = MaterialTheme.typography.bodyMedium,
                        suffix        = { Text("%", color = NxFg2) },
                    )
                    RemoteButton(
                        label    = "set",
                        icon     = Icons.Default.VolumeUp,
                        modifier = Modifier.weight(1f),
                        enabled  = volumeInput.isNotBlank() && !isLoading,
                        onClick  = { vm.send("set the volume to $volumeInput percent") },
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RemoteButton(
                        label    = "mute",
                        icon     = Icons.Default.VolumeOff,
                        modifier = Modifier.weight(1f),
                        enabled  = !isLoading,
                        onClick  = { vm.send("mute the speakers") },
                    )
                    RemoteButton(
                        label    = "unmute",
                        icon     = Icons.Default.VolumeUp,
                        modifier = Modifier.weight(1f),
                        enabled  = !isLoading,
                        onClick  = { vm.send("unmute the speakers") },
                    )
                }
            }

            // ── System ────────────────────────────────────────────────────────
            RemoteSection(label = "system") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RemoteButton(
                        label    = "lock screen",
                        icon     = Icons.Default.Lock,
                        modifier = Modifier.weight(1f),
                        enabled  = !isLoading,
                        onClick  = { vm.send("lock the computer screen") },
                    )
                    RemoteButton(
                        label    = "sleep",
                        icon     = Icons.Default.Bedtime,
                        modifier = Modifier.weight(1f),
                        enabled  = !isLoading,
                        onClick  = { vm.send("put the computer to sleep") },
                    )
                }
                Spacer(Modifier.height(8.dp))
                RemoteButton(
                    label    = "screenshot + describe",
                    icon     = Icons.Default.Screenshot,
                    modifier = Modifier.fillMaxWidth(),
                    enabled  = !isLoading,
                    onClick  = { vm.send("take a screenshot and describe what is on my screen right now") },
                )
            }

            // ── Clipboard ─────────────────────────────────────────────────────
            RemoteSection(label = "clipboard") {
                OutlinedTextField(
                    value         = clipboardInput,
                    onValueChange = { clipboardInput = it },
                    label         = { Text("text to copy", color = NxFg2) },
                    modifier      = Modifier.fillMaxWidth(),
                    maxLines      = 3,
                    shape         = RoundedCornerShape(4.dp),
                    colors        = nxFieldColors(),
                    textStyle     = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(8.dp))
                RemoteButton(
                    label    = "copy to PC clipboard",
                    icon     = Icons.Default.ContentCopy,
                    modifier = Modifier.fillMaxWidth(),
                    enabled  = clipboardInput.isNotBlank() && !isLoading,
                    onClick  = { vm.send("copy this to the clipboard: ${clipboardInput.trim()}") },
                )
            }

            // ── Response ──────────────────────────────────────────────────────
            if (isLoading || response.isNotEmpty()) {
                RemoteSection(label = "nexis says") {
                    if (isLoading && response.isEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier    = Modifier.size(14.dp),
                                color       = NxOrange,
                                strokeWidth = 2.dp,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("working…",
                                 style = MaterialTheme.typography.bodySmall,
                                 color = NxFg2,
                                 fontStyle = FontStyle.Italic)
                        }
                    } else {
                        Text(
                            response,
                            style    = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize   = 12.sp,
                            ),
                            color    = NxFg,
                        )
                        if (isLoading) {
                            Spacer(Modifier.height(4.dp))
                            LinearProgressIndicator(
                                modifier   = Modifier.fillMaxWidth(),
                                color      = NxOrange,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

// ── Sub-composables ────────────────────────────────────────────────────────────

@Composable
private fun RemoteSection(label: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            label,
            style    = MaterialTheme.typography.labelMedium,
            color    = NxOrange,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        HorizontalDivider(color = NxBorder, thickness = 0.5.dp)
        Spacer(Modifier.height(10.dp))
        content()
    }
}

@Composable
private fun RemoteButton(
    label:    String,
    icon:     androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    enabled:  Boolean  = true,
    onClick:  () -> Unit,
) {
    OutlinedButton(
        onClick  = onClick,
        modifier = modifier.height(44.dp),
        enabled  = enabled,
        shape    = RoundedCornerShape(4.dp),
        colors   = ButtonDefaults.outlinedButtonColors(
            contentColor         = NxFg,
            disabledContentColor = NxFg2,
        ),
        border   = androidx.compose.foundation.BorderStroke(
            1.dp, if (enabled) NxBorder else NxBorder.copy(alpha = 0.4f)),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = NxOrange)
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun nxFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor      = NxOrangeDim,
    unfocusedBorderColor    = NxBorder,
    focusedTextColor        = NxFg,
    unfocusedTextColor      = NxFg,
    cursorColor             = NxOrange,
    focusedContainerColor   = MaterialTheme.colorScheme.surfaceVariant,
    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
)
