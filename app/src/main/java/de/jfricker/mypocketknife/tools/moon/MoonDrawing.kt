package de.jfricker.mypocketknife.tools.moon

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Zeichnet den Mond mit beleuchtetem Anteil [fraction].
 * Nordhalbkugel: zunehmend ist rechts hell. Südhalbkugel: gespiegelt.
 */
@Composable
fun MoonDrawing(fraction: Double, waxing: Boolean, southern: Boolean, modifier: Modifier = Modifier) {
    val dark = Color(0xFF2E2E38)
    val lit = Color(0xFFF4EFD8)

    Canvas(modifier) {
        val r = min(size.width, size.height) / 2f * 0.95f
        val c = Offset(size.width / 2f, size.height / 2f)
        drawCircle(dark, r, c)

        // Seite, die beleuchtet ist: +1 = rechts, -1 = links
        val side = (if (waxing) 1f else -1f) * (if (southern) -1f else 1f)
        val k = fraction.coerceIn(0.0, 1.0).toFloat()
        val steps = 90

        val path = Path()
        // Außenrand (Halbkreis auf der hellen Seite), von oben nach unten
        for (i in 0..steps) {
            val t = (-PI / 2 + PI * i / steps)
            val x = c.x + side * r * cos(t).toFloat()
            val y = c.y + r * sin(t).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        // Schattengrenze (Halbellipse) zurück nach oben
        val term = 1f - 2f * k
        for (i in steps downTo 0) {
            val t = (-PI / 2 + PI * i / steps)
            val x = c.x + side * r * term * cos(t).toFloat()
            val y = c.y + r * sin(t).toFloat()
            path.lineTo(x, y)
        }
        path.close()
        drawPath(path, lit)
    }
}
