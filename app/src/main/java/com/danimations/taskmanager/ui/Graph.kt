package com.danimations.taskmanager.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.danimations.taskmanager.PerfHistory

data class GraphSeries(
    val values: List<Float>,
    val color: Color,
    val fill: Boolean = true,
    val dashed: Boolean = false,
)

/** Scrolling line graph: newest sample on the right edge, 60 samples wide. */
@Composable
fun PerfGraph(
    series: List<GraphSeries>,
    maxValue: Float,
    borderColor: Color,
    modifier: Modifier = Modifier,
    grid: Boolean = true,
    strokeWidth: Dp = 1.5.dp,
    capacity: Int = PerfHistory.CAPACITY,
) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        if (grid) {
            val gridColor = borderColor.copy(alpha = 0.22f)
            for (i in 1 until 10) {
                val y = h * i / 10f
                drawLine(gridColor, Offset(0f, y), Offset(w, y), 1f)
            }
            for (i in 1 until 12) {
                val x = w * i / 12f
                drawLine(gridColor, Offset(x, 0f), Offset(x, h), 1f)
            }
        }
        val max = if (maxValue > 0f) maxValue else 1f
        val step = w / (capacity - 1).coerceAtLeast(1)
        for (s in series) {
            val n = s.values.size
            if (n < 2) continue
            val startX = w - step * (n - 1)
            val line = Path()
            s.values.forEachIndexed { i, v ->
                val x = startX + step * i
                val y = h - (v / max).coerceIn(0f, 1f) * h
                if (i == 0) line.moveTo(x, y) else line.lineTo(x, y)
            }
            if (s.fill) {
                val area = Path().apply {
                    addPath(line)
                    lineTo(w, h)
                    lineTo(startX, h)
                    close()
                }
                drawPath(area, s.color.copy(alpha = 0.16f))
            }
            drawPath(
                line,
                s.color,
                style = Stroke(
                    width = strokeWidth.toPx(),
                    pathEffect = if (s.dashed) PathEffect.dashPathEffect(floatArrayOf(10f, 8f)) else null,
                ),
            )
        }
        drawRect(borderColor, style = Stroke(width = 1.dp.toPx()))
    }
}
