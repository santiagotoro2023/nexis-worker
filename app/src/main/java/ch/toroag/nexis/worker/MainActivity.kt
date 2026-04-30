package ch.toroag.nexis.worker

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import ch.toroag.nexis.worker.data.PreferencesRepository
import ch.toroag.nexis.worker.ui.chat.ChatScreen
import ch.toroag.nexis.worker.ui.history.HistoryScreen
import ch.toroag.nexis.worker.ui.login.LoginScreen
import ch.toroag.nexis.worker.ui.memories.MemoriesScreen
import ch.toroag.nexis.worker.ui.schedules.SchedulesScreen
import ch.toroag.nexis.worker.ui.devices.DevicesScreen
import ch.toroag.nexis.worker.ui.hypervisor.HypervisorScreen
import ch.toroag.nexis.worker.ui.remote.RemoteScreen
import ch.toroag.nexis.worker.ui.settings.SettingsScreen
import ch.toroag.nexis.worker.ui.voice.VoiceScreen
import ch.toroag.nexis.worker.ui.theme.NexisTheme
import ch.toroag.nexis.worker.ui.theme.NexisEyeLogo
import ch.toroag.nexis.worker.ui.theme.*
import ch.toroag.nexis.worker.service.NexisBackgroundService
import ch.toroag.nexis.worker.util.UpdateChecker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Base64
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

// ── Startup state machine ─────────────────────────────────────────────────────
private sealed interface StartupState {
    object Checking   : StartupState
    data class Downloading(val progress: Int) : StartupState
    object Installing : StartupState
    object NeedsPerm  : StartupState
    object Ready      : StartupState
}

/** Payload extracted from an ACTION_SEND share intent. */
data class SharePayload(
    val text:      String?  = null,
    val imageUri:  Uri?     = null,
    val imageB64:  String?  = null,
    val imageMime: String?  = null,
)

class MainActivity : ComponentActivity() {

    private val _startupState = mutableStateOf<StartupState>(StartupState.Checking)
    private val _sharePayload = mutableStateOf<SharePayload?>(null)

    private val notifPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* result ignored — best-effort */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
        ) {
            notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        handleShareIntent(intent)

        setContent {
            NexisTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = NxBg) {
                    val state by _startupState
                    val share by _sharePayload
                    AnimatedContent(targetState = state, label = "startup") { s ->
                        when (s) {
                            StartupState.Ready    -> NexisApp(sharePayload = share, onShareConsumed = { _sharePayload.value = null })
                            StartupState.NeedsPerm -> PermissionSetupScreen {
                                UpdateChecker.openInstallPermissionSettings(this@MainActivity)
                            }
                            else -> UpdateSplashScreen(s)
                        }
                    }
                }
            }
        }

        lifecycleScope.launch(Dispatchers.IO) { runStartupSequence() }

        lifecycleScope.launch {
            val prefs = PreferencesRepository.get(this@MainActivity)
            prefs.token.collect { token ->
                if (token.isNotEmpty()) {
                    NexisBackgroundService.start(this@MainActivity)
                } else {
                    NexisBackgroundService.stop(this@MainActivity)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShareIntent(intent)
    }

    private fun handleShareIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) return
        val mimeType = intent.type ?: return
        when {
            mimeType == "text/plain" -> {
                val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return
                _sharePayload.value = SharePayload(text = text)
            }
            mimeType.startsWith("image/") -> {
                @Suppress("DEPRECATION")
                val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM) ?: return
                lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        val bytes = contentResolver.openInputStream(uri)?.readBytes() ?: return@launch
                        val b64   = "data:$mimeType;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
                        _sharePayload.value = SharePayload(imageUri = uri, imageB64 = b64, imageMime = mimeType)
                    } catch (_: Exception) {}
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (_startupState.value == StartupState.NeedsPerm) {
            if (UpdateChecker.hasInstallPermission(this)) {
                lifecycleScope.launch(Dispatchers.IO) { runStartupSequence() }
            }
        }
    }

    private suspend fun runStartupSequence() {
        val release = UpdateChecker.checkForUpdate()
        if (release == null) {
            withContext(Dispatchers.Main) { _startupState.value = StartupState.Ready }
            return
        }
        if (!UpdateChecker.hasInstallPermission(this)) {
            withContext(Dispatchers.Main) { _startupState.value = StartupState.NeedsPerm }
            return
        }
        val apkFile = UpdateChecker.downloadApk(this, release) { pct ->
            lifecycleScope.launch(Dispatchers.Main) {
                _startupState.value = StartupState.Downloading(pct)
            }
        }
        if (apkFile == null) {
            withContext(Dispatchers.Main) { _startupState.value = StartupState.Ready }
            return
        }
        withContext(Dispatchers.Main) { _startupState.value = StartupState.Installing }
        delay(300)
        val installed = runCatching { UpdateChecker.installSilently(this, apkFile) }.isSuccess
        if (!installed) {
            withContext(Dispatchers.Main) { _startupState.value = StartupState.Ready }
            return
        }
        delay(8000)
        withContext(Dispatchers.Main) { _startupState.value = StartupState.Ready }
    }
}

