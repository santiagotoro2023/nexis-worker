package ch.toroag.nexis.worker.ui.remote

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
    val result    by vm.result.collectAsState()
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
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
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
            RemoteSection("app control") {
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
                    RemoteButton("open",  Icons.Default.OpenInNew, Modifier.weight(1f),
                        appInput.isNotBlank() && !isLoading) { vm.action("open",  appInput.trim()) }
                    RemoteButton("close", Icons.Default.Close,     Modifier.weight(1f),
                        appInput.isNotBlank() && !isLoading) { vm.action("close", appInput.trim()) }
                }
                Spacer(Modifier.height(4.dp))
                RemoteButton("list open windows", Icons.Default.List, Modifier.fillMaxWidth(),
                    !isLoading) { vm.action("windows") }
            }

            // ── Media ─────────────────────────────────────────────────────────
            RemoteSection("media") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RemoteButton("prev",       Icons.Default.SkipPrevious, Modifier.weight(1f), !isLoading) {
                        vm.action("media", "previous") }
                    RemoteButton("play/pause", Icons.Default.PlayArrow,    Modifier.weight(2f), !isLoading) {
                        vm.action("media", "play-pause") }
                    RemoteButton("next",       Icons.Default.SkipNext,     Modifier.weight(1f), !isLoading) {
                        vm.action("media", "next") }
                }
            }

            // ── Volume ────────────────────────────────────────────────────────
            RemoteSection("volume") {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value         = volumeInput,
                        onValueChange = { if (it.length <= 3 && it.all(Char::isDigit)) volumeInput = it },
                        label         = { Text("0–100", color = NxFg2) },
                        modifier      = Modifier.weight(1f),
                        singleLine    = true,
                        shape         = RoundedCornerShape(4.dp),
                        colors        = nxFieldColors(),
                        textStyle     = MaterialTheme.typography.bodyMedium,
                        suffix        = { Text("%", color = NxFg2) },
                    )
                    RemoteButton("set", Icons.Default.VolumeUp, Modifier.weight(1f),
                        volumeInput.isNotBlank() && !isLoading) { vm.action("volume", volumeInput) }
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RemoteButton("mute",   Icons.Default.VolumeOff, Modifier.weight(1f), !isLoading) {
                        vm.action("mute") }
                    RemoteButton("unmute", Icons.Default.VolumeUp,  Modifier.weight(1f), !isLoading) {
                        vm.action("unmute") }
                }
            }

            // ── Clipboard ─────────────────────────────────────────────────────
            RemoteSection("clipboard") {
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
                RemoteButton("copy to PC clipboard", Icons.Default.ContentCopy, Modifier.fillMaxWidth(),
                    clipboardInput.isNotBlank() && !isLoading) { vm.action("clip", clipboardInput.trim()) }
            }

            // ── System ────────────────────────────────────────────────────────
            RemoteSection("system") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RemoteButton("lock screen", Icons.Default.Lock,    Modifier.weight(1f), !isLoading) {
                        vm.action("lock") }
                    RemoteButton("sleep",       Icons.Default.Bedtime, Modifier.weight(1f), !isLoading) {
                        vm.action("sleep") }
                }
                Spacer(Modifier.height(8.dp))
                RemoteButton("screenshot + describe", Icons.Default.Screenshot, Modifier.fillMaxWidth(),
                    !isLoading) { vm.action("screenshot") }
            }

            // ── Result ────────────────────────────────────────────────────────
            if (isLoading || result.isNotEmpty()) {
                RemoteSection("result") {
                    if (isLoading && result.isEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier    = Modifier.size(14.dp),
                                color       = NxOrange,
                                strokeWidth = 2.dp,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("working…",
                                style     = MaterialTheme.typography.bodySmall,
                                color     = NxFg2)
                        }
                    } else {
                        Text(
                            result,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize   = 12.sp,
                            ),
                            color = NxFg,
                        )
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
        Text(label, style = MaterialTheme.typography.labelMedium, color = NxOrange,
             modifier = Modifier.padding(bottom = 6.dp))
        HorizontalDivider(color = NxBorder, thickness = 0.5.dp)
        Spacer(Modifier.height(10.dp))
        content()
    }
}

@Composable
private fun RemoteButton(
    label:   String,
    icon:    androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    enabled:  Boolean  = true,
    onClick:  () -> Unit,
) {
    OutlinedButton(
        onClick        = onClick,
        modifier       = modifier.height(44.dp),
        enabled        = enabled,
        shape          = RoundedCornerShape(4.dp),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
        colors         = ButtonDefaults.outlinedButtonColors(
            contentColor         = NxFg,
            disabledContentColor = NxFg2,
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp, if (enabled) NxBorder else NxBorder.copy(alpha = 0.4f)),
    ) {
        Icon(icon, null, modifier = Modifier.size(16.dp), tint = NxOrange)
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
