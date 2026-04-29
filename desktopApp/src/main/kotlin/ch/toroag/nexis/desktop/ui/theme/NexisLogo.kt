package ch.toroag.nexis.desktop.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun NexisEyeLogo(modifier: Modifier = Modifier, size: Dp = 28.dp) {
    Canvas(modifier = modifier.then(Modifier)) {
        val w = this.size.width
        val h = this.size.height

        // Triangle outline (stroke only)
        val path = Path().apply {
            moveTo(w * 0.5f,  h * 0.089f)   // top center
            lineTo(w * 0.946f, h * 0.875f)  // bottom right
            lineTo(w * 0.054f, h * 0.875f)  // bottom left
            close()
        }
        drawPath(path, color = NxOrange, style = Stroke(width = w * 0.036f, join = StrokeJoin.Round))

        // Iris ellipse
        drawOval(
            color   = NxOrange,
            topLeft = Offset(w * 0.34f, h * 0.57f),
            size    = Size(w * 0.32f, h * 0.20f),
            style   = Stroke(width = w * 0.027f),
        )

        // Pupil filled
        drawCircle(color = NxOrange, radius = w * 0.107f, center = Offset(w * 0.5f, h * 0.67f))

        // Pupil dark center
        drawCircle(color = NxBg, radius = w * 0.046f, center = Offset(w * 0.5f, h * 0.67f))
    }
}
