package ch.toroag.nexis.desktop.ui.login

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ch.toroag.nexis.desktop.ui.theme.*

@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    vm: LoginViewModel = remember { LoginViewModel() },
) {
    val uiState by vm.uiState.collectAsState()
    var url      by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPw   by remember { mutableStateOf(false) }

    LaunchedEffect(uiState) {
        if (uiState is LoginState.Success) onLoginSuccess()
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            Modifier.width(360.dp),
            verticalArrangement   = Arrangement.Center,
            horizontalAlignment   = Alignment.Start,
        ) {
            Text(
                "NEXIS",
                style         = MaterialTheme.typography.headlineLarge,
                color         = NxOrange,
                fontWeight    = FontWeight.Bold,
                letterSpacing = 3.sp,
            )
            Text(
                "connect to controller",
                style = MaterialTheme.typography.labelSmall,
                color = NxFg2,
            )

            Spacer(Modifier.height(40.dp))

            Text("server url", style = MaterialTheme.typography.labelMedium, color = NxFg2)
            Spacer(Modifier.height(4.dp))
            OutlinedTextField(
                value         = url,
                onValueChange = { url = it },
                placeholder   = {
                    Text("192.168.1.x:8443  or  nexis.example.com",
                         color = NxFg2, style = MaterialTheme.typography.labelSmall)
                },
                modifier  = Modifier.fillMaxWidth(),
                singleLine = true,
                shape      = RoundedCornerShape(12.dp),
                colors     = nxFieldColors(),
                textStyle  = MaterialTheme.typography.bodyMedium,
            )

            Spacer(Modifier.height(16.dp))

            Text("password", style = MaterialTheme.typography.labelMedium, color = NxFg2)
            Spacer(Modifier.height(4.dp))
            OutlinedTextField(
                value         = password,
                onValueChange = { password = it },
                modifier      = Modifier.fillMaxWidth(),
                singleLine    = true,
                shape         = RoundedCornerShape(12.dp),
                colors        = nxFieldColors(),
                textStyle     = MaterialTheme.typography.bodyMedium,
                visualTransformation = if (showPw) VisualTransformation.None
                                       else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showPw = !showPw }) {
                        Icon(
                            if (showPw) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (showPw) "Hide" else "Show",
                            tint = NxFg2,
                        )
                    }
                },
            )

            if (uiState is LoginState.Error) {
                Spacer(Modifier.height(8.dp))
                Text(
                    (uiState as LoginState.Error).message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Spacer(Modifier.height(28.dp))

            Button(
                onClick  = { vm.login(url, password) },
                modifier = Modifier.fillMaxWidth().height(44.dp),
                enabled  = uiState !is LoginState.Loading,
                shape    = RoundedCornerShape(12.dp),
                colors   = ButtonDefaults.buttonColors(
                    containerColor = NxOrangeDim,
                    contentColor   = MaterialTheme.colorScheme.background,
                ),
            ) {
                if (uiState is LoginState.Loading) {
                    CircularProgressIndicator(
                        Modifier.size(18.dp),
                        color       = MaterialTheme.colorScheme.background,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text("connect", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
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
