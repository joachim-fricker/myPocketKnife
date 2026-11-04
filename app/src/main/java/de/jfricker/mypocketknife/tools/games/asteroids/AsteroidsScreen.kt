package de.jfricker.mypocketknife.tools.games.asteroids

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotateRad
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.jfricker.mypocketknife.tools.games.Game
import de.jfricker.mypocketknife.tools.games.GameOverDialog
import de.jfricker.mypocketknife.tools.games.GameScaffold
import de.jfricker.mypocketknife.tools.games.asteroids.AsteroidsEngine.Companion.H
import de.jfricker.mypocketknife.tools.games.asteroids.AsteroidsEngine.Companion.W
import de.jfricker.mypocketknife.tools.games.formatScore
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

private val SPACE = Color(0xFF000000)
private val LINE = Color(0xFFEFEFEF)
private const val STROKE = 1.4f

/** Abstand zwischen den beiden Knöpfen links (◀ ▶) bzw. rechts (▲ ●). */
private val PAIR_GAP = 24.dp

@Composable
fun AsteroidsScreen(onBack: () -> Unit, vm: AsteroidsViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val scores by vm.highScores.collectAsStateWithLifecycle()
    val entries = scores[Game.ASTEROIDS.key].orEmpty()

    // Spielschleife: ein Schritt pro Bildschirm-Bild
    var frame by remember { mutableLongStateOf(0L) }
    LaunchedEffect(state.running) {
        if (!state.running) return@LaunchedEffect
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                val dt = if (last == 0L) 0f else (now - last) / 1_000_000_000f
                last = now
                vm.tick(dt)
                frame = now
            }
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { vm.pause() }
    DisposableEffect(Unit) { onDispose { vm.pause() } }
    val view = LocalView.current
    DisposableEffect(state.running) {
        view.keepScreenOn = state.running
        onDispose { view.keepScreenOn = false }
    }

    GameScaffold(
        game = Game.ASTEROIDS,
        entries = entries,
        inProgress = state.inProgress,
        onBack = onBack,
        onNewGame = vm::newGame,
        actions = {
            if (state.running) IconButton(onClick = vm::pause) { Text("⏸", fontSize = 20.sp) }
            IconButton(onClick = vm::toggleSound) { Text(if (state.sound) "🔊" else "🔇", fontSize = 20.sp) }
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
                    Text(formatScore(state.score), fontSize = 26.sp, fontWeight = FontWeight.Bold)
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Rekord", style = MaterialTheme.typography.labelMedium)
                    Text(formatScore(maxOf(entries.firstOrNull()?.score ?: 0, state.score)), fontSize = 18.sp)
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                    Text("Welle ${state.wave}", style = MaterialTheme.typography.labelMedium)
                    Text("▲".repeat(state.lives.coerceIn(0, 8)).ifEmpty { "–" }, fontSize = 18.sp)
                }
            }

            BoxWithConstraints(
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.TopCenter,
            ) {
                val fieldW = min(maxWidth.value, maxHeight.value * W / H).dp
                val fieldMod = Modifier.size(fieldW, fieldW * H / W)
                // Tipp ins Spielfeld = Hyperraum
                Field(vm, { frame }, fieldMod.pointerInput(Unit) { detectTapGestures { vm.hyperspace() } })
                if (!state.running) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = fieldMod
                            .background(Color.Black.copy(alpha = 0.5f))
                            .clickable { vm.start() },
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                when {
                                    state.gameOver != null -> "Spiel vorbei"
                                    state.started -> "Pause"
                                    else -> "Asteroids"
                                },
                                color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold,
                            )
                            Text(
                                when {
                                    state.gameOver != null -> "Antippen für ein neues Spiel"
                                    state.started -> "Antippen zum Weiterspielen"
                                    else -> "Antippen zum Starten"
                                },
                                color = Color.White,
                            )
                            Text(
                                "◀ ▶ drehen · ▲ Schub · ● Feuer · Tipp ins Feld = Hyperraum",
                                color = Color.White.copy(alpha = 0.8f),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp),
            ) {
                // links drehen, rechts Schub/Feuer – Lücke in der Mitte für zwei Daumen
                PadKey("◀", Modifier.weight(1f)) { vm.left = it }
                Spacer(Modifier.width(PAIR_GAP))
                PadKey("▶", Modifier.weight(1f)) { vm.right = it }
                Spacer(Modifier.weight(0.35f))
                PadKey("▲", Modifier.weight(1f)) { vm.thrust = it }
                Spacer(Modifier.width(PAIR_GAP))
                PadKey("●", Modifier.weight(1.2f), accent = true) { vm.fire = it }
            }
        }

        val over = state.gameOver
        if (over != null && state.showGameOver) {
            GameOverDialog(over, entries, onNewGame = { vm.newGame(); vm.start() }, onDismiss = vm::dismissGameOver)
        }
    }
}

