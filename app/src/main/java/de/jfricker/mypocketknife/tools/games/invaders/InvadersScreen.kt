package de.jfricker.mypocketknife.tools.games.invaders

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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
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
import de.jfricker.mypocketknife.tools.games.invaders.InvadersEngine.Companion.BUNKER_H
import de.jfricker.mypocketknife.tools.games.invaders.InvadersEngine.Companion.BUNKER_W
import de.jfricker.mypocketknife.tools.games.invaders.InvadersEngine.Companion.BUNKER_X
import de.jfricker.mypocketknife.tools.games.invaders.InvadersEngine.Companion.BUNKER_Y
import de.jfricker.mypocketknife.tools.games.invaders.InvadersEngine.Companion.COLS
import de.jfricker.mypocketknife.tools.games.invaders.InvadersEngine.Companion.GROUND_Y
import de.jfricker.mypocketknife.tools.games.invaders.InvadersEngine.Companion.H
import de.jfricker.mypocketknife.tools.games.invaders.InvadersEngine.Companion.PLAYER_W
import de.jfricker.mypocketknife.tools.games.invaders.InvadersEngine.Companion.PLAYER_Y
import de.jfricker.mypocketknife.tools.games.invaders.InvadersEngine.Companion.UFO_Y
import de.jfricker.mypocketknife.tools.games.invaders.InvadersEngine.Companion.W
import kotlin.math.min

private val SPACE = Color(0xFF05060C)
private val GREEN = Color(0xFF3CF060)
private val INVADER_COLORS = listOf(Color(0xFFFF5CD6), Color(0xFF5CE1FF), Color(0xFFB7FF5C))
private val UFO_COLOR = Color(0xFFFF4040)
private val SHOT_COLOR = Color.White
private val ENEMY_SHOT_COLOR = Color(0xFFFFE066)

/** Pfade der Pixel-Figuren, einmal erzeugt. */
private object SpritePaths {
    val invaders = Sprites.INVADERS.map { frames -> frames.map { path(it) } }
    val cannon = path(Sprites.CANNON)
    val cannonBurst = path(Sprites.CANNON_BURST)
    val saucer = path(Sprites.SAUCER)
    val burst = path(Sprites.BURST)

    fun path(sprite: List<String>) = Path().apply {
        sprite.forEachIndexed { y, line ->
            line.forEachIndexed { x, ch ->
                if (ch == '#') addRect(androidx.compose.ui.geometry.Rect(x.toFloat(), y.toFloat(), x + 1f, y + 1f))
            }
        }
    }
}

/** Bunker-Pfad, nur neu gebaut, wenn sich die Bunker geändert haben. */
private class BunkerCache {
    var version = -1
    var engine: InvadersEngine? = null
    var path = Path()

    fun get(e: InvadersEngine): Path {
        if (e.bunkerVersion != version || e !== engine) {
            version = e.bunkerVersion
            engine = e
            path = Path().apply {
                e.bunkers.forEachIndexed { b, pixels ->
                    for (y in 0 until BUNKER_H) for (x in 0 until BUNKER_W) {
                        if (pixels[y * BUNKER_W + x]) {
                            val l = BUNKER_X[b] + x
                            val t = BUNKER_Y + y
                            addRect(androidx.compose.ui.geometry.Rect(l, t, l + 1f, t + 1f))
                        }
                    }
                }
            }
        }
        return path
    }
}

