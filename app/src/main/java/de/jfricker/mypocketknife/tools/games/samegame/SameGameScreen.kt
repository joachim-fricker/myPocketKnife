package de.jfricker.mypocketknife.tools.games.samegame

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.jfricker.mypocketknife.tools.games.Game
import de.jfricker.mypocketknife.tools.games.GameBoard
import de.jfricker.mypocketknife.tools.games.GameOverDialog
import de.jfricker.mypocketknife.tools.games.GameScaffold
import de.jfricker.mypocketknife.tools.games.drawBall
import de.jfricker.mypocketknife.tools.games.formatScore

private val BALL_COLORS = listOf(
    Color(0xFFE53935), // rot
    Color(0xFF1E88E5), // blau
    Color(0xFF43A047), // grün
    Color(0xFFFDD835), // gelb
    Color(0xFF8E24AA), // violett
)
private val BOARD_BG = Color(0xFF1B1F2A)
private val SELECT_BG = Color(0x55FFFFFF)

@Composable
fun SameGameScreen(onBack: () -> Unit, vm: SameGameViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val scores by vm.highScores.collectAsStateWithLifecycle()
    val entries = scores[Game.SAME_GAME.key].orEmpty()

    GameScaffold(
        game = Game.SAME_GAME,
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
                        if (state.selection.isEmpty()) "Übrig: ${state.remaining}"
                        else "${state.selection.size} Steine",
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Text(
                        if (state.selection.isEmpty()) " " else "+${formatScore(state.selectionPoints)}",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            GameBoard(
                view = state.board,
                onTap = vm::tap,
                background = { drawRect(BOARD_BG) },
            ) { piece, index, center, cell ->
                if (index in state.selection) {
                    drawRoundRect(
                        SELECT_BG,
                        topLeft = center - Offset(cell / 2, cell / 2),
                        size = Size(cell, cell),
                        cornerRadius = CornerRadius(cell * 0.2f),
                    )
                }
                drawBall(BALL_COLORS[piece.color], center, cell * 0.44f)
            }

            val over = state.gameOver
            if (over != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Spiel vorbei – ${formatScore(over.score)} Punkte", modifier = Modifier.weight(1f))
                    Button(onClick = vm::newGame) { Text("Neues Spiel") }
                }
            } else {
                Text(
                    "Tippe eine Gruppe an, um sie zu markieren – nochmals tippen entfernt sie. " +
                        "Punkte = n · (n − 1), leeres Feld +${formatScore(SameGameLogic.CLEAR_BONUS)}.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        val over = state.gameOver
        if (over != null && state.showGameOver) {
            GameOverDialog(over, entries, onNewGame = vm::newGame, onDismiss = vm::dismissGameOver)
        }
    }
}
