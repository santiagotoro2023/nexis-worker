package ch.toroag.nexis.worker.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Subtle grid overlay used on login and setup screens. */
@Composable
fun GridBackground() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val gridSize = 40.dp.toPx()
        val color    = Color(0xFFC4B898).copy(alpha = 0.03f)
        var x = 0f
        while (x <= size.width) {
            drawLine(color, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
            x += gridSize
        }
        var y = 0f
        while (y <= size.height) {
            drawLine(color, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
            y += gridSize
        }
    }
}

/** Pill badge showing online/offline/other status. */
@Composable
fun StatusBadge(status: String, modifier: Modifier = Modifier) {
    val (bg, fg, border) = when (status.lowercase()) {
        "running", "online"   -> Triple(NxGreen.copy(alpha = .10f), NxGreen,  NxGreen.copy(alpha = .20f))
        "stopped", "offline"  -> Triple(NxRed.copy(alpha = .10f),   NxRed,    NxRed.copy(alpha = .20f))
        else                  -> Triple(NxFg2.copy(alpha = .10f),   NxFg2,    NxFg2.copy(alpha = .20f))
    }
    Box(
        modifier
            .background(bg, RoundedCornerShape(999.dp))
            .border(1.dp, border, RoundedCornerShape(999.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(
            status.uppercase(),
            color         = fg,
            fontSize      = 9.sp,
            letterSpacing = 0.1.sp,
            fontFamily    = FontFamily.Monospace,
        )
    }
}
