package ch.toroag.nexis.desktop.ui.login

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
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
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPw   by remember { mutableStateOf(false) }

    LaunchedEffect(uiState) {
        if (uiState is LoginState.Success) onLoginSuccess()
    }

    Box(
        modifier         = Modifier
            .fillMaxSize()
            .background(NxBg),
        contentAlignment = Alignment.Center,
    ) {
        // Grid background overlay
        GridBackground()

        Column(
            modifier            = Modifier.width(420.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Eye logo
            NexisEyeLogo(size = 56.dp)
            Spacer(Modifier.height(16.dp))

            // Title
            Text(
                "NEXIS",
                fontFamily    = FontFamily.Monospace,
                fontSize      = 26.sp,
                fontWeight    = FontWeight.Bold,
                letterSpacing = 0.4.sp,
                color         = NxFg,
            )
            Text(
                "WORKER NODE",
                fontFamily    = FontFamily.Monospace,
                fontSize      = 10.sp,
                letterSpacing = 0.2.sp,
                color         = NxFg2,
            )

            Spacer(Modifier.height(32.dp))

            // Auth card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(NxBg3, RoundedCornerShape(16.dp))
                    .border(1.dp, NxBorder, RoundedCornerShape(16.dp))
                    .padding(24.dp),
            ) {
                Text(
                    "IDENTITY VERIFICATION REQUIRED",
                    fontFamily    = FontFamily.Monospace,
                    fontSize      = 9.sp,
                    fontWeight    = FontWeight.Bold,
                    letterSpacing = 0.18.sp,
                    color         = NxOrange,
                    modifier      = Modifier.padding(bottom = 16.dp),
                )

                // Server URL
                Text(
                    "CONTROLLER URL",
                    fontFamily    = FontFamily.Monospace,
                    fontSize      = 10.sp,
                    letterSpacing = 0.15.sp,
                    color         = NxFg2,
                    modifier      = Modifier.padding(bottom = 4.dp),
                )
                OutlinedTextField(
                    value         = url,
                    onValueChange = { url = it },
                    placeholder   = {
                        Text(
                            "https://192.168.1.x:8443",
                            color = NxFg2.copy(alpha = 0.5f),
                            style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp),
                        )
                    },
                    modifier   = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape      = RoundedCornerShape(12.dp),
                    colors     = nxFieldColors(),
                    textStyle  = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = NxFg),
                )

                Spacer(Modifier.height(12.dp))

                // Username
                Text(
                    "USERNAME",
                    fontFamily    = FontFamily.Monospace,
                    fontSize      = 10.sp,
                    letterSpacing = 0.15.sp,
                    color         = NxFg2,
                    modifier      = Modifier.padding(bottom = 4.dp),
                )
                OutlinedTextField(
                    value         = username,
                    onValueChange = { username = it },
                    placeholder   = {
                        Text(
                            "creator",
                            color = NxFg2.copy(alpha = 0.5f),
                            style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp),
                        )
                    },
                    modifier   = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape      = RoundedCornerShape(12.dp),
                    colors     = nxFieldColors(),
                    textStyle  = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = NxFg),
                )

                Spacer(Modifier.height(12.dp))

                // Password
                Text(
                    "PASSWORD",
                    fontFamily    = FontFamily.Monospace,
                    fontSize      = 10.sp,
                    letterSpacing = 0.15.sp,
                    color         = NxFg2,
                    modifier      = Modifier.padding(bottom = 4.dp),
                )
                OutlinedTextField(
                    value         = password,
                    onValueChange = { password = it },
                    modifier      = Modifier.fillMaxWidth(),
                    singleLine    = true,
                    shape         = RoundedCornerShape(12.dp),
                    colors        = nxFieldColors(),
                    textStyle     = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = NxFg),
                    visualTransformation = if (showPw) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showPw = !showPw }) {
                            Icon(
                                if (showPw) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = null,
                                tint = NxFg2,
                            )
                        }
                    },
                )

                if (uiState is LoginState.Error) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        (uiState as LoginState.Error).message,
                        color = NxRed,
                        style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                    )
                }

                Spacer(Modifier.height(20.dp))

                Button(
                    onClick  = { vm.login(url, username, password) },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    enabled  = uiState !is LoginState.Loading,
                    shape    = RoundedCornerShape(12.dp),
                    colors   = ButtonDefaults.buttonColors(
                        containerColor = NxOrange,
                        contentColor   = NxBg,
                    ),
                ) {
                    if (uiState is LoginState.Loading) {
                        CircularProgressIndicator(Modifier.size(18.dp), color = NxBg, strokeWidth = 2.dp)
                    } else {
                        Text(
                            "AUTHENTICATE",
                            fontFamily    = FontFamily.Monospace,
                            fontWeight    = FontWeight.Bold,
                            letterSpacing = 0.15.sp,
                            fontSize      = 11.sp,
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            Text(
                "AUTHORISED PERSONNEL ONLY · LOCAL ACCESS",
                fontFamily    = FontFamily.Monospace,
                fontSize      = 9.sp,
                letterSpacing = 0.1.sp,
                color         = NxFg2,
                textAlign     = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun nxFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor   = NxBg2,
    unfocusedContainerColor = NxBg2,
    focusedBorderColor      = NxOrangeDim,
    unfocusedBorderColor    = NxBorder,
    focusedTextColor        = NxFg,
    unfocusedTextColor      = NxFg,
    cursorColor             = NxOrange,
)
