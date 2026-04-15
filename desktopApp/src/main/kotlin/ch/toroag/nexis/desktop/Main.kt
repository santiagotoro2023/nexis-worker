package ch.toroag.nexis.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import java.awt.Point
import ch.toroag.nexis.desktop.data.PreferencesRepository
import ch.toroag.nexis.desktop.ui.chat.ChatScreen
import ch.toroag.nexis.desktop.ui.chat.ChatViewModel
import ch.toroag.nexis.desktop.ui.devices.DevicesScreen
import ch.toroag.nexis.desktop.ui.devices.DevicesViewModel
import ch.toroag.nexis.desktop.ui.history.HistoryScreen
import ch.toroag.nexis.desktop.ui.history.HistoryViewModel
import ch.toroag.nexis.desktop.ui.login.LoginScreen
import ch.toroag.nexis.desktop.ui.login.LoginViewModel
import ch.toroag.nexis.desktop.ui.memory.MemoryScreen
import ch.toroag.nexis.desktop.ui.memory.MemoryViewModel
import ch.toroag.nexis.desktop.ui.remote.RemoteScreen
import ch.toroag.nexis.desktop.ui.remote.RemoteViewModel
import ch.toroag.nexis.desktop.ui.schedules.SchedulesScreen
import ch.toroag.nexis.desktop.ui.schedules.SchedulesViewModel
import ch.toroag.nexis.desktop.ui.settings.SettingsScreen
import ch.toroag.nexis.desktop.ui.settings.SettingsViewModel
import ch.toroag.nexis.desktop.ui.theme.NexisTheme
import ch.toroag.nexis.desktop.ui.theme.*
import ch.toroag.nexis.desktop.util.SystemTrayManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

enum class Screen(
    val label: String,
    val icon:  ImageVector,
) {
    Chat      ("chat",      Icons.Default.Chat),
    Remote    ("remote",    Icons.Default.Computer),
    Memory    ("memory",    Icons.Default.Psychology),
    History   ("history",  Icons.Default.History),
    Schedules ("schedules", Icons.Default.Schedule),
    Devices   ("devices",   Icons.Default.Devices),
    Settings  ("settings",  Icons.Default.Settings),
}

fun main() = application {
    val windowState = rememberWindowState(width = 1100.dp, height = 720.dp)
    var isVisible   by remember { mutableStateOf(true) }

    // Install tray icon once; open/quit callbacks talk to the Compose application
    DisposableEffect(Unit) {
        SystemTrayManager.install(
            onOpen = { isVisible = true },
            onQuit = {
                SystemTrayManager.remove()
                exitApplication()
            },
        )
        onDispose { SystemTrayManager.remove() }
    }

    Window(
        onCloseRequest = {
            if (SystemTrayManager.isSupported) {
                isVisible = false
                SystemTrayManager.notify("NeXiS", "Running in background — click the tray icon to reopen")
            } else {
                SystemTrayManager.remove()
                exitApplication()
            }
        },
        title       = "NeXiS Worker",
        state       = windowState,
        visible     = isVisible,
        undecorated = true,
    ) {
        // 'window' is the underlying ComposeWindow (AWT), accessible here in FrameWindowScope.
        // Capture it so we can move the window from within the drag modifier below.
        val awtWindow = window

        NexisTheme {
            Column(Modifier.fillMaxSize()) {
                // ── Custom title bar ───────────────────────────────────────────
                // Drag-to-move implemented via AWT mouse coordinates rather than
                // WindowDraggableArea (not available in this Compose version).
                var dragOrigin by remember { mutableStateOf<Point?>(null) }

                Column(
                    Modifier.fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    when (event.type) {
                                        PointerEventType.Press -> {
                                            dragOrigin = java.awt.MouseInfo.getPointerInfo().location
                                        }
                                        PointerEventType.Move -> {
                                            val origin = dragOrigin
                                            if (origin != null && event.changes.any { it.pressed }) {
                                                val cur = java.awt.MouseInfo.getPointerInfo().location
                                                awtWindow.setLocation(
                                                    awtWindow.x + (cur.x - origin.x),
                                                    awtWindow.y + (cur.y - origin.y),
                                                )
                                                dragOrigin = cur
                                            }
                                        }
                                        PointerEventType.Release -> dragOrigin = null
                                        else -> {}
                                    }
                                }
                            }
                        }
                ) {
                    Row(
                        Modifier.fillMaxWidth().height(36.dp)
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "NeXiS Worker",
                            style = MaterialTheme.typography.labelMedium,
                            color = NxFg2,
                            modifier = Modifier.weight(1f),
                        )
                        // Minimise
                        Box(
                            Modifier.size(28.dp).clickable { windowState.isMinimized = true },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("─", color = NxFg2, style = MaterialTheme.typography.labelSmall)
                        }
                        // Close / hide to tray
                        Box(
                            Modifier.size(28.dp).clickable {
                                if (SystemTrayManager.isSupported) isVisible = false
                                else { SystemTrayManager.remove(); exitApplication() }
                            },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("✕", color = NxFg2, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    HorizontalDivider(color = NxBorder, thickness = 0.5.dp)
                }
                App()
            }
        }
    }
}

