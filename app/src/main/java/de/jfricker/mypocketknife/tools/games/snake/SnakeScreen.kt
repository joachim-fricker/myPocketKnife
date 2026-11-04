package de.jfricker.mypocketknife.tools.games.snake

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.jfricker.mypocketknife.tools.games.Game
import de.jfricker.mypocketknife.tools.games.GameOverDialog
import de.jfricker.mypocketknife.tools.games.GameScaffold
import de.jfricker.mypocketknife.tools.games.formatScore
import kotlin.math.abs
import kotlin.math.min

private val FIELD = Color(0xFF9BBC0F).copy(alpha = 0.18f)
private val BORDER = Color(0xFF6B8E23)
private val SNAKE = Color(0xFF2E7D32)
private val HEAD = Color(0xFF1B5E20)
private val FOOD = Color(0xFFD32F2F)

@Composable
fun SnakeScreen(onBack: () -> Unit, vm: SnakeViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val scores by vm.highScores.collectAsStateWithLifecycle()
    val entries = scores[Game.SNAKE.key].orEmpty()
    val game = state.game

    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { vm.pause() }
    DisposableEffect(Unit) { onDispose { vm.pause() } }
    val view = LocalView.current
    DisposableEffect(state.running) {
        view.keepScreenOn = state.running
        onDispose { view.keepScreenOn = false }
    }

    GameScaffold(
        game = Game.SNAKE,
        entries = entries,
        inProgress = state.inProgress,
        onBack = onBack,
        onNewGame = vm::newGame,
        actions = {
            if (state.running) IconButton(onClick = vm::pause) { Text("⏸", fontSize = 20.sp) }
        },
    ) { padding ->
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text("Punkte", style = MaterialTheme.typography.labelMedium)
                    Text(formatScore(game.score), fontSize = 26.sp, fontWeight = FontWeight.Bold)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Rekord", style = MaterialTheme.typography.labelMedium)
                    Text(formatScore(maxOf(entries.firstOrNull()?.score ?: 0, game.score)), fontSize = 18.sp)
                }
            }

            // Wischen irgendwo im Bereich lenkt die Schlange
            BoxWithConstraints(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        val threshold = 24.dp.toPx()
                        awaitEachGesture {
                            var origin = awaitFirstDown().position
                            while (true) {
                                val change = awaitPointerEvent().changes.firstOrNull() ?: break
                                if (!change.pressed) break
                                val d = change.position - origin
                                if (maxOf(abs(d.x), abs(d.y)) > threshold) {
                                    vm.turn(
                                        if (abs(d.x) > abs(d.y)) (if (d.x > 0) Dir.RIGHT else Dir.LEFT)
                                        else (if (d.y > 0) Dir.DOWN else Dir.UP)
                                    )
                                    origin = change.position // weitere Richtung im selben Wisch möglich
                                }
                                change.consume()
                            }
                        }
                    }
            ) {
                val cellDp = min(maxWidth.value / SnakeLogic.COLS, maxHeight.value / SnakeLogic.ROWS)
                val fieldMod = Modifier
                    .align(Alignment.TopCenter)
                    .size((cellDp * SnakeLogic.COLS).dp, (cellDp * SnakeLogic.ROWS).dp)

                Canvas(fieldMod) {
                    val cell = size.width / SnakeLogic.COLS
                    drawRect(FIELD)
                    drawRect(BORDER, style = Stroke(width = 2.dp.toPx()))
                    val gap = cell * 0.08f
                    game.body.forEachIndexed { i, c ->
                        drawRoundRect(
                            if (i == 0) HEAD else SNAKE,
                            topLeft = Offset(c.x * cell + gap, c.y * cell + gap),
                            size = Size(cell - 2 * gap, cell - 2 * gap),
                            cornerRadius = CornerRadius(cell * 0.2f),
                        )
                    }
                    game.food?.let { f ->
                        drawCircle(FOOD, cell * 0.38f, Offset((f.x + 0.5f) * cell, (f.y + 0.5f) * cell))
                    }
                }

                if (!state.running && state.gameOver == null && game.alive) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = fieldMod
                            .background(Color.Black.copy(alpha = 0.45f))
                            .clickable { vm.start() },
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                if (state.started) "Pause" else "Snake",
                                color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold,
                            )
                            Text(
                                if (state.started) "Antippen zum Weiterspielen" else "Antippen zum Starten",
                                color = Color.White,
                            )
                        }
                    }
                } else if (state.gameOver != null && !state.showGameOver) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = fieldMod
                            .background(Color.Black.copy(alpha = 0.45f))
                            .clickable { vm.start() },
                    ) {
                        Text("Antippen für ein neues Spiel", color = Color.White)
                    }
                }
            }

            DirectionPad(onTurn = vm::turn, modifier = Modifier.align(Alignment.CenterHorizontally))
        }

        val over = state.gameOver
        if (over != null && state.showGameOver) {
            GameOverDialog(over, entries, onNewGame = { vm.newGame(); vm.start() }, onDismiss = vm::dismissGameOver)
        }
    }
}

/** Steuerkreuz unter dem Spielfeld; reagiert schon beim Drücken (nicht erst beim Loslassen). */
@Composable
private fun DirectionPad(onTurn: (Dir) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        PadButton("▲") { onTurn(Dir.UP) }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PadButton("◀") { onTurn(Dir.LEFT) }
            PadButton("▼") { onTurn(Dir.DOWN) }
            PadButton("▶") { onTurn(Dir.RIGHT) }
        }
    }
}

@Composable
private fun PadButton(label: String, onPress: () -> Unit) {
    var pressed by remember { mutableStateOf(false) }
    val base = MaterialTheme.colorScheme.secondaryContainer
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(width = 76.dp, height = 56.dp)
            .background(if (pressed) base.copy(alpha = 0.55f) else base, RoundedCornerShape(14.dp))
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown().consume()
                    pressed = true
                    onPress()
                    try {
                        waitForUpOrCancellation()
                    } finally {
                        pressed = false
                    }
                }
            },
    ) {
        Text(label, fontSize = 24.sp, fontWeight = FontWeight.Bold)
    }
}