@Composable
fun InvadersScreen(onBack: () -> Unit, vm: InvadersViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val scores by vm.highScores.collectAsStateWithLifecycle()
    val entries = scores[Game.INVADERS.key].orEmpty()
    val settings = state.settings
    var showSettings by rememberSaveable { mutableStateOf(false) }

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

    // Pause beim Verlassen / App im Hintergrund; Display an während des Spiels
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { vm.pause() }
    DisposableEffect(Unit) { onDispose { vm.pause() } }
    val view = LocalView.current
    DisposableEffect(state.running) {
        view.keepScreenOn = state.running
        onDispose { view.keepScreenOn = false }
    }

    GameScaffold(
        game = Game.INVADERS,
        entries = entries,
        inProgress = state.inProgress,
        onBack = onBack,
        onNewGame = vm::newGame,
        actions = {
            if (state.running) IconButton(onClick = vm::pause) { Text("⏸", fontSize = 20.sp) }
            IconButton(onClick = { vm.pause(); showSettings = true }) { Text("⚙", fontSize = 22.sp) }
        },
    ) { padding ->
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Hud(state.score, state.wave, state.lives, best = entries.firstOrNull()?.score ?: 0)

            BoxWithConstraints(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                val fieldW = min(maxWidth.value, maxHeight.value * W / H).dp
                Box(
                    Modifier
                        .fillMaxSize()
                        .pointerInput(settings.mode) {
                            if (settings.mode == ControlMode.BUTTONS) return@pointerInput
                            awaitEachGesture {
                                val fw = min(size.width.toFloat(), size.height * W / H)
                                val offset = (size.width - fw) / 2
                                fun toWorld(x: Float) = ((x - offset) / fw * W).coerceIn(0f, W)
                                val down = awaitFirstDown()
                                vm.touching = true
                                vm.touchX = toWorld(down.position.x)
                                try {
                                    while (true) {
                                        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                                        if (!change.pressed) break
                                        vm.touchX = toWorld(change.position.x)
                                        change.consume()
                                    }
                                } finally {
                                    vm.touching = false
                                    vm.touchX = null
                                }
                            }
                        }
                ) {
                    PlayField(vm, { frame }, Modifier
                        .align(Alignment.TopCenter)
                        .size(fieldW, fieldW * H / W))

                    if (!state.running) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .size(fieldW, fieldW * H / W)
                                .background(Color.Black.copy(alpha = 0.55f))
                                .clickable { vm.start() },
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    when {
                                        state.gameOver != null -> "Spiel vorbei"
                                        state.started -> "Pause"
                                        else -> "Invaders"
                                    },
                                    color = Color.White,
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.Bold,
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
                                    "Steuerung: ${settings.mode.title} (⚙ zum Ändern)",
                                    color = Color.White.copy(alpha = 0.8f),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }
            }

            if (settings.mode == ControlMode.BUTTONS) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(84.dp),
                ) {
                    HoldButton("◀", Modifier.weight(1f)) { vm.leftPressed = it }
                    HoldButton(if (settings.autoFire) "Auto-Feuer" else "FEUER", Modifier.weight(1.2f), accent = true) {
                        vm.firePressed = it
                    }
                    HoldButton("▶", Modifier.weight(1f)) { vm.rightPressed = it }
                }
            }
        }

        val over = state.gameOver
        if (over != null && state.showGameOver) {
            GameOverDialog(over, entries, onNewGame = { vm.newGame(); vm.start() }, onDismiss = vm::dismissGameOver)
        }
        if (showSettings) {
            SettingsDialog(
                settings = settings,
                onChange = vm::updateSettings,
                onDismiss = { showSettings = false },
            )
        }
    }
}

@Composable
private fun Hud(score: Int, wave: Int, lives: Int, best: Int) {
    Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f)) {
            Text("Punkte", style = MaterialTheme.typography.labelMedium)
            Text(formatScore(score), fontSize = 26.sp, fontWeight = FontWeight.Bold)
        }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Rekord", style = MaterialTheme.typography.labelMedium)
            Text(formatScore(maxOf(best, score)), fontSize = 18.sp)
        }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
            Text("Welle $wave", style = MaterialTheme.typography.labelMedium)
            Text("▲".repeat(lives.coerceIn(0, 6)).ifEmpty { "–" }, fontSize = 18.sp, color = Color(0xFF2EBD4E))
        }
    }
}

