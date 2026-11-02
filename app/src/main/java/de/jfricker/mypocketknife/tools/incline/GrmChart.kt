package de.jfricker.mypocketknife.tools.incline

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp

private val G_GREEN = Color(0xFF4E9A3A)
private val G_ORANGE = Color(0xFFE59A35)
private val G_RED = Color(0xFFC8302A)

/**
 * Nachbau der GRM-Grafik mit Messpunkt.
 * [x] = Lage der gewählten Gefahrenstufe (0..4), [angle] = gemessene Neigung.
 */
@Composable
fun GrmChart(x: Double, angle: Double, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurface)

    Canvas(modifier.aspectRatio(1.7f)) {
        val left = 34.dp.toPx()
        val bottom = 18.dp.toPx()
        val w = size.width - left
        val h = size.height - bottom
        fun px(xv: Double) = left + (xv / 4.0 * w).toFloat()
        fun py(deg: Double) = ((Grm.MAX_DEG - deg) / (Grm.MAX_DEG - Grm.MIN_DEG) * h).toFloat()
        fun frac(deg: Double) = ((Grm.MAX_DEG - deg) / (Grm.MAX_DEG - Grm.MIN_DEG)).toFloat().coerceIn(0f, 1f)

        // Farbverlauf: schmale senkrechte Streifen mit weichem Übergang (±1,5°) an den Grenzen
        val strips = 120
        val sw = w / strips
        for (i in 0 until strips) {
            val xv = (i + 0.5) / strips * 4.0
            val red = Grm.redLimit(xv)
            val green = Grm.greenLimit(xv)
            val soft = 1.5
            val stops = listOf(
                0f to G_RED,
                frac(red + soft) to G_RED,
                frac(red - soft) to G_ORANGE,
                frac(green + soft) to G_ORANGE,
                frac(green - soft) to G_GREEN,
                1f to G_GREEN,
            ).sortedBy { it.first }.toTypedArray()
            drawRect(
                brush = Brush.verticalGradient(*stops, startY = 0f, endY = h),
                topLeft = Offset(left + i * sw, 0f),
                size = Size(sw + 1f, h),
            )
        }

        // Gitter: Stufen und 30/35/40°
        val grid = Color.White.copy(alpha = 0.85f)
        for (k in 1..3) drawLine(grid, Offset(px(k.toDouble()), 0f), Offset(px(k.toDouble()), h), 2f)
        for (d in listOf(30.0, 35.0, 40.0)) drawLine(grid, Offset(left, py(d)), Offset(left + w, py(d)), 2f)

        // Schwarze Einsteigerlinie (Treppe)
        val dash = PathEffect.dashPathEffect(floatArrayOf(14f, 8f))
        val steps = listOf(40.0, 35.0, 30.0, 25.0)
        for (k in 0..3) {
            drawLine(Color.Black, Offset(px(k.toDouble()), py(steps[k])), Offset(px(k + 1.0), py(steps[k])), 6f, pathEffect = dash)
            if (k < 3) drawLine(Color.Black, Offset(px(k + 1.0), py(steps[k])), Offset(px(k + 1.0), py(steps[k + 1])), 6f, pathEffect = dash)
        }

        // Beschriftung
        for (d in listOf(25, 30, 35, 40, 45)) {
            val t = measurer.measure("$d°", labelStyle)
            drawText(t, topLeft = Offset(left - t.size.width - 4.dp.toPx(),
                (py(d.toDouble()) - t.size.height / 2f).coerceIn(0f, h - t.size.height)))
        }
        listOf("gering", "mässig", "erheblich", "gross").forEachIndexed { k, s ->
            val t = measurer.measure(s, labelStyle)
            drawText(t, topLeft = Offset(px(k + 0.5) - t.size.width / 2f, h + 2.dp.toPx()))
        }

        // Gewählte Stufe und Messpunkt
        val mx = px(x)
        drawLine(Color.White, Offset(mx, 0f), Offset(mx, h), 3f)
        val my = py(angle.coerceIn(Grm.MIN_DEG, Grm.MAX_DEG))
        drawCircle(Color.White, 11.dp.toPx(), Offset(mx, my))
        drawCircle(Color.Black, 7.dp.toPx(), Offset(mx, my))
    }
}