// ── Update splash ─────────────────────────────────────────────────────────────

@Composable
private fun UpdateSplashScreen(state: StartupState) {
    Box(
        modifier         = Modifier.fillMaxSize().background(NxBg),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier            = Modifier.padding(40.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            NexisEyeLogo(size = 56.dp)
            Spacer(Modifier.height(16.dp))
            Text(
                "NEXIS",
                fontFamily    = FontFamily.Monospace,
                fontSize      = 26.sp,
                fontWeight    = FontWeight.Bold,
                letterSpacing = 0.4.sp,
                color         = NxFg,
            )
            Text(
                "NX-WRK · BUILD 1.0.6",
                fontFamily    = FontFamily.Monospace,
                fontSize      = 9.sp,
                letterSpacing = 0.15.sp,
                color         = NxFg2,
            )
            Spacer(Modifier.height(40.dp))

            when (state) {
                StartupState.Checking -> {
                    CircularProgressIndicator(color = NxOrange, strokeWidth = 2.dp)
                    Spacer(Modifier.height(16.dp))
                    Text("CHECKING FOR UPDATES",
                         fontFamily = FontFamily.Monospace, fontSize = 10.sp,
                         letterSpacing = 0.15.sp, color = NxFg2)
                }
                is StartupState.Downloading -> {
                    LinearProgressIndicator(
                        progress    = { state.progress / 100f },
                        modifier    = Modifier.fillMaxWidth(),
                        color       = NxOrange,
                        trackColor  = NxBorder,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("DOWNLOADING UPDATE… ${state.progress}%",
                         fontFamily = FontFamily.Monospace, fontSize = 10.sp,
                         letterSpacing = 0.15.sp, color = NxFg2)
                }
                StartupState.Installing -> {
                    CircularProgressIndicator(color = NxOrange, strokeWidth = 2.dp)
                    Spacer(Modifier.height(16.dp))
                    Text("INSTALLING UPDATE",
                         fontFamily = FontFamily.Monospace, fontSize = 10.sp,
                         letterSpacing = 0.15.sp, color = NxFg2)
                }
                else -> {}
            }
        }
    }
}

// ── Permission setup ──────────────────────────────────────────────────────────

@Composable
private fun PermissionSetupScreen(onGrant: () -> Unit) {
    Box(
        modifier         = Modifier.fillMaxSize().background(NxBg),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp)
                .background(NxBg3, RoundedCornerShape(16.dp))
                .border(1.dp, NxBorder, RoundedCornerShape(16.dp))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "ONE-TIME SETUP",
                fontFamily    = FontFamily.Monospace,
                fontSize      = 9.sp,
                fontWeight    = FontWeight.Bold,
                letterSpacing = 0.18.sp,
                color         = NxOrange,
                modifier      = Modifier.padding(bottom = 12.dp),
            )
            Text(
                "NeXiS needs permission to install updates automatically.\n\n" +
                "Tap GRANT below, enable 'Allow from this source' in the next screen, " +
                "then come back — this is the only time you will need to do this.",
                fontFamily = FontFamily.Monospace,
                fontSize   = 13.sp,
                color      = NxFg2,
                textAlign  = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
            Button(
                onClick  = onGrant,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape    = RoundedCornerShape(12.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = NxOrange, contentColor = NxBg),
            ) {
                Text(
                    "GRANT PERMISSION",
                    fontFamily    = FontFamily.Monospace,
                    fontWeight    = FontWeight.Bold,
                    letterSpacing = 0.15.sp,
                    fontSize      = 11.sp,
                )
            }
        }
    }
}

// ── Drawer destinations ───────────────────────────────────────────────────────

private data class NavDestination(
    val route: String,
    val label: String,
    val icon:  ImageVector,
)

private val navDestinations = listOf(
    NavDestination("chat",       "Chat",       Icons.Default.Chat),
    NavDestination("remote",     "Remote",     Icons.Default.Computer),
    NavDestination("schedules",  "Schedules",  Icons.Default.Schedule),
    NavDestination("devices",    "Devices",    Icons.Default.Devices),
    NavDestination("hypervisor", "Hypervisor", Icons.Default.Dns),
    NavDestination("settings",   "Settings",   Icons.Default.Settings),
)

// ── Main app ──────────────────────────────────────────────────────────────────