@Composable
private fun PlayField(vm: InvadersViewModel, frame: () -> Long, modifier: Modifier) {
    val bunkerCache = remember { BunkerCache() }
    val textMeasurer = rememberTextMeasurer()
    val popupStyle = TextStyle(color = UFO_COLOR, fontSize = 16.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
    val bannerStyle = TextStyle(color = Color.White, fontSize = 26.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)

    Canvas(modifier.background(SPACE)) {
        frame() // Zustand lesen → jedes Bild neu zeichnen (ohne Neuaufbau der Oberfläche)
        val e = vm.engine
        val s = size.width / W

        scale(s, s, pivot = Offset.Zero) {
            // Boden
            drawRect(GREEN, Offset(0f, GROUND_Y), Size(W, 1f))
            // Bunker
            drawPath(bunkerCache.get(e), GREEN)

            // Invader
            for (i in e.alive.indices) {
                if (!e.alive[i]) continue
                val r = i / COLS
                val c = i % COLS
                val type = InvadersEngine.rowType(r)
                sprite(SpritePaths.invaders[type][e.animFrame], e.invaderLeft(r, c), e.invaderTop(r), INVADER_COLORS[type])
            }
            // Explosionen
            e.effects.filter { it.text == null }.forEach { sprite(SpritePaths.burst, it.x - 6f, it.y, Color.White) }

            // Ufo
            e.ufoX?.let { sprite(SpritePaths.saucer, it, UFO_Y, UFO_COLOR) }

            // Kanone
            val px = e.playerX - PLAYER_W / 2
            if (e.dyingTimer > 0f) {
                if (((e.dyingTimer * 8).toInt() % 2) == 0) sprite(SpritePaths.cannonBurst, px, PLAYER_Y, GREEN)
            } else if (!e.gameOver) {
                sprite(SpritePaths.cannon, px, PLAYER_Y, GREEN)
            }

            // Schüsse
            e.playerBullet?.let { drawRect(SHOT_COLOR, Offset(it.x - 0.5f, it.y), Size(1f, 4f)) }
            e.enemyBullets.forEach { b ->
                val wiggle = if (((b.y / 3).toInt() % 2) == 0) -0.5f else 0.5f
                drawRect(ENEMY_SHOT_COLOR, Offset(b.x - 0.5f + wiggle, b.y), Size(1f, 6f))
            }
        }

        // Texte in Bildschirm-Koordinaten (sonst würden sie mitskaliert)
        e.effects.filter { it.text != null }.forEach { fx ->
            val layout = textMeasurer.measure(fx.text!!, popupStyle)
            drawText(layout, topLeft = Offset(fx.x * s - layout.size.width / 2f, fx.y * s))
        }
        if (e.waveTimer > 0f) banner(textMeasurer, "WELLE ${e.wave + 1}", bannerStyle)
    }
}

private fun DrawScope.sprite(path: Path, x: Float, y: Float, color: Color) {
    translate(x, y) { drawPath(path, color) }
}

private fun DrawScope.banner(tm: androidx.compose.ui.text.TextMeasurer, text: String, style: TextStyle) {
    val layout = tm.measure(text, style.copy(textAlign = TextAlign.Center))
    drawText(layout, topLeft = Offset((size.width - layout.size.width) / 2f, size.height * 0.45f))
}

/** Knopf, der gedrückt gehalten werden kann; mehrere gleichzeitig möglich (Mehrfinger). */
@Composable
private fun HoldButton(label: String, modifier: Modifier, accent: Boolean = false, onPressed: (Boolean) -> Unit) {
    var pressed by remember { mutableStateOf(false) }
    val base = if (accent) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.secondaryContainer
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxSize()
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
        Text(label, fontSize = 26.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SettingsDialog(
    settings: InvaderSettings,
    onChange: (InvaderSettings) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Einstellungen") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Steuerung", style = MaterialTheme.typography.titleSmall)
                ControlMode.entries.forEach { mode ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = settings.mode == mode,
                                role = Role.RadioButton,
                                onClick = { onChange(settings.copy(mode = mode)) },
                            ),
                    ) {
                        RadioButton(selected = settings.mode == mode, onClick = null)
                        Column(Modifier.padding(start = 8.dp, top = 4.dp, bottom = 4.dp)) {
                            Text(mode.title)
                            Text(
                                mode.description,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                SwitchRow("Automatisch schiessen", "Feuert dauernd, ohne Finger oder Knopf", settings.autoFire) {
                    onChange(settings.copy(autoFire = it))
                }
                SwitchRow("Ton", null, settings.sound) { onChange(settings.copy(sound = it)) }
                SwitchRow("Vibration bei Treffer", null, settings.vibration) { onChange(settings.copy(vibration = it)) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
    )
}

@Composable
private fun SwitchRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f)) {
            Text(title)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
