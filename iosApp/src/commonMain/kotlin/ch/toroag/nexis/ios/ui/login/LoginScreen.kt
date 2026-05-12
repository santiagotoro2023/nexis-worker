package ch.toroag.nexis.ios.ui.login

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ch.toroag.nexis.ios.data.NexisApiService
import ch.toroag.nexis.ios.data.PreferencesRepository
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(prefs: PreferencesRepository) {
    val scope   = rememberCoroutineScope()
    val api     = remember { NexisApiService() }
    var url     by remember { mutableStateOf(prefs.baseUrl.value) }
    var user    by remember { mutableStateOf("") }
    var pass    by remember { mutableStateOf("") }
    var showPw  by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var error   by remember { mutableStateOf("") }

    Column(
        modifier            = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("NeXiS", fontSize = 36.sp, fontWeight = FontWeight.Bold,
             color = MaterialTheme.colorScheme.primary)
        Text("Worker", fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        Spacer(Modifier.height(40.dp))
        OutlinedTextField(value = url, onValueChange = { url = it },
            label = { Text("Server URL") }, modifier = Modifier.fillMaxWidth(),
            singleLine = true)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(value = user, onValueChange = { user = it },
            label = { Text("Username") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = pass, onValueChange = { pass = it },
            label = { Text("Password") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
            visualTransformation = if (showPw) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            trailingIcon = {
                IconButton(onClick = { showPw = !showPw }) {
                    Icon(if (showPw) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                         contentDescription = null)
                }
            },
        )
        if (error.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(error, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
        }
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = {
                loading = true; error = ""
                scope.launch {
                    runCatching { api.getToken(url.trimEnd('/'), user, pass) }
                        .onSuccess { result ->
                            prefs.saveCredentials(url, result.token, user, result.role)
                        }
                        .onFailure { e -> error = e.message ?: "Login failed" }
                    loading = false
                }
            },
            enabled  = !loading && url.isNotEmpty() && user.isNotEmpty() && pass.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().height(50.dp),
        ) {
            if (loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            else Text("Sign In")
        }
    }
}
