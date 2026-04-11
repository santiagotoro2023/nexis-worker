package ch.toroag.nexis.worker.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack:   () -> Unit,
    onLogout: () -> Unit,
    vm: SettingsViewModel = viewModel(),
) {
    val baseUrl by vm.baseUrl.collectAsState()
    val status  by vm.status.collectAsState()

    var reAuthPw     by remember { mutableStateOf("") }
    var showReAuthPw by remember { mutableStateOf(false) }

    LaunchedEffect(status) {
        if (status != null) {
            kotlinx.coroutines.delay(3000)
            vm.clearStatus()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).padding(16.dp).fillMaxSize(),
               verticalArrangement = Arrangement.spacedBy(16.dp)) {

            // Current server info
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Controller", style = MaterialTheme.typography.labelMedium,
                         color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(4.dp))
                    Text(baseUrl.ifEmpty { "Not configured" },
                         style = MaterialTheme.typography.bodyMedium)
                }
            }

            // Re-authenticate (e.g. after password change)
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Re-authenticate", style = MaterialTheme.typography.labelMedium,
                         color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(8.dp))
                    Text("Use this if you changed your NeXiS password.",
                         style = MaterialTheme.typography.bodySmall,
                         color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value         = reAuthPw,
                        onValueChange = { reAuthPw = it },
                        label         = { Text("New password") },
                        modifier      = Modifier.fillMaxWidth(),
                        singleLine    = true,
                        visualTransformation = PasswordVisualTransformation(),
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick  = { vm.reAuthenticate(reAuthPw) { reAuthPw = "" } },
                        modifier = Modifier.fillMaxWidth(),
                        enabled  = reAuthPw.isNotBlank(),
                    ) { Text("Re-authenticate") }
                }
            }

            if (status != null) {
                Text(status!!, color = MaterialTheme.colorScheme.primary,
                     style = MaterialTheme.typography.bodySmall)
            }

            Spacer(Modifier.weight(1f))

            // Logout
            OutlinedButton(
                onClick  = { vm.logout(onLogout) },
                modifier = Modifier.fillMaxWidth(),
                colors   = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error),
            ) { Text("Disconnect") }
        }
    }
}
