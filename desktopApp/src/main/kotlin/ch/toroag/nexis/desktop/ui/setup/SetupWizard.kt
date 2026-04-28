package ch.toroag.nexis.desktop.ui.setup

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ch.toroag.nexis.desktop.ui.theme.NexisEyeLogo
import ch.toroag.nexis.desktop.ui.theme.NxBorder
import ch.toroag.nexis.desktop.ui.theme.NxFg
import ch.toroag.nexis.desktop.ui.theme.NxFg2
import ch.toroag.nexis.desktop.ui.theme.NxOrange
import ch.toroag.nexis.desktop.ui.theme.NxOrangeDim

/**
 * Four-step setup wizard for the desktop app, shown when no server URL is configured.
 *
 * @param onComplete Called with (serverUrl, username, password) when the user taps ENTER on step 4.
 */
@Composable
fun SetupWizard(onComplete: (serverUrl: String, username: String, password: String) -> Unit) {
    var step     by remember { mutableStateOf(1) }
    var url      by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPw   by remember { mutableStateOf(false) }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor      = NxOrangeDim,
        unfocusedBorderColor    = NxBorder,
        focusedTextColor        = NxFg,
        unfocusedTextColor      = NxFg,
        cursorColor             = NxOrange,
        focusedContainerColor   = MaterialTheme.colorScheme.surfaceVariant,
        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
    )

    Box(
        modifier         = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier            = Modifier.width(400.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            when (step) {

                // ── Step 1: Welcome ───────────────────────────────────────────
                1 -> {
                    NexisEyeLogo(modifier = Modifier.size(80.dp))
                    Spacer(Modifier.height(24.dp))
                    Text(
                        "NEXIS WORKER",
                        style     = MaterialTheme.typography.headlineMedium,
                        color     = NxOrange,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "NX-WRK",
                        style     = MaterialTheme.typography.labelSmall,
                        color     = NxFg2,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Connect to your NeXiS Controller to begin.",
                        style     = MaterialTheme.typography.bodyMedium,
                        color     = NxFg2,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(48.dp))
                    WizardNextButton(label = "BEGIN", modifier = Modifier.fillMaxWidth()) { step = 2 }
                }

                // ── Step 2: Server Config ─────────────────────────────────────
                2 -> {
                    Text(
                        "CONTROLLER URL",
                        style = MaterialTheme.typography.labelLarge,
                        color = NxOrange,
                    )
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(
                        value         = url,
                        onValueChange = { url = it },
                        label         = { Text("https://ip:8443", color = NxFg2) },
                        modifier      = Modifier.fillMaxWidth(),
                        singleLine    = true,
                        shape         = RoundedCornerShape(12.dp),
                        colors        = fieldColors,
                        textStyle     = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Self-signed certificates are accepted and pinned automatically on first connection.",
                        style     = MaterialTheme.typography.labelSmall,
                        color     = NxFg2,
                        textAlign = TextAlign.Start,
                        modifier  = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(32.dp))
                    Row(
                        modifier              = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        WizardBackButton(Modifier.weight(1f)) { step = 1 }
                        WizardNextButton(
                            label    = "NEXT",
                            modifier = Modifier.weight(1f),
                            enabled  = url.isNotBlank(),
                        ) { step = 3 }
                    }
                }

                // ── Step 3: Credentials ───────────────────────────────────────
                3 -> {
                    Text(
                        "CREDENTIALS",
                        style = MaterialTheme.typography.labelLarge,
                        color = NxOrange,
                    )
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(
                        value         = username,
                        onValueChange = { username = it },
                        label         = { Text("username", color = NxFg2) },
                        modifier      = Modifier.fillMaxWidth(),
                        singleLine    = true,
                        shape         = RoundedCornerShape(12.dp),
                        colors        = fieldColors,
                        textStyle     = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value                = password,
                        onValueChange        = { password = it },
                        label                = { Text("password", color = NxFg2) },
                        modifier             = Modifier.fillMaxWidth(),
                        singleLine           = true,
                        shape                = RoundedCornerShape(12.dp),
                        colors               = fieldColors,
                        textStyle            = MaterialTheme.typography.bodyMedium,
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
                    Spacer(Modifier.height(32.dp))
                    Row(
                        modifier              = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        WizardBackButton(Modifier.weight(1f)) { step = 2 }
                        WizardNextButton(
                            label    = "NEXT",
                            modifier = Modifier.weight(1f),
                            enabled  = username.isNotBlank() && password.isNotBlank(),
                        ) { step = 4 }
                    }
                }

                // ── Step 4: Done ──────────────────────────────────────────────
                4 -> {
                    NexisEyeLogo(modifier = Modifier.size(56.dp))
                    Spacer(Modifier.height(24.dp))
                    Text(
                        "System online.",
                        style     = MaterialTheme.typography.headlineSmall,
                        color     = NxOrange,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Configuration saved. Connecting to $url as $username.",
                        style     = MaterialTheme.typography.bodySmall,
                        color     = NxFg2,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(40.dp))
                    Row(
                        modifier              = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        WizardBackButton(Modifier.weight(1f)) { step = 3 }
                        Button(
                            onClick  = { onComplete(url, username, password) },
                            modifier = Modifier.weight(1f).height(44.dp),
                            shape    = RoundedCornerShape(12.dp),
                            colors   = ButtonDefaults.buttonColors(
                                containerColor = NxOrange,
                                contentColor   = MaterialTheme.colorScheme.background,
                            ),
                        ) {
                            Text("ENTER", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }

            // Step indicator dots
            Spacer(Modifier.height(32.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (1..4).forEach { s ->
                    val active = s == step
                    Surface(
                        modifier = Modifier.size(if (active) 8.dp else 6.dp),
                        shape    = RoundedCornerShape(50),
                        color    = if (active) NxOrange else NxFg2.copy(alpha = 0.4f),
                    ) {}
                }
            }
        }
    }
}

// ── Reusable wizard buttons ───────────────────────────────────────────────────

@Composable
private fun WizardNextButton(
    label:    String,
    modifier: Modifier = Modifier,
    enabled:  Boolean  = true,
    onClick:  () -> Unit,
) {
    Button(
        onClick  = onClick,
        modifier = modifier.height(44.dp),
        enabled  = enabled,
        shape    = RoundedCornerShape(12.dp),
        colors   = ButtonDefaults.buttonColors(
            containerColor         = NxOrangeDim,
            contentColor           = MaterialTheme.colorScheme.background,
            disabledContainerColor = NxOrangeDim.copy(alpha = 0.3f),
            disabledContentColor   = MaterialTheme.colorScheme.background.copy(alpha = 0.4f),
        ),
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun WizardBackButton(
    modifier: Modifier = Modifier,
    onClick:  () -> Unit,
) {
    OutlinedButton(
        onClick  = onClick,
        modifier = modifier.height(44.dp),
        shape    = RoundedCornerShape(12.dp),
        colors   = ButtonDefaults.outlinedButtonColors(contentColor = NxFg2),
        border   = BorderStroke(1.dp, NxBorder),
    ) {
        Text("BACK", style = MaterialTheme.typography.labelLarge)
    }
}
