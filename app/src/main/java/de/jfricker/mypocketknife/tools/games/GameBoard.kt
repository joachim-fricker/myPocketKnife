package de.jfricker.mypocketknife.tools.games

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** Ein Zustand des Spielfelds, wie er angezeigt wird. Neues [token] = neue Animation. */
data class BoardView(
    val grid: Grid,
    /** Herkunft der Steine, die sich gerade bewegen (id → Position). */
    val moves: Map<Int, From> = emptyMap(),
    val token: Int = 0,
    val moveMillis: Int = 0,
)

/**
 * Gemeinsames Spielfeld für alle Gitter-Spiele: zeichnet, animiert Bewegungen und erkennt Tippen/Wischen.
 * [onSwipe] bekommt Start- und Nachbarfeld in Wischrichtung.
 */
@Composable
fun GameBoard(
    view: BoardView,
    onTap: (Int) -> Unit,
    modifier: Modifier = Modifier,
    onSwipe: ((from: Int, to: Int) -> Unit)? = null,
    background: DrawScope.(cell: Float) -> Unit = {},
    drawPiece: DrawScope.(piece: Piece, index: Int, center: Offset, cell: Float) -> Unit,
) {
    val grid = view.grid
    val progress = remember(view.token) { Animatable(if (view.moves.isEmpty()) 1f else 0f) }
    LaunchedEffect(view.token) {
        if (progress.value < 1f) progress.animateTo(1f, tween(view.moveMillis, easing = FastOutSlowInEasing))
    }
    val tap by rememberUpdatedState(onTap)
    val swipe by rememberUpdatedState(onSwipe)

    Canvas(
        modifier
            .fillMaxWidth()
            .aspectRatio(grid.cols.toFloat() / grid.rows)
            .clipToBounds()
            .pointerInput(grid.rows, grid.cols) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val cell = size.width.toFloat() / grid.cols
                    val col = (down.position.x / cell).toInt()
                    val row = (down.position.y / cell).toInt()
                    if (row !in 0 until grid.rows || col !in 0 until grid.cols) return@awaitEachGesture
                    val start = grid.index(row, col)
                    var swiped = false
                    while (true) {
                        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) {
                            if (!swiped) tap(start)
                            break
                        }
                        val onSwipeNow = swipe
                        if (!swiped && onSwipeNow != null) {
                            val d = change.position - down.position
                            if (maxOf(abs(d.x), abs(d.y)) > cell * 0.4f) {
                                swiped = true
                                val (dr, dc) = if (abs(d.x) > abs(d.y)) 0 to (if (d.x > 0) 1 else -1)
                                else (if (d.y > 0) 1 else -1) to 0
                                val r2 = row + dr
                                val c2 = col + dc
                                if (r2 in 0 until grid.rows && c2 in 0 until grid.cols) {
                                    onSwipeNow(start, grid.index(r2, c2))
                                }
                            }
                        }
                    }
                }
            }
    ) {
        val cell = size.width / grid.cols
        background(cell)
        val p = progress.value
        grid.cells.forEachIndexed { i, piece ->
            if (piece == null) return@forEachIndexed
            val tr = grid.rowOf(i).toFloat()
            val tc = grid.colOf(i).toFloat()
            val from = view.moves[piece.id]
            val r = if (from != null) from.row + (tr - from.row) * p else tr
            val c = if (from != null) from.col + (tc - from.col) * p else tc
            drawPiece(piece, i, Offset((c + 0.5f) * cell, (r + 0.5f) * cell), cell)
        }
    }
}

// ---------- Zeichen-Helfer ----------

fun Color.lighter(f: Float = 0.45f) = lerp(this, Color.White, f)
fun Color.darker(f: Float = 0.35f) = lerp(this, Color.Black, f)

/** Glänzende Kugel (SameGame). */
fun DrawScope.drawBall(color: Color, center: Offset, radius: Float) {
    drawCircle(color.darker(0.5f), radius, center + Offset(0f, radius * 0.06f))
    drawCircle(
        Brush.radialGradient(
            0f to color.lighter(0.55f),
            0.45f to color,
            1f to color.darker(0.3f),
            center = center - Offset(radius * 0.35f, radius * 0.35f),
            radius = radius * 1.5f,
        ),
        radius * 0.94f,
        center,
    )
    drawCircle(Color.White.copy(alpha = 0.55f), radius * 0.2f, center - Offset(radius * 0.38f, radius * 0.4f))
}

/** Regelmässiges Vieleck um [center]; [rotation] in Grad. */
fun polygon(center: Offset, radius: Float, corners: Int, rotation: Float = -90f): Path = Path().apply {
    for (k in 0 until corners) {
        val a = Math.toRadians((rotation + 360.0 * k / corners))
        val pt = Offset(center.x + radius * cos(a).toFloat(), center.y + radius * sin(a).toFloat())
        if (k == 0) moveTo(pt.x, pt.y) else lineTo(pt.x, pt.y)
    }
    close()
}
