package ch.toroag.nexis.desktop.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun NexisEyeLogo(modifier: Modifier = Modifier, size: Dp = 28.dp) {
    Canvas(modifier = modifier.then(Modifier.also {})) {
        val w = this.size.width
        val h = this.size.height
        val strokeW = w * 0.06f
        val stroke = Stroke(width = strokeW, cap = StrokeCap.Round, join = StrokeJoin.Round)

        // Triangle outline
        val tri = Path().apply {
            moveTo(w * 0.5f, h * 0.04f)
            lineTo(w * 0.97f, h * 0.94f)
            lineTo(w * 0.03f, h * 0.94f)
            close()
        }
        drawPath(tri, NxOrangeDim, style = stroke)

        // Eye - ellipse (iris outline)
        drawOval(
            color = NxOrangeDim,
            topLeft = Offset(w * 0.26f, h * 0.56f),
            size = Size(w * 0.48f, h * 0.26f),
            style = Stroke(width = strokeW * 0.85f),
        )

        // Pupil
        drawCircle(color = NxOrange, radius = w * 0.09f, center = Offset(w * 0.5f, h * 0.69f))
        drawCircle(color = NxOrangeLit, radius = w * 0.04f, center = Offset(w * 0.5f, h * 0.69f))
    }
}