@Composable
private fun NexisApp(
    sharePayload:    SharePayload? = null,
    onShareConsumed: () -> Unit    = {},
) {
    val context       = androidx.compose.ui.platform.LocalContext.current
    val navController = rememberNavController()
    val prefs         = PreferencesRepository.get(context)
    val token         by prefs.token.collectAsState(initial = null)
    val drawerState   = rememberDrawerState(DrawerValue.Closed)
    val scope         = rememberCoroutineScope()

    val startDest = when {
        token == null        -> "loading"
        token!!.isNotEmpty() -> "chat"
        else                 -> "login"
    }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute   = backStackEntry?.destination?.route
    val showDrawer     = currentRoute != null && currentRoute != "login" && currentRoute != "loading"

    fun navigate(route: String) {
        scope.launch { drawerState.close() }
        navController.navigate(route) {
            popUpTo("chat") { saveState = true }
            launchSingleTop = true
            restoreState    = true
        }
    }

    ModalNavigationDrawer(
        drawerState     = drawerState,
        gesturesEnabled = showDrawer,
        drawerContent   = {
            if (showDrawer) {
                ModalDrawerSheet(
                    drawerContainerColor = NxBg2,
                    drawerTonalElevation = 0.dp,
                    windowInsets         = WindowInsets(0),
                    modifier             = Modifier.width(260.dp),
                ) {
                    // Header
                    Row(
                        modifier            = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 18.dp),
                        verticalAlignment   = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        NexisEyeLogo(size = 28.dp)
                        Column {
                            Text("NEXIS",
                                fontFamily    = FontFamily.Monospace,
                                fontSize      = 15.sp,
                                fontWeight    = FontWeight.Bold,
                                color         = NxFg,
                                letterSpacing = 0.3.sp)
                            Text("NX-WRK",
                                fontFamily    = FontFamily.Monospace,
                                fontSize      = 9.sp,
                                color         = NxFg2,
                                letterSpacing = 0.15.sp)
                        }
                    }
                    HorizontalDivider(color = NxBorder, thickness = 1.dp)
                    Spacer(Modifier.height(8.dp))
                    navDestinations.forEach { dest ->
                        val selected = currentRoute == dest.route ||
                            (dest.route == "chat" && currentRoute == "voice")
                        NavigationDrawerItem(
                            icon   = {
                                Icon(dest.icon,
                                    contentDescription = dest.label,
                                    modifier           = Modifier.size(18.dp))
                            },
                            label  = {
                                Text(dest.label,
                                    fontFamily    = FontFamily.Monospace,
                                    fontSize      = 11.sp,
                                    letterSpacing = 0.1.sp,
                                    fontWeight    = if (selected) FontWeight.Bold else FontWeight.Normal)
                            },
                            selected = selected,
                            onClick  = { navigate(dest.route) },
                            modifier = Modifier.padding(horizontal = 8.dp),
                            colors   = NavigationDrawerItemDefaults.colors(
                                selectedContainerColor = NxOrange.copy(alpha = 0.10f),
                                selectedIconColor      = NxOrange,
                                selectedTextColor      = NxOrange,
                                unselectedIconColor    = NxFg2,
                                unselectedTextColor    = NxFg2,
                            ),
                        )
                    }
                }
            }
        },
    ) {
        NavHost(
            navController    = navController,
            startDestination = startDest,
            modifier         = Modifier.fillMaxSize(),
        ) {
            composable("loading") {
                Box(Modifier.fillMaxSize().background(NxBg))
            }
            composable("login") {
                LoginScreen(
                    onLoginSuccess = {
                        navController.navigate("chat") {
                            popUpTo("login") { inclusive = true }
                        }
                    }
                )
            }
            composable("chat") {
                ChatScreen(
                    onOpenDrawer    = { scope.launch { drawerState.open() } },
                    onNavigateToVoice = { navController.navigate("voice") },
                    sharePayload    = sharePayload,
                    onShareConsumed = onShareConsumed,
                )
            }
            composable("settings") {
                SettingsScreen(
                    onBack                = { navController.popBackStack() },
                    onLogout              = {
                        navController.navigate("login") {
                            popUpTo(0) { inclusive = true }
                        }
                    },
                    onNavigateToMemories  = { navController.navigate("memories") },
                    onNavigateToHistory   = { navController.navigate("history") },
                    onNavigateToSchedules = { navController.navigate("schedules") },
                    onNavigateToDevices   = { navController.navigate("devices") },
                    onNavigateToHypervisor = { navController.navigate("hypervisor") },
                )
            }
            composable("voice") {
                VoiceScreen(onBack = { navController.popBackStack() })
            }
            composable("memories") {
                MemoriesScreen(onBack = { navController.popBackStack() })
            }
            composable("history") {
                HistoryScreen(
                    onBack          = { navController.popBackStack() },
                    onSessionLoaded = {
                        navController.navigate("chat") {
                            popUpTo("chat") { inclusive = true }
                        }
                    },
                )
            }
            composable("schedules") {
                SchedulesScreen(onBack = { navController.popBackStack() })
            }
            composable("remote") {
                RemoteScreen(onBack = { navController.popBackStack() })
            }
            composable("devices") {
                DevicesScreen(onBack = { navController.popBackStack() })
            }
            composable("hypervisor") {
                HypervisorScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}