@Composable
private fun App() {
    val prefs = remember { PreferencesRepository.get() }

    // Determine initial auth state synchronously (cold read from prefs)
    val hasToken = remember {
        runBlocking { prefs.token.first().isNotEmpty() }
    }
    var isLoggedIn by remember { mutableStateOf(hasToken) }

    if (!isLoggedIn) {
        val loginVm = remember { LoginViewModel() }
        LoginScreen(
            onLoginSuccess = { isLoggedIn = true },
            vm             = loginVm,
        )
    } else {
        MainShell(onLogout = { isLoggedIn = false })
    }
}

@Composable
private fun MainShell(onLogout: () -> Unit) {
    // Hoist all VMs here so they survive navigation
    val chatVm      = remember { ChatViewModel() }
    val remoteVm    = remember { RemoteViewModel() }
    val devicesVm   = remember { DevicesViewModel() }
    val settingsVm  = remember { SettingsViewModel() }
    val memoryVm    = remember { MemoryViewModel() }
    val historyVm   = remember { HistoryViewModel() }
    val schedulesVm = remember { SchedulesViewModel() }

    var currentScreen by remember { mutableStateOf(Screen.Chat) }

    Row(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {

        // ── Sidebar ────────────────────────────────────────────────────────────
        Surface(
            color           = MaterialTheme.colorScheme.surface,
            shadowElevation = 2.dp,
            modifier        = Modifier.width(200.dp).fillMaxHeight(),
        ) {
            Column(Modifier.fillMaxSize().padding(top = 16.dp)) {
                // Logo / brand
                Row(
                    Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "NeXiS",
                        style  = MaterialTheme.typography.titleLarge,
                        color  = NxOrange,
                    )
                }

                HorizontalDivider(color = NxBorder, thickness = 0.5.dp)
                Spacer(Modifier.height(8.dp))

                // Nav items
                Screen.entries.forEach { screen ->
                    val selected = currentScreen == screen
                    NavItem(
                        label    = screen.label,
                        icon     = screen.icon,
                        selected = selected,
                        onClick  = { currentScreen = screen },
                    )
                }
            }
        }

        // ── Content ────────────────────────────────────────────────────────────
        Box(Modifier.weight(1f).fillMaxHeight()) {
            when (currentScreen) {
                Screen.Chat      -> ChatScreen(vm = chatVm)
                Screen.Remote    -> RemoteScreen(vm = remoteVm)
                Screen.Memory    -> MemoryScreen(vm = memoryVm)
                Screen.History   -> HistoryScreen(vm = historyVm)
                Screen.Schedules -> SchedulesScreen(vm = schedulesVm)
                Screen.Devices   -> DevicesScreen(vm = devicesVm)
                Screen.Settings  -> SettingsScreen(
                    vm       = settingsVm,
                    onLogout = onLogout,
                )
            }
        }
    }
}

@Composable
private fun NavItem(
    label:    String,
    icon:     ImageVector,
    selected: Boolean,
    onClick:  () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (selected) NxDim else androidx.compose.ui.graphics.Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint     = if (selected) NxOrange else NxFg2,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) NxFg else NxFg2,
        )
    }
}
