package de.jfricker.mypocketknife.tools.dice

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.jfricker.mypocketknife.tools.games.GOLD

/**
 * Ein Würfel (Augen 1–6); held = gold umrandet (ausgewählt), faded = blass (noch nicht gewürfelt),
 * kept = grün (zählt schon, z. B. bei 10000 zur Seite gelegt).
 */
@Composable
fun DieFace(value: Int, held: Boolean, faded: Boolean, size: Dp, onClick: () -> Unit, kept: Boolean = false) {
    val face = when {
        kept -> KEPT_FACE
        held -> Color(0xFFFFF3C4)
        else -> Color.White
    }
    val edge = when {
        held -> GOLD
        kept -> KEPT_EDGE
        else -> Color(0xFFB0B0B0)
    }
    Canvas(
        Modifier
            .size(size)
            .alpha(if (faded) 0.35f else 1f)
            .border(if (held || kept) 3.dp else 1.dp, edge, RoundedCornerShape(size * 0.2f))
            .clickable(onClick = onClick)
    ) {
        val s = this.size.width
        drawRoundRect(face, cornerRadius = CornerRadius(s * 0.2f))
        val r = s * 0.09f
        val a = s * 0.27f
        val m = s * 0.5f
        val b = s * 0.73f
        val pips: List<Pair<Float, Float>> = when (value) {
            1 -> listOf(m to m)
            2 -> listOf(a to a, b to b)
            3 -> listOf(a to a, m to m, b to b)
            4 -> listOf(a to a, b to a, a to b, b to b)
            5 -> listOf(a to a, b to a, m to m, a to b, b to b)
            else -> listOf(a to a, b to a, a to m, b to m, a to b, b to b)
        }
        pips.forEach { (x, y) -> drawCircle(Color(0xFF222222), r, Offset(x, y)) }
    }
}

private val KEPT_FACE = Color(0xFFB9F0B9)
private val KEPT_EDGE = Color(0xFF2E9D3A)
