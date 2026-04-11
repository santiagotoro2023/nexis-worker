package ch.toroag.nexis.worker

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import ch.toroag.nexis.worker.data.PreferencesRepository
import ch.toroag.nexis.worker.ui.chat.ChatScreen
import ch.toroag.nexis.worker.ui.login.LoginScreen
import ch.toroag.nexis.worker.ui.settings.SettingsScreen
import ch.toroag.nexis.worker.util.UpdateChecker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.os.Bundle

// ── Startup state machine ─────────────────────────────────────────────────────
private sealed interface StartupState {
    object Checking   : StartupState                         // "Checking for updates…"
    data class Downloading(val progress: Int) : StartupState // "Downloading… 42%"
    object Installing : StartupState                         // "Installing…"
    object NeedsPerm  : StartupState                         // one-time permission screen
    object Ready      : StartupState                         // proceed to app
}

class MainActivity : ComponentActivity() {

    private val _startupState = mutableStateOf<StartupState>(StartupState.Checking)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val state by _startupState
                    AnimatedContent(targetState = state, label = "startup") { s ->
                        when (s) {
                            StartupState.Ready    -> NexisApp()
                            StartupState.NeedsPerm -> PermissionSetupScreen {
                                // User tapped "Grant" — open settings, then re-check on resume
                                UpdateChecker.openInstallPermissionSettings(this@MainActivity)
                            }
                            else -> UpdateSplashScreen(s)
                        }
                    }
                }
            }
        }

        // Run the update check after UI is ready
        lifecycleScope.launch(Dispatchers.IO) { runStartupSequence() }
    }

    /** Called when user returns from the Install Unknown Apps settings screen. */
    override fun onResume() {
        super.onResume()
        if (_startupState.value == StartupState.NeedsPerm) {
            // Re-check permission; if granted now, retry the update flow
            if (UpdateChecker.hasInstallPermission(this)) {
                lifecycleScope.launch(Dispatchers.IO) { runStartupSequence() }
            }
        }
    }

    private suspend fun runStartupSequence() {
        // 1. Check for an update
        val release = UpdateChecker.checkForUpdate()

        if (release == null) {
            // No update — go straight to the app
            withContext(Dispatchers.Main) { _startupState.value = StartupState.Ready }
            return
        }

        // 2. Update found — do we have permission to install silently?
        if (!UpdateChecker.hasInstallPermission(this)) {
            withContext(Dispatchers.Main) { _startupState.value = StartupState.NeedsPerm }
            return
        }

        // 3. Download with progress
        val apkFile = UpdateChecker.downloadApk(this, release) { pct ->
            lifecycleScope.launch(Dispatchers.Main) {
                _startupState.value = StartupState.Downloading(pct)
            }
        }

        if (apkFile == null) {
            // Download failed — skip update, open app normally
            withContext(Dispatchers.Main) { _startupState.value = StartupState.Ready }
            return
        }

        // 4. Install silently — Android will restart the app on success
        withContext(Dispatchers.Main) { _startupState.value = StartupState.Installing }
        delay(300) // let the UI render "Installing…" before we hand off to PackageInstaller
        UpdateChecker.installSilently(this, apkFile)

        // PackageInstaller handles the rest asynchronously via InstallResultReceiver.
        // If it fails, we fall through to Ready so the user isn't stuck.
        delay(8000)
        withContext(Dispatchers.Main) { _startupState.value = StartupState.Ready }
    }
}

// ── Update splash ─────────────────────────────────────────────────────────────

@Composable
private fun UpdateSplashScreen(state: StartupState) {
    Column(
        modifier              = Modifier.fillMaxSize().padding(40.dp),
        verticalArrangement   = Arrangement.Center,
        horizontalAlignment   = Alignment.CenterHorizontally,
    ) {
        Text("NeXiS", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(32.dp))

        when (state) {
            StartupState.Checking -> {
                CircularProgressIndicator()
                Spacer(Modifier.height(16.dp))
                Text("Checking for updates…",
                     style = MaterialTheme.typography.bodyMedium,
                     color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            is StartupState.Downloading -> {
                LinearProgressIndicator(
                    progress    = { state.progress / 100f },
                    modifier    = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                Text("Downloading update… ${state.progress}%",
                     style = MaterialTheme.typography.bodyMedium,
                     color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            StartupState.Installing -> {
                CircularProgressIndicator()
                Spacer(Modifier.height(16.dp))
                Text("Installing update…",
                     style = MaterialTheme.typography.bodyMedium,
                     color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            else -> {}
        }
    }
}

// ── One-time permission setup ─────────────────────────────────────────────────

@Composable
private fun PermissionSetupScreen(onGrant: () -> Unit) {
    Column(
        modifier              = Modifier.fillMaxSize().padding(40.dp),
        verticalArrangement   = Arrangement.Center,
        horizontalAlignment   = Alignment.CenterHorizontally,
    ) {
        Text("One-time setup", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))
        Text(
            "NeXiS needs permission to install updates automatically.\n\n" +
            "Tap \"Grant\" below, enable \"Allow from this source\" in the next screen, " +
            "then come back — this is the only time you'll ever need to do this.",
            style     = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color     = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(32.dp))
        Button(onClick = onGrant, modifier = Modifier.fillMaxWidth()) {
            Text("Grant permission")
        }
    }
}

// ── Main app nav ──────────────────────────────────────────────────────────────

@Composable
private fun NexisApp() {
    val navController = rememberNavController()
    val prefs         = PreferencesRepository.get(androidx.compose.ui.platform.LocalContext.current)
    val token         by prefs.token.collectAsState(initial = null)

    val startDest = when {
        token == null        -> "loading"   // still loading from DataStore
        token!!.isNotEmpty() -> "chat"
        else                 -> "login"
    }

    NavHost(navController = navController, startDestination = startDest) {
        composable("loading") {
            // Momentary blank while DataStore resolves; token state updates will trigger recompose
            Box(Modifier.fillMaxSize())
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
                onNavigateToSettings = { navController.navigate("settings") }
            )
        }
        composable("settings") {
            SettingsScreen(
                onBack   = { navController.popBackStack() },
                onLogout = {
                    navController.navigate("login") {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }
    }
}
