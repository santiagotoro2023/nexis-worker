package ch.toroag.nexis.worker.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// ── Color palette — mirrors the web UI CSS variables ─────────────────────────
val NxOrange    = Color(0xFFE8720C)  // --or
val NxOrangeDim = Color(0xFFC45C00)  // --or2
val NxOrangeLit = Color(0xFFFF9533)  // --or3
val NxBg        = Color(0xFF080807)  // --bg
val NxBg2       = Color(0xFF0D0D0A)  // --bg2  (surface)
val NxBg3       = Color(0xFF131310)  // --bg3  (surface variant)
val NxDim       = Color(0xFF2A2A1A)  // --dim  (user bubble bg)
val NxFg        = Color(0xFFC4B898)  // --fg
val NxFg2       = Color(0xFF887766)  // --fg2
val NxBorder    = Color(0xFF1A1A12)  // --border
val NxOutline   = Color(0xFF3A3A28)  // slightly brighter for interactive outlines
val NxGreen     = Color(0xFF4CAF50)  // online status indicator

private val NexisColorScheme = darkColorScheme(
    primary              = NxOrange,
    onPrimary            = NxBg,
    primaryContainer     = NxDim,
    onPrimaryContainer   = NxFg,
    secondary            = NxOrangeDim,
    onSecondary          = NxBg,
    secondaryContainer   = NxDim,
    onSecondaryContainer = NxFg,
    tertiary             = NxOrangeLit,
    onTertiary           = NxBg,
    background           = NxBg,
    onBackground         = NxFg,
    surface              = NxBg2,
    onSurface            = NxFg,
    surfaceVariant       = NxBg3,
    onSurfaceVariant     = NxFg2,
    outline              = NxOutline,
    outlineVariant       = NxBorder,
    error                = Color(0xFFCF6679),
    onError              = NxBg,
    errorContainer       = Color(0xFF4A1520),
    onErrorContainer     = Color(0xFFFFB3BC),
    scrim                = Color(0xCC000000),
)

// Monospace throughout — matches JetBrains Mono style of the web UI
private val NexisTypography = Typography(
    displayLarge   = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,   fontSize = 57.sp),
    displayMedium  = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,   fontSize = 45.sp),
    displaySmall   = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,   fontSize = 36.sp),
    headlineLarge  = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,   fontSize = 32.sp, letterSpacing = 0.1.sp),
    headlineMedium = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,   fontSize = 28.sp),
    headlineSmall  = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,   fontSize = 24.sp),
    titleLarge     = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,   fontSize = 18.sp, letterSpacing = 0.15.sp),
    titleMedium    = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,   fontSize = 15.sp, letterSpacing = 0.1.sp),
    titleSmall     = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium, fontSize = 13.sp),
    bodyLarge      = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Normal, fontSize = 14.sp),
    bodyMedium     = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Normal, fontSize = 13.sp),
    bodySmall      = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Normal, fontSize = 11.sp),
    labelLarge     = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium, fontSize = 13.sp),
    labelMedium    = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium, fontSize = 11.sp),
    labelSmall     = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Normal, fontSize = 10.sp, letterSpacing = 0.05.sp),
)

@Composable
fun NexisTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = NexisColorScheme,
        typography  = NexisTypography,
        content     = content,
    )
}
