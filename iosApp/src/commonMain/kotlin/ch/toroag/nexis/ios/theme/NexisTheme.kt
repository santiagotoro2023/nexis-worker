package ch.toroag.nexis.ios.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary          = Color(0xFF7B61FF),
    onPrimary        = Color.White,
    primaryContainer = Color(0xFF3D2E99),
    secondary        = Color(0xFF9ECAFF),
    background       = Color(0xFF0F0F1A),
    surface          = Color(0xFF1A1A2E),
    surfaceVariant   = Color(0xFF252540),
    onBackground     = Color(0xFFE0E0FF),
    onSurface        = Color(0xFFE0E0FF),
    error            = Color(0xFFFF6B6B),
)

@Composable
fun NexisTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkColors, content = content)
}
