package ch.toroag.nexis.worker.ui.login

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import ch.toroag.nexis.worker.ui.theme.NxBorder
import ch.toroag.nexis.worker.ui.theme.NxFg
import ch.toroag.nexis.worker.ui.theme.NxFg2
import ch.toroag.nexis.worker.ui.theme.NxOrange
import ch.toroag.nexis.worker.ui.theme.NxOrangeDim

@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    vm: LoginViewModel = viewModel(),
) {
    val uiState by vm.uiState.collectAsState()
    val focusMgr = LocalFocusManager.current

    var url      by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPw   by remember { mutableStateOf(false) }

    LaunchedEffect(uiState) {
        if (uiState is LoginState.Success) onLoginSuccess()
    }

    Column(
        Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(horizontal = 32.dp),
        verticalArrangement   = Arrangement.Center,
        horizontalAlignment   = Alignment.Start,
    ) {
        Text(
            "NeXiS",
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
            placeholder   = { Text("192.168.1.x:8443  or  nexis.example.com", color = NxFg2, style = MaterialTheme.typography.labelSmall) },
            modifier      = Modifier.fillMaxWidth(),
            singleLine    = true,
            shape         = androidx.compose.foundation.shape.RoundedCornerShape(4.dp),
            colors        = OutlinedTextFieldDefaults.colors(
                focusedBorderColor      = NxOrangeDim,
                unfocusedBorderColor    = NxBorder,
                focusedTextColor        = NxFg,
                unfocusedTextColor      = NxFg,
                cursorColor             = NxOrange,
                focusedContainerColor   = MaterialTheme.colorScheme.surfaceVariant,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
            textStyle = MaterialTheme.typography.bodyMedium,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Uri,
                imeAction    = ImeAction.Next,
            ),
            keyboardActions = KeyboardActions(onNext = { focusMgr.moveFocus(FocusDirection.Down) }),
        )

        Spacer(Modifier.height(16.dp))

        Text("password", style = MaterialTheme.typography.labelMedium, color = NxFg2)
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            value         = password,
            onValueChange = { password = it },
            modifier      = Modifier.fillMaxWidth(),
            singleLine    = true,
            shape         = androidx.compose.foundation.shape.RoundedCornerShape(4.dp),
            colors        = OutlinedTextFieldDefaults.colors(
                focusedBorderColor      = NxOrangeDim,
                unfocusedBorderColor    = NxBorder,
                focusedTextColor        = NxFg,
                unfocusedTextColor      = NxFg,
                cursorColor             = NxOrange,
                focusedContainerColor   = MaterialTheme.colorScheme.surfaceVariant,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
            textStyle = MaterialTheme.typography.bodyMedium,
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
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction    = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = {
                focusMgr.clearFocus()
                vm.login(url, password)
            }),
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
            shape    = androidx.compose.foundation.shape.RoundedCornerShape(4.dp),
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
