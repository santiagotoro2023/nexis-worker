package ch.toroag.nexis.ios

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import ch.toroag.nexis.ios.data.PreferencesRepository
import ch.toroag.nexis.ios.theme.NexisTheme
import ch.toroag.nexis.ios.ui.chat.ChatScreen
import ch.toroag.nexis.ios.ui.commands.CommandsScreen
import ch.toroag.nexis.ios.ui.devices.DevicesScreen
import ch.toroag.nexis.ios.ui.history.HistoryScreen
import ch.toroag.nexis.ios.ui.hypervisor.HypervisorScreen
import ch.toroag.nexis.ios.ui.login.LoginScreen
import ch.toroag.nexis.ios.ui.memory.MemoryScreen
import ch.toroag.nexis.ios.ui.personality.PersonalityScreen
import ch.toroag.nexis.ios.ui.remote.RemoteScreen
import ch.toroag.nexis.ios.ui.schedules.SchedulesScreen
import ch.toroag.nexis.ios.ui.settings.SettingsScreen

@Composable
fun App() {
    NexisTheme {
        val prefs  = remember { PreferencesRepository.get() }
        val token  by prefs.token.collectAsState()
        val isAdmin by prefs.role.collectAsState()

        if (token.isEmpty()) {
            LoginScreen(prefs = prefs)
        } else {
            MainScaffold(prefs = prefs, isAdmin = isAdmin == "admin" || isAdmin == "creator")
        }
    }
}

@Composable
private fun MainScaffold(prefs: PreferencesRepository, isAdmin: Boolean) {
    var current by remember { mutableStateOf(Screen.Chat) }

    val navItems = listOf(
        Triple(Screen.Chat,        "Chat",       Icons.Default.Chat),
        Triple(Screen.Commands,    "Commands",   Icons.Default.Bolt),
        Triple(Screen.Devices,     "Devices",    Icons.Default.Devices),
        Triple(Screen.History,     "History",    Icons.Default.History),
        Triple(Screen.Settings,    "Settings",   Icons.Default.Settings),
    )

    Scaffold(
        bottomBar = {
            NavigationBar {
                navItems.forEach { (screen, label, icon) ->
                    NavigationBarItem(
                        selected     = current == screen,
                        onClick      = { current = screen },
                        icon         = { Icon(icon, contentDescription = label) },
                        label        = { Text(label) },
                    )
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (current) {
                Screen.Chat        -> ChatScreen(prefs)
                Screen.Commands    -> CommandsScreen(prefs)
                Screen.Personality -> PersonalityScreen(prefs)
                Screen.Devices     -> DevicesScreen(prefs)
                Screen.History     -> HistoryScreen(prefs)
                Screen.Memory      -> MemoryScreen(prefs)
                Screen.Schedules   -> SchedulesScreen(prefs)
                Screen.Remote      -> RemoteScreen(prefs)
                Screen.Hypervisor  -> HypervisorScreen(prefs)
                Screen.Settings    -> SettingsScreen(prefs, onLogout = { prefs.clearToken() })
                Screen.Login       -> {}
            }
        }
    }
}
