package ch.toroag.nexis.desktop.ui.devices

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.rememberWindowState
import ch.toroag.nexis.desktop.ui.theme.*

data class VncTarget(
    val deviceId: String,
    val hostname: String,
    val viewUrl: String,
    val wsPort: Int,
    val baseUrl: String,
    val token: String,
)

@Composable
fun VncViewerWindow(target: VncTarget, onClose: () -> Unit) {
    val windowState = rememberWindowState(width = 1280.dp, height = 800.dp)

    Window(
        onCloseRequest = onClose,
        title = "Screen — ${target.hostname}",
        state = windowState,
    ) {
        NexisTheme {
            VncViewerContent(target = target, onOpenBrowser = {
                try {
                    val url = target.baseUrl + target.viewUrl
                    java.awt.Desktop.getDesktop().browse(java.net.URI(url))
                } catch (_: Exception) {
                    try { Runtime.getRuntime().exec(arrayOf("xdg-open", target.baseUrl + target.viewUrl)) }
                    catch (_: Exception) {}
                }
            })
        }
    }
}

@Composable
private fun VncViewerContent(target: VncTarget, onOpenBrowser: () -> Unit) {
    Box(Modifier.fillMaxSize().background(NxBg)) {
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "VNC VIEWER",
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                color = NxOrange,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                target.hostname,
                fontFamily = FontFamily.Monospace,
                fontSize = 18.sp,
                color = NxFg,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "port ${target.wsPort}",
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = NxFg2,
            )
            Spacer(Modifier.height(24.dp))
            Text(
                "VNC stream is proxied through your Nexis controller.",
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = NxFg2,
            )
            Text(
                "Open in browser for the full interactive noVNC experience.",
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = NxFg2,
            )
            Spacer(Modifier.height(20.dp))
            OutlinedButton(
                onClick = onOpenBrowser,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = NxOrange),
                border = androidx.compose.foundation.BorderStroke(1.dp, NxOrangeDim),
            ) {
                Icon(Icons.Default.OpenInBrowser, null, Modifier.size(16.dp), tint = NxOrange)
                Spacer(Modifier.width(8.dp))
                Text("Open VNC in Browser", fontFamily = FontFamily.Monospace, fontSize = 11.sp)
            }
        }
    }
}
