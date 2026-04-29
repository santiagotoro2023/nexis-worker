package ch.toroag.nexis.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import java.awt.Point
import ch.toroag.nexis.desktop.data.PreferencesRepository
import ch.toroag.nexis.desktop.ui.chat.ChatScreen
import ch.toroag.nexis.desktop.ui.chat.ChatViewModel
import ch.toroag.nexis.desktop.ui.devices.DevicesScreen
import ch.toroag.nexis.desktop.ui.devices.DevicesViewModel
import ch.toroag.nexis.desktop.ui.hypervisor.HypervisorScreen
import ch.toroag.nexis.desktop.ui.hypervisor.HypervisorViewModel
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
import ch.toroag.nexis.desktop.ui.theme.NexisEyeLogo
import ch.toroag.nexis.desktop.util.SystemTrayManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

enum class Screen(
    val label: String,
    val icon:  ImageVector,
) {
    Chat       ("CHAT",       Icons.Default.Chat),
    Remote     ("REMOTE",     Icons.Default.Computer),
    Memories   ("MEMORIES",   Icons.Default.Psychology),
    History    ("HISTORY",    Icons.Default.History),
    Schedules  ("SCHEDULES",  Icons.Default.Schedule),
    Devices    ("DEVICES",    Icons.Default.Devices),
    Hypervisor ("HYPERVISOR", Icons.Default.Dns),
    Settings   ("SETTINGS",   Icons.Default.Settings),
}

fun main() = application {
    val windowState = rememberWindowState(width = 1200.dp, height = 760.dp)
    var isVisible   by remember { mutableStateOf(true) }

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
                SystemTrayManager.notify("Nexis", "Running in background — click the tray icon to reopen")
            } else {
                SystemTrayManager.remove()
                exitApplication()
            }
        },
        title       = "Nexis Worker",
        state       = windowState,
        visible     = isVisible,
        undecorated = true,
    ) {
        val awtWindow = window
        LaunchedEffect(Unit) {
            runCatching {
                val img = javax.imageio.ImageIO.read(
                    Thread.currentThread().contextClassLoader
                        .getResourceAsStream("icon.png")
                )
                if (img != null) awtWindow.iconImage = img
            }
        }

        NexisTheme {
            Column(Modifier.fillMaxSize().background(NxBg)) {
                // ── Custom title bar ───────────────────────────────────────────
                var dragOrigin by remember { mutableStateOf<Point?>(null) }

                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(36.dp)
                        .background(NxBg2)
                        .border(width = 0.5.dp, color = NxBorder, shape = RoundedCornerShape(0.dp))
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
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "NEXIS WORKER  ·  NX-WRK · BUILD 1.0.6",
                        fontFamily    = FontFamily.Monospace,
                        fontSize      = 10.sp,
                        letterSpacing = 0.15.sp,
                        color         = NxFg2,
                        modifier      = Modifier.weight(1f),
                    )
                    // Minimise
                    Box(
                        Modifier.size(32.dp).clickable { windowState.isMinimized = true },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("─", color = NxFg2, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                    }
                    // Close / hide to tray
                    Box(
                        Modifier.size(32.dp).clickable {
                            if (SystemTrayManager.isSupported) isVisible = false
                            else { SystemTrayManager.remove(); exitApplication() }
                        },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("✕", color = NxFg2, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                    }
                }

                App()
            }
        }
    }
}

@Composable
private fun App() {
    val prefs = remember { PreferencesRepository.get() }
    val hasToken = remember { runBlocking { prefs.token.first().isNotEmpty() } }
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
    val chatVm       = remember { ChatViewModel() }
    val remoteVm     = remember { RemoteViewModel() }
    val devicesVm    = remember { DevicesViewModel() }
    val settingsVm   = remember { SettingsViewModel() }
    val memoryVm     = remember { MemoryViewModel() }
    val historyVm    = remember { HistoryViewModel() }
    val schedulesVm  = remember { SchedulesViewModel() }
    val hypervisorVm = remember { HypervisorViewModel() }

    var currentScreen by remember { mutableStateOf(Screen.Chat) }

    Row(Modifier.fillMaxSize().background(NxBg)) {

        // ── Sidebar 240dp ──────────────────────────────────────────────────────
        NexisSidebar(
            currentScreen = currentScreen,
            onNavigate    = { currentScreen = it },
            onLogout      = onLogout,
        )

        // ── Content ────────────────────────────────────────────────────────────
        Box(Modifier.weight(1f).fillMaxHeight()) {
            when (currentScreen) {
                Screen.Chat       -> ChatScreen(vm = chatVm)
                Screen.Remote     -> RemoteScreen(vm = remoteVm)
                Screen.Memories   -> MemoryScreen(vm = memoryVm)
                Screen.History    -> HistoryScreen(vm = historyVm)
                Screen.Schedules  -> SchedulesScreen(vm = schedulesVm)
                Screen.Devices    -> DevicesScreen(vm = devicesVm)
                Screen.Hypervisor -> HypervisorScreen(vm = hypervisorVm)
                Screen.Settings   -> SettingsScreen(vm = settingsVm, onLogout = onLogout)
            }
        }
    }
}

