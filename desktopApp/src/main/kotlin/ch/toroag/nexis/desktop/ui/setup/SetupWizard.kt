package ch.toroag.nexis.desktop.ui.setup

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
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

/**
 * Four-step setup wizard for the desktop app, shown when no server URL is configured.
 */
@Composable
fun SetupWizard(onComplete: (serverUrl: String, username: String, password: String) -> Unit) {
    var step     by remember { mutableStateOf(1) }
    var url      by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPw   by remember { mutableStateOf(false) }

    Box(
        modifier         = Modifier
            .fillMaxSize()
            .background(NxBg),
        contentAlignment = Alignment.Center,
    ) {
        // Grid background overlay
        GridBackground()

        Column(
            modifier            = Modifier.width(480.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Header: logo + title
            NexisEyeLogo(size = 48.dp)
            Spacer(Modifier.height(12.dp))
            Text(
                "NEXIS",
                fontFamily    = FontFamily.Monospace,
                fontSize      = 22.sp,
                fontWeight    = FontWeight.Bold,
                letterSpacing = 0.4.sp,
                color         = NxFg,
            )
            Text(
                "WORKER SETUP",
                fontFamily    = FontFamily.Monospace,
                fontSize      = 10.sp,
                letterSpacing = 0.2.sp,
                color         = NxFg2,
            )
            Spacer(Modifier.height(20.dp))

            // Progress bar: 4 segments
            StepProgressBar(currentStep = step, totalSteps = 4)
            Spacer(Modifier.height(20.dp))

            // Step card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(NxBg3, RoundedCornerShape(16.dp))
                    .border(1.dp, NxBorder, RoundedCornerShape(16.dp))
                    .padding(24.dp),
            ) {
                when (step) {

                    // ── Step 1: Welcome ────────────────────────────────────────
                    1 -> {
                        Text(
                            "WELCOME",
                            fontFamily    = FontFamily.Monospace,
                            fontSize      = 9.sp,
                            fontWeight    = FontWeight.Bold,
                            letterSpacing = 0.18.sp,
                            color         = NxOrange,
                            modifier      = Modifier.padding(bottom = 12.dp),
                        )
                        Text(
                            "Connect your Worker node to a NeXiS Controller.",
                            fontFamily = FontFamily.Monospace,
                            fontSize   = 13.sp,
                            color      = NxFg,
                        )
                        Spacer(Modifier.height(12.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            FeatureRow("Secure HTTPS connection to Controller")
                            FeatureRow("Self-signed certificates pinned automatically")
                            FeatureRow("Remote control, chat, schedules & hypervisor")
                            FeatureRow("Persistent sessions and memory context")
                        }
                        Spacer(Modifier.height(20.dp))
                        WizardPrimaryButton("BEGIN", Modifier.fillMaxWidth()) { step = 2 }
                    }

                    // ── Step 2: Controller URL ─────────────────────────────────
                    2 -> {
                        Text(
                            "CONTROLLER URL",
                            fontFamily    = FontFamily.Monospace,
                            fontSize      = 9.sp,
                            fontWeight    = FontWeight.Bold,
                            letterSpacing = 0.18.sp,
                            color         = NxOrange,
                            modifier      = Modifier.padding(bottom = 12.dp),
                        )
                        Text(
                            "URL",
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
                                Text("https://192.168.1.x:8443",
                                     color = NxFg2.copy(alpha = 0.5f),
                                     style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp))
                            },
                            modifier   = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape      = RoundedCornerShape(12.dp),
                            colors     = nxFieldColors(),
                            textStyle  = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = NxFg),
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Self-signed certificates are accepted and pinned automatically on first connection.",
                            fontFamily = FontFamily.Monospace,
                            fontSize   = 10.sp,
                            color      = NxFg2,
                        )
                        Spacer(Modifier.height(20.dp))
                        Row(
                            modifier              = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            WizardGhostButton(Modifier.weight(1f)) { step = 1 }
                            WizardPrimaryButton(
                                label    = "NEXT",
                                modifier = Modifier.weight(1f),
                                enabled  = url.isNotBlank(),
                            ) { step = 3 }
                        }
                    }

                    // ── Step 3: Credentials ────────────────────────────────────
                    3 -> {
                        Text(
                            "CREDENTIALS",
                            fontFamily    = FontFamily.Monospace,
                            fontSize      = 9.sp,
                            fontWeight    = FontWeight.Bold,
                            letterSpacing = 0.18.sp,
                            color         = NxOrange,
                            modifier      = Modifier.padding(bottom = 12.dp),
                        )
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
                                Text("creator",
                                     color = NxFg2.copy(alpha = 0.5f),
                                     style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp))
                            },
                            modifier   = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape      = RoundedCornerShape(12.dp),
                            colors     = nxFieldColors(),
                            textStyle  = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = NxFg),
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "PASSWORD",
                            fontFamily    = FontFamily.Monospace,
                            fontSize      = 10.sp,
                            letterSpacing = 0.15.sp,
                            color         = NxFg2,
                            modifier      = Modifier.padding(bottom = 4.dp),
                        )
                        OutlinedTextField(
                            value                = password,
                            onValueChange        = { password = it },
                            modifier             = Modifier.fillMaxWidth(),
                            singleLine           = true,
                            shape                = RoundedCornerShape(12.dp),
                            colors               = nxFieldColors(),
                            textStyle            = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = NxFg),
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
                        Spacer(Modifier.height(20.dp))
                        Row(
                            modifier              = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            WizardGhostButton(Modifier.weight(1f)) { step = 2 }
                            WizardPrimaryButton(
                                label    = "NEXT",
                                modifier = Modifier.weight(1f),
                                enabled  = username.isNotBlank() && password.isNotBlank(),
                            ) { step = 4 }
                        }
                    }

                    // ── Step 4: Done ───────────────────────────────────────────
                    4 -> {
                        Text(
                            "SYSTEM READY",
                            fontFamily    = FontFamily.Monospace,
                            fontSize      = 9.sp,
                            fontWeight    = FontWeight.Bold,
                            letterSpacing = 0.18.sp,
                            color         = NxOrange,
                            modifier      = Modifier.padding(bottom = 12.dp),
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                tint     = NxGreen,
                                modifier = Modifier.size(48.dp),
                            )
                        }
                        Text(
                            "Configuration complete.",
                            fontFamily = FontFamily.Monospace,
                            fontSize   = 13.sp,
                            color      = NxFg,
                            textAlign  = TextAlign.Center,
                            modifier   = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Connecting to $url as $username.",
                            fontFamily = FontFamily.Monospace,
                            fontSize   = 11.sp,
                            color      = NxFg2,
                            textAlign  = TextAlign.Center,
                            modifier   = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(20.dp))
                        Row(
                            modifier              = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            WizardGhostButton(Modifier.weight(1f)) { step = 3 }
                            Button(
                                onClick  = { onComplete(url, username, password) },
                                modifier = Modifier.weight(1f).height(48.dp),
                                shape    = RoundedCornerShape(12.dp),
                                colors   = ButtonDefaults.buttonColors(
                                    containerColor = NxOrange,
                                    contentColor   = NxBg,
                                ),
                            ) {
                                Text(
                                    "ENTER SYSTEM",
                                    fontFamily    = FontFamily.Monospace,
                                    fontWeight    = FontWeight.Bold,
                                    letterSpacing = 0.15.sp,
                                    fontSize      = 11.sp,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Step progress bar ─────────────────────────────────────────────────────────

@Composable
private fun StepProgressBar(currentStep: Int, totalSteps: Int) {
    Row(
        modifier              = Modifier.width(480.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        (1..totalSteps).forEach { s ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(3.dp)
                    .background(
                        if (s <= currentStep) NxOrange else NxBorder,
                        RoundedCornerShape(999.dp),
                    ),
            )
        }
    }
}

// ── Feature bullet ────────────────────────────────────────────────────────────

@Composable
private fun FeatureRow(text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Text("·  ", color = NxOrange, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
        Text(text, color = NxFg2, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
    }
}

// ── Shared wizard buttons ─────────────────────────────────────────────────────

@Composable
private fun WizardPrimaryButton(
    label:    String,
    modifier: Modifier = Modifier,
    enabled:  Boolean  = true,
    onClick:  () -> Unit,
) {
    Button(
        onClick  = onClick,
        modifier = modifier.height(48.dp),
        enabled  = enabled,
        shape    = RoundedCornerShape(12.dp),
        colors   = ButtonDefaults.buttonColors(
            containerColor         = NxOrange,
            contentColor           = NxBg,
            disabledContainerColor = NxOrange.copy(alpha = 0.3f),
            disabledContentColor   = NxBg.copy(alpha = 0.4f),
        ),
    ) {
        Text(
            label,
            fontFamily    = FontFamily.Monospace,
            fontWeight    = FontWeight.Bold,
            letterSpacing = 0.15.sp,
            fontSize      = 11.sp,
        )
    }
}

@Composable
private fun WizardGhostButton(
    modifier: Modifier = Modifier,
    onClick:  () -> Unit,
) {
    OutlinedButton(
        onClick  = onClick,
        modifier = modifier.height(48.dp),
        shape    = RoundedCornerShape(12.dp),
        colors   = ButtonDefaults.outlinedButtonColors(contentColor = NxFg2),
        border   = androidx.compose.foundation.BorderStroke(1.dp, NxBorder),
    ) {
        Text(
            "BACK",
            fontFamily    = FontFamily.Monospace,
            fontWeight    = FontWeight.Bold,
            letterSpacing = 0.15.sp,
            fontSize      = 11.sp,
        )
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