@Composable
private fun Field(vm: AsteroidsViewModel, frame: () -> Long, modifier: Modifier) {
    val textMeasurer = rememberTextMeasurer()
    val bannerStyle = TextStyle(color = Color.White, fontSize = 24.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
    Canvas(modifier.background(SPACE)) {
        frame() // jedes Bild neu zeichnen
        val e = vm.engine
        val s = size.width / W
        val stroke = Stroke(width = STROKE)

        scale(s, s, pivot = Offset.Zero) {
            // Felsen (mit Kopien am Rand, damit sie sauber "herüberwandern")
            e.rocks.forEach { r ->
                wrapped(r.x, r.y, r.radius) { x, y ->
                    val path = Path()
                    val n = r.shape.size
                    for (k in 0 until n) {
                        val a = r.angle + k * 2f * PI.toFloat() / n
                        val rr = r.radius * r.shape[k]
                        val px = x + cos(a) * rr
                        val py = y + sin(a) * rr
                        if (k == 0) path.moveTo(px, py) else path.lineTo(px, py)
                    }
                    path.close()
                    drawPath(path, LINE, style = stroke)
                }
            }

            // Ufo
            e.saucer?.let { u ->
                val f = if (u.small) 0.55f else 1f
                translate(u.x, u.y) {
                    scale(f, f, pivot = Offset.Zero) {
                        val body = Path().apply {
                            moveTo(-16f, 0f); lineTo(-7f, -5f); lineTo(7f, -5f); lineTo(16f, 0f)
                            lineTo(7f, 6f); lineTo(-7f, 6f); close()
                            moveTo(-16f, 0f); lineTo(16f, 0f)
                            moveTo(-6f, -5f); lineTo(-3f, -10f); lineTo(3f, -10f); lineTo(6f, -5f)
                        }
                        drawPath(body, LINE, style = Stroke(width = STROKE / f))
                    }
                }
            }

            // Schiff (blinkt, solange es unverwundbar ist)
            val blinkOn = e.invulnerable <= 0f || ((e.invulnerable * 8).toInt() % 2 == 0)
            if (e.shipAlive && blinkOn) {
                translate(e.shipX, e.shipY) {
                    rotateRad(e.shipAngle, pivot = Offset.Zero) {
                        val ship = Path().apply {
                            moveTo(0f, -11f); lineTo(7f, 8f); lineTo(0f, 4.5f); lineTo(-7f, 8f); close()
                        }
                        drawPath(ship, LINE, style = stroke)
                        if (e.thrusting && (frame() / 40_000_000L) % 2 == 0L) {
                            val flame = Path().apply { moveTo(-3.5f, 7f); lineTo(0f, 14f); lineTo(3.5f, 7f) }
                            drawPath(flame, LINE, style = stroke)
                        }
                    }
                }
            }

            // Schüsse und Trümmer
            e.shots.forEach { drawCircle(LINE, 1.4f, Offset(it.x, it.y)) }
            e.particles.forEach { p ->
                drawCircle(LINE.copy(alpha = (p.life / p.maxLife).coerceIn(0f, 1f)), 0.9f, Offset(p.x, p.y))
            }
        }

        if (e.rocks.isEmpty() && e.waveTimer > 0f) {
            val layout = textMeasurer.measure("WELLE ${e.wave + 1}", bannerStyle)
            drawText(layout, topLeft = Offset((size.width - layout.size.width) / 2f, size.height * 0.4f))
        }
    }
}

/** Zeichnet ein Objekt an seiner Position und – falls es über den Rand ragt – auch auf der anderen Seite. */
private fun DrawScope.wrapped(x: Float, y: Float, r: Float, draw: DrawScope.(Float, Float) -> Unit) {
    val xs = buildList {
        add(x)
        if (x < r) add(x + W)
        if (x > W - r) add(x - W)
    }
    val ys = buildList {
        add(y)
        if (y < r) add(y + H)
        if (y > H - r) add(y - H)
    }
    for (px in xs) for (py in ys) draw(px, py)
}

/** Knopf zum Gedrückthalten; mehrere gleichzeitig möglich. */
@Composable
private fun PadKey(label: String, modifier: Modifier, small: Boolean = false, accent: Boolean = false, onPressed: (Boolean) -> Unit) {
    var pressed by remember { mutableStateOf(false) }
    val base = if (accent) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.secondaryContainer
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxHeight()
            .background(if (pressed) base.copy(alpha = 0.55f) else base, RoundedCornerShape(16.dp))
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown().consume()
                    pressed = true
                    onPressed(true)
                    try {
                        waitForUpOrCancellation()
                    } finally {
                        pressed = false
                        onPressed(false)
                    }
                }
            },
    ) {
        Text(label, fontSize = if (small) 20.sp else 26.sp, fontWeight = FontWeight.Bold)
    }
}
