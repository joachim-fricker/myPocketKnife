package de.jfricker.mypocketknife.tools.games.bejeweled

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.jfricker.mypocketknife.tools.games.GOLD
import de.jfricker.mypocketknife.tools.games.Game
import de.jfricker.mypocketknife.tools.games.GameBoard
import de.jfricker.mypocketknife.tools.games.GameOverDialog
import de.jfricker.mypocketknife.tools.games.GameScaffold
import de.jfricker.mypocketknife.tools.games.darker
import de.jfricker.mypocketknife.tools.games.formatScore
import de.jfricker.mypocketknife.tools.games.lighter
import de.jfricker.mypocketknife.tools.games.polygon

/** Jede Farbe hat zusätzlich eine eigene Form, damit man sie gut unterscheidet. */
private val GEM_COLORS = listOf(
    Color(0xFFE53935), // rot – Quadrat
    Color(0xFFFB8C00), // orange – Sechseck
    Color(0xFFFDD835), // gelb – Raute
    Color(0xFF43A047), // grün – Achteck
    Color(0xFF1E88E5), // blau – Dreieck nach unten
    Color(0xFF8E24AA), // violett – Dreieck nach oben
    Color(0xFFECEFF1), // weiss – Kreis
)
private val BG_A = Color(0xFF232836)
private val BG_B = Color(0xFF2C3243)

@Composable
fun BejeweledScreen(onBack: () -> Unit, vm: BejeweledViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val scores by vm.highScores.collectAsStateWithLifecycle()
    val entries = scores[Game.BEJEWELED.key].orEmpty()

    GameScaffold(
        game = Game.BEJEWELED,
        entries = entries,
        inProgress = state.inProgress,
        onBack = onBack,
        onNewGame = vm::newGame,
    ) { padding ->
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(12.dp),
        ) {
            Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text("Punkte", style = MaterialTheme.typography.labelMedium)
                    Text(formatScore(state.score), fontSize = 30.sp, fontWeight = FontWeight.Bold)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        if (state.chain >= 2) "Kombo ×${state.chain}" else "Züge: ${state.moves}",
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Text(
                        if (state.lastGain > 0) "+${formatScore(state.lastGain)}" else " ",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            GameBoard(
                view = state.board,
                onTap = vm::tap,
                onSwipe = vm::swipe,
                background = { cell -> drawChecker(cell, state.board.grid.rows, state.board.grid.cols) },
            ) { piece, index, center, cell ->
                val frame = when (index) {
                    state.selected -> Color.White
                    state.hint?.first, state.hint?.second -> GOLD
                    else -> null
                }
                if (frame != null) {
                    drawRoundRect(
                        frame,
                        topLeft = center - Offset(cell * 0.47f, cell * 0.47f),
                        size = Size(cell * 0.94f, cell * 0.94f),
                        cornerRadius = CornerRadius(cell * 0.15f),
                        style = Stroke(width = cell * 0.07f),
                    )
                }
                val hit = index in state.matched
                if (hit) drawCircle(Color.White.copy(alpha = 0.45f), cell * 0.5f, center)
                drawGem(piece.color, center, cell * (if (hit) 0.44f else 0.38f))
            }

            val over = state.gameOver
            if (over != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Spiel vorbei – ${formatScore(over.score)} Punkte", modifier = Modifier.weight(1f))
                    Button(onClick = vm::newGame) { Text("Neues Spiel") }
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Zwei benachbarte Steine tauschen (wischen oder nacheinander antippen), " +
                            "sodass 3 gleiche in einer Reihe liegen. Ketten zählen doppelt, dreifach …",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedButton(onClick = vm::showHint) { Text("Tipp") }
                }
            }
        }

        val over = state.gameOver
        if (over != null && state.showGameOver) {
            GameOverDialog(over, entries, onNewGame = vm::newGame, onDismiss = vm::dismissGameOver)
        }
    }
}

private fun DrawScope.drawChecker(cell: Float, rows: Int, cols: Int) {
    for (r in 0 until rows) for (c in 0 until cols) {
        drawRect(if ((r + c) % 2 == 0) BG_A else BG_B, Offset(c * cell, r * cell), Size(cell, cell))
    }
}

private fun DrawScope.drawGem(color: Int, center: Offset, radius: Float) {
    val base = GEM_COLORS[color]
    val path: Path = when (color) {
        0 -> polygon(center, radius * 1.2f, 4, rotation = 45f)   // Quadrat
        1 -> polygon(center, radius * 1.05f, 6, rotation = 0f)   // Sechseck
        2 -> polygon(center, radius * 1.15f, 4, rotation = -90f) // Raute
        3 -> polygon(center, radius * 1.08f, 8, rotation = 22.5f) // Achteck
        4 -> polygon(center + Offset(0f, -radius * 0.15f), radius * 1.2f, 3, rotation = 90f) // Dreieck ▼
        5 -> polygon(center + Offset(0f, radius * 0.15f), radius * 1.2f, 3, rotation = -90f) // Dreieck ▲
        else -> Path().apply {
            addOval(androidx.compose.ui.geometry.Rect(center, radius))
        }
    }
    drawPath(
        path,
        Brush.linearGradient(
            listOf(base.lighter(0.5f), base, base.darker(0.35f)),
            start = center - Offset(radius, radius),
            end = center + Offset(radius, radius),
        ),
    )
    drawPath(path, base.darker(0.45f), style = Stroke(width = radius * 0.1f))
    // Glanzlicht
    drawCircle(Color.White.copy(alpha = 0.6f), radius * 0.16f, center - Offset(radius * 0.3f, radius * 0.32f))
}
