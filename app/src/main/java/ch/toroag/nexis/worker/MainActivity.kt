package ch.toroag.nexis.worker

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Check for updates on startup (runs in background)
        checkForUpdate()

        setContent {
            MaterialTheme {
                Surface {
                    NexisApp()
                }
            }
        }
    }

    private fun checkForUpdate() {
        lifecycleScope.launch(Dispatchers.IO) {
            val release = UpdateChecker.checkForUpdate() ?: return@launch
            withContext(Dispatchers.Main) {
                android.app.AlertDialog.Builder(this@MainActivity)
                    .setTitle("Update available")
                    .setMessage("NeXiS ${release.tag} is ready. Install now?")
                    .setPositiveButton("Install") { _, _ ->
                        lifecycleScope.launch(Dispatchers.IO) {
                            UpdateChecker.downloadAndInstall(this@MainActivity, release)
                        }
                    }
                    .setNegativeButton("Later", null)
                    .show()
            }
        }
    }
}

@Composable
private fun NexisApp() {
    val navController = rememberNavController()
    val prefs         = PreferencesRepository.get(androidx.compose.ui.platform.LocalContext.current)
    val token         by prefs.token.collectAsState(initial = null)

    // Determine start destination based on whether we have a stored token
    val startDest = if (token == null) "login" else when {
        token!!.isNotEmpty() -> "chat"
        else -> "login"
    }

    NavHost(navController = navController, startDestination = startDest) {
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
