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
import ch.toroag.nexis.worker.ui.theme.*
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemoteScreen(
    onBack: () -> Unit,
    vm:     RemoteViewModel = viewModel(),
) {
    val result         by vm.result.collectAsState()
    val isLoading      by vm.isLoading.collectAsState()
    val devices        by vm.devices.collectAsState()
    val selectedDevice by vm.selectedDevice.collectAsState()
    val devicesLoading by vm.devicesLoading.collectAsState()

    var appInput       by remember { mutableStateOf("") }
    var clipboardInput by remember { mutableStateOf("") }
    var urlInput       by remember { mutableStateOf("") }
    var notifyInput    by remember { mutableStateOf("") }
    var unlockPassword by remember { mutableStateOf("") }
    var volumeSlider   by remember { mutableFloatStateOf(50f) }
    var deviceDropdown by remember { mutableStateOf(false) }

    val isDesktop = selectedDevice?.deviceType == "desktop"
    val isMobile  = selectedDevice?.deviceType == "mobile"
    val isOffline = selectedDevice?.online == false

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("remote control", style = MaterialTheme.typography.titleMedium, color = NxFg) },
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

            // ── Target device ──────────────────────────────────────────────────
            RemoteSection("target device") {
                Box(Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick  = { if (devices.isNotEmpty()) deviceDropdown = true },
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                        shape    = RoundedCornerShape(4.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                        colors   = ButtonDefaults.outlinedButtonColors(contentColor = NxFg),
                        border   = androidx.compose.foundation.BorderStroke(1.dp, NxBorder),
                    ) {
                        when {
                            devicesLoading -> {
                                CircularProgressIndicator(Modifier.size(14.dp), color = NxOrange, strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                                Text("loading…", style = MaterialTheme.typography.bodySmall, color = NxFg2)
                            }
                            selectedDevice != null -> {
                                val dev = selectedDevice!!
                                Text(if (dev.online) "●" else "○",
                                     color = if (dev.online) NxGreen else NxFg2,
                                     style = MaterialTheme.typography.bodySmall)
                                Spacer(Modifier.width(6.dp))
                                Icon(
                                    if (dev.deviceType == "mobile") Icons.Default.PhoneAndroid
                                    else Icons.Default.Computer,
                                    null, Modifier.size(14.dp), tint = NxFg2,
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(dev.hostname, style = MaterialTheme.typography.bodyMedium, color = NxFg)
                                dev.role?.let {
                                    Spacer(Modifier.width(6.dp))
                                    Text("[$it]", style = MaterialTheme.typography.labelSmall, color = NxOrangeDim)
                                }
                                Spacer(Modifier.weight(1f))
                                Icon(Icons.Default.ArrowDropDown, null, Modifier.size(18.dp), tint = NxFg2)
                            }
                            else -> {
                                Text("no devices", style = MaterialTheme.typography.bodySmall, color = NxFg2)
                                Spacer(Modifier.weight(1f))
                                Icon(Icons.Default.ArrowDropDown, null, Modifier.size(18.dp), tint = NxFg2)
                            }
                        }
                    }
                    DropdownMenu(
                        expanded = deviceDropdown,
                        onDismissRequest = { deviceDropdown = false },
                    ) {
                        devices.forEach { dev ->
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(if (dev.online) "●" else "○",
                                             color = if (dev.online) NxGreen else NxFg2,
                                             style = MaterialTheme.typography.bodySmall)
                                        Spacer(Modifier.width(6.dp))
                                        Icon(
                                            if (dev.deviceType == "mobile") Icons.Default.PhoneAndroid
                                            else Icons.Default.Computer,
                                            null, Modifier.size(14.dp), tint = NxFg2,
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Column {
                                            Text(dev.hostname, style = MaterialTheme.typography.bodyMedium, color = NxFg)
                                            Text("${dev.os}${dev.role?.let { " · [$it]" } ?: ""}",
                                                 style = MaterialTheme.typography.labelSmall, color = NxFg2)
                                        }
                                    }
                                },
                                onClick = { vm.selectDevice(dev); deviceDropdown = false },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    TextButton(
                        onClick  = { vm.probeSelectedDevice() },
                        enabled  = selectedDevice != null && !isLoading,
                        contentPadding = PaddingValues(horizontal = 4.dp),
                    ) {
                        Icon(Icons.Default.Info, null, Modifier.size(12.dp), tint = NxFg2)
                        Spacer(Modifier.width(4.dp))
                        Text("probe", style = MaterialTheme.typography.labelSmall, color = NxFg2)
                    }
                    TextButton(
                        onClick  = { vm.loadDevices() },
                        contentPadding = PaddingValues(horizontal = 4.dp),
                    ) {
                        Icon(Icons.Default.Refresh, null, Modifier.size(12.dp), tint = NxFg2)
                        Spacer(Modifier.width(4.dp))
                        Text("refresh", style = MaterialTheme.typography.labelSmall, color = NxFg2)
                    }
                }
            }

            // ── WOL — offline PC only ──────────────────────────────────────────
            if (isDesktop && isOffline) {
                RemoteSection("wake on lan") {
                    val hasMac = selectedDevice?.mac?.isNotEmpty() == true
                    RemoteButton(
                        label    = if (hasMac) "send magic packet (WOL)" else "WOL — no MAC recorded yet",
                        icon     = Icons.Default.PowerSettingsNew,
                        modifier = Modifier.fillMaxWidth(),
                        enabled  = hasMac && !isLoading,
                    ) { vm.wakeOnLan() }
                }
            }

            // ── Desktop controls ───────────────────────────────────────────────
            if (isDesktop) {

                RemoteSection("app control") {
                    OutlinedTextField(
                        value         = appInput,
                        onValueChange = { appInput = it },
                        label         = { Text("app name or URL", color = NxFg2) },
                        modifier      = Modifier.fillMaxWidth(),
                        singleLine    = true,
                        shape         = RoundedCornerShape(4.dp),
                        colors        = nxFieldColors(),
                        textStyle     = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        RemoteButton("open",  Icons.Default.OpenInNew, Modifier.weight(1f),
                            appInput.isNotBlank() && !isLoading) { vm.action("open", appInput.trim()) }
                        RemoteButton("close", Icons.Default.Close,     Modifier.weight(1f),
                            appInput.isNotBlank() && !isLoading) { vm.action("close", appInput.trim()) }
                    }
                    Spacer(Modifier.height(4.dp))
                    RemoteButton("list open windows", Icons.Default.List, Modifier.fillMaxWidth(),
                        !isLoading) { vm.action("windows") }
                }

                RemoteSection("media") {
                    // prev | play/pause | next
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        RemoteButton("prev",       Icons.Default.SkipPrevious, Modifier.weight(1f), !isLoading) {
                            vm.action("media", "previous") }
                        RemoteButton("play/pause", Icons.Default.PlayArrow,    Modifier.weight(2f), !isLoading) {
                            vm.action("media", "play-pause") }
                        RemoteButton("next",       Icons.Default.SkipNext,     Modifier.weight(1f), !isLoading) {
                            vm.action("media", "next") }
                    }
                    Spacer(Modifier.height(8.dp))
                    // -10s | +10s
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        RemoteButton("−10s", Icons.Default.Replay10,  Modifier.weight(1f), !isLoading) {
                            vm.action("media", "seek_backward") }
                        RemoteButton("+10s", Icons.Default.Forward10, Modifier.weight(1f), !isLoading) {
                            vm.action("media", "seek_forward") }
                    }
                }

                RemoteSection("volume") {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.VolumeDown, null, Modifier.size(18.dp), tint = NxFg2)
                        Slider(
                            value         = volumeSlider,
                            onValueChange = { volumeSlider = it },
                            onValueChangeFinished = { vm.action("volume", volumeSlider.roundToInt().toString()) },
                            valueRange    = 0f..100f,
                            modifier      = Modifier.weight(1f),
                            colors        = SliderDefaults.colors(
                                thumbColor          = NxOrange,
                                activeTrackColor    = NxOrange,
                                inactiveTrackColor  = NxBorder,
                            ),
                        )
                        Icon(Icons.Default.VolumeUp, null, Modifier.size(18.dp), tint = NxFg2)
                        Text(
                            "${volumeSlider.roundToInt()}%",
                            style    = MaterialTheme.typography.labelSmall,
                            color    = NxFg2,
                            modifier = Modifier.widthIn(min = 36.dp),
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        RemoteButton("mute",   Icons.Default.VolumeOff, Modifier.weight(1f), !isLoading) {
                            vm.action("mute") }
                        RemoteButton("unmute", Icons.Default.VolumeUp,  Modifier.weight(1f), !isLoading) {
                            vm.action("unmute") }
                    }
                }

                RemoteSection("clipboard") {
                    OutlinedTextField(
                        value         = clipboardInput,
                        onValueChange = { clipboardInput = it },
                        label         = { Text("text to copy to PC", color = NxFg2) },
                        modifier      = Modifier.fillMaxWidth(),
                        maxLines      = 3,
                        shape         = RoundedCornerShape(4.dp),
                        colors        = nxFieldColors(),
                        textStyle     = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(8.dp))
                    RemoteButton("copy to PC clipboard", Icons.Default.ContentCopy, Modifier.fillMaxWidth(),
                        clipboardInput.isNotBlank() && !isLoading) { vm.action("clip", clipboardInput.trim()) }
                    Spacer(Modifier.height(4.dp))
                    RemoteButton("paste PC clipboard on phone", Icons.Default.ContentPaste, Modifier.fillMaxWidth(),
                        !isLoading) { vm.pasteFromPc() }
                }

                RemoteSection("system") {
                    // lock ↔ unlock (logical pair)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        RemoteButton("lock screen", Icons.Default.Lock,     Modifier.weight(1f), !isLoading) {
                            vm.action("lock") }
                        RemoteButton("unlock",      Icons.Default.LockOpen, Modifier.weight(1f), !isLoading) {
                            vm.action("unlock", unlockPassword) }
                    }
                    // Password for unlock (compact, always visible)
                    OutlinedTextField(
                        value         = unlockPassword,
                        onValueChange = { unlockPassword = it },
                        label         = { Text("password for unlock (optional)", color = NxFg2) },
                        modifier      = Modifier.fillMaxWidth().padding(top = 6.dp),
                        singleLine    = true,
                        shape         = RoundedCornerShape(4.dp),
                        colors        = nxFieldColors(),
                        textStyle     = MaterialTheme.typography.bodyMedium,
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    )
                    Spacer(Modifier.height(8.dp))
                    // sleep ↔ wake (logical pair)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        RemoteButton("sleep",        Icons.Default.Bedtime,   Modifier.weight(1f), !isLoading) {
                            vm.action("sleep") }
                        RemoteButton("wake display", Icons.Default.LightMode, Modifier.weight(1f), !isLoading) {
                            vm.action("wake") }
                    }
                    Spacer(Modifier.height(8.dp))
                    RemoteButton("screenshot + describe", Icons.Default.Screenshot, Modifier.fillMaxWidth(),
                        !isLoading) { vm.action("screenshot") }
                }
            }

            // ── Mobile controls ────────────────────────────────────────────────
            if (isMobile) {

                if (isOffline) {
                    RemoteSection("device offline") {
                        Text("${selectedDevice?.hostname} is offline — commands queue and deliver when back online.",
                             style = MaterialTheme.typography.bodySmall, color = NxFg2)
                    }
                }

                RemoteSection("media") {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        RemoteButton("prev",       Icons.Default.SkipPrevious, Modifier.weight(1f), !isLoading) {
                            vm.mobileCommand("media", "previous") }
                        RemoteButton("play/pause", Icons.Default.PlayArrow,    Modifier.weight(2f), !isLoading) {
                            vm.mobileCommand("media", "play-pause") }
                        RemoteButton("next",       Icons.Default.SkipNext,     Modifier.weight(1f), !isLoading) {
                            vm.mobileCommand("media", "next") }
                    }
                }

                RemoteSection("volume") {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.VolumeDown, null, Modifier.size(18.dp), tint = NxFg2)
                        Slider(
                            value         = volumeSlider,
                            onValueChange = { volumeSlider = it },
                            onValueChangeFinished = {
                                vm.mobileCommand("volume", volumeSlider.roundToInt().toString()) },
                            valueRange    = 0f..100f,
                            modifier      = Modifier.weight(1f),
                            colors        = SliderDefaults.colors(
                                thumbColor         = NxOrange,
                                activeTrackColor   = NxOrange,
                                inactiveTrackColor = NxBorder,
                            ),
                        )
                        Icon(Icons.Default.VolumeUp, null, Modifier.size(18.dp), tint = NxFg2)
                        Text(
                            "${volumeSlider.roundToInt()}%",
                            style    = MaterialTheme.typography.labelSmall,
                            color    = NxFg2,
                            modifier = Modifier.widthIn(min = 36.dp),
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        RemoteButton("mute",   Icons.Default.VolumeOff, Modifier.weight(1f), !isLoading) {
                            vm.mobileCommand("volume", "0") }
                        RemoteButton("max",    Icons.Default.VolumeUp,  Modifier.weight(1f), !isLoading) {
                            vm.mobileCommand("volume", "100") }
                    }
                }

                RemoteSection("open") {
                    OutlinedTextField(
                        value         = urlInput,
                        onValueChange = { urlInput = it },
                        label         = { Text("URL or package name", color = NxFg2) },
                        modifier      = Modifier.fillMaxWidth(),
                        singleLine    = true,
                        shape         = RoundedCornerShape(4.dp),
                        colors        = nxFieldColors(),
                        textStyle     = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        RemoteButton("open URL", Icons.Default.OpenInNew, Modifier.weight(1f),
                            urlInput.isNotBlank() && !isLoading) {
                            vm.mobileCommand("open_url", urlInput.trim()) }
                        RemoteButton("open app", Icons.Default.Apps,      Modifier.weight(1f),
                            urlInput.isNotBlank() && !isLoading) {
                            vm.mobileCommand("open_app", urlInput.trim()) }
                    }
                }

                RemoteSection("clipboard") {
                    OutlinedTextField(
                        value         = clipboardInput,
                        onValueChange = { clipboardInput = it },
                        label         = { Text("text to copy to phone", color = NxFg2) },
                        modifier      = Modifier.fillMaxWidth(),
                        maxLines      = 3,
                        shape         = RoundedCornerShape(4.dp),
                        colors        = nxFieldColors(),
                        textStyle     = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(8.dp))
                    RemoteButton("copy to phone clipboard", Icons.Default.ContentCopy, Modifier.fillMaxWidth(),
                        clipboardInput.isNotBlank() && !isLoading) {
                        vm.mobileCommand("clip", clipboardInput.trim()) }
                }

                RemoteSection("notify") {
                    OutlinedTextField(
                        value         = notifyInput,
                        onValueChange = { notifyInput = it },
                        label         = { Text("notification text", color = NxFg2) },
                        modifier      = Modifier.fillMaxWidth(),
                        maxLines      = 2,
                        shape         = RoundedCornerShape(4.dp),
                        colors        = nxFieldColors(),
                        textStyle     = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(8.dp))
                    RemoteButton("send notification", Icons.Default.Notifications, Modifier.fillMaxWidth(),
                        notifyInput.isNotBlank() && !isLoading) {
                        vm.mobileCommand("notify", notifyInput.trim()) }
                }
            }

            // ── Result ─────────────────────────────────────────────────────────
            if (isLoading || result.isNotEmpty()) {
                RemoteSection("result") {
                    if (isLoading && result.isEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(14.dp), color = NxOrange, strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("working…", style = MaterialTheme.typography.bodySmall, color = NxFg2)
                        }
                    } else {
                        Text(
                            result,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace, fontSize = 12.sp),
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
    label:    String,
    icon:     androidx.compose.ui.graphics.vector.ImageVector,
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
        Icon(icon, null, modifier = Modifier.size(16.dp), tint = if (enabled) NxOrange else NxFg2)
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