// ── Sidebar ───────────────────────────────────────────────────────────────────

@Composable
private fun NexisSidebar(
    currentScreen: Screen,
    onNavigate:    (Screen) -> Unit,
    onLogout:      () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(240.dp)
            .fillMaxHeight()
            .background(NxBg2)
            .border(width = 1.dp, color = NxBorder,
                    shape = RoundedCornerShape(topEnd = 0.dp, bottomEnd = 0.dp)),
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NexisEyeLogo(size = 28.dp)
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    "NEXIS",
                    fontFamily    = FontFamily.Monospace,
                    fontSize      = 13.sp,
                    fontWeight    = FontWeight.Bold,
                    letterSpacing = 0.2.sp,
                    color         = NxFg,
                )
                Text(
                    "WORKER",
                    fontFamily    = FontFamily.Monospace,
                    fontSize      = 9.sp,
                    letterSpacing = 0.15.sp,
                    color         = NxFg2,
                )
            }
        }

        HorizontalDivider(color = NxBorder, thickness = 1.dp)
        Spacer(Modifier.height(8.dp))

        // Nav items
        Screen.entries.forEach { screen ->
            SidebarNavItem(
                screen    = screen,
                selected  = currentScreen == screen,
                onClick   = { onNavigate(screen) },
            )
        }

        Spacer(Modifier.weight(1f))
        HorizontalDivider(color = NxBorder, thickness = 1.dp)

        // Version
        Text(
            "NX-WRK · BUILD 1.0.6",
            fontFamily    = FontFamily.Monospace,
            fontSize      = 9.sp,
            letterSpacing = 0.1.sp,
            color         = NxFg2.copy(alpha = 0.6f),
            modifier      = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )

        // Logout
        LogoutNavItem(onClick = onLogout)
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun SidebarNavItem(
    screen:   Screen,
    selected: Boolean,
    onClick:  () -> Unit,
) {
    val shape             = RoundedCornerShape(12.dp)
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered         by interactionSource.collectIsHoveredAsState()

    val bgColor = when {
        selected  -> NxOrange.copy(alpha = 0.08f)
        isHovered -> NxDim
        else      -> androidx.compose.ui.graphics.Color.Transparent
    }
    val borderColor = when {
        selected  -> NxOrange.copy(alpha = 0.18f)
        else      -> androidx.compose.ui.graphics.Color.Transparent
    }
    val textColor = if (selected) NxOrange else NxFg2

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .clip(shape)
            .background(bgColor, shape)
            .border(1.dp, borderColor, shape)
            .hoverable(interactionSource)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            screen.icon,
            contentDescription = screen.label,
            tint     = textColor,
            modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            screen.label,
            fontFamily    = FontFamily.Monospace,
            fontSize      = 10.sp,
            letterSpacing = 0.12.sp,
            fontWeight    = if (selected) FontWeight.Bold else FontWeight.Normal,
            color         = textColor,
        )
    }
}

@Composable
private fun LogoutNavItem(onClick: () -> Unit) {
    val shape             = RoundedCornerShape(12.dp)
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered         by interactionSource.collectIsHoveredAsState()

    val textColor = if (isHovered) NxRed else NxFg2

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .clip(shape)
            .background(if (isHovered) NxRed.copy(alpha = 0.06f) else androidx.compose.ui.graphics.Color.Transparent, shape)
            .hoverable(interactionSource)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Default.Logout,
            contentDescription = "Logout",
            tint     = textColor,
            modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            "LOGOUT",
            fontFamily    = FontFamily.Monospace,
            fontSize      = 10.sp,
            letterSpacing = 0.12.sp,
            color         = textColor,
        )
    }
}
