package de.jfricker.mypocketknife.tools.dice.maedn

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.jfricker.mypocketknife.tools.dice.DieFace
import kotlin.math.min

private val BOARD_BG = Color(0xFFF3E2B3)
private val LINE = Color(0xFF6D5A3A)

private const val RULES = """Immer 4 Farben; jede Farbe spielt ein Mensch oder der Computer.
Mit einer 6 muss eine Figur aus dem Haus aufs Startfeld (solange es nicht von einer eigenen Figur besetzt ist). Nach einer 6 darf man nochmals würfeln.
Solange noch Figuren im Haus sind, muss das eigene Startfeld geräumt werden.
Kann man ohne 6 nicht ziehen (alle im Haus oder fertig im Ziel), hat man 3 Versuche.
Landet man auf einer fremden Figur, wird sie geschlagen und muss zurück ins Haus. Eigene Figuren blockieren das Feld. Es gibt keinen Schlagzwang.
Ins Ziel nur mit genauer Augenzahl; im Ziel wird nicht übersprungen.
Gespielt wird, bis alle Menschen fertig sind (oder nur noch einer übrig ist)."""

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MaednScreen(onBack: () -> Unit, vm: MaednViewModel = viewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    var showRules by rememberSaveable { mutableStateOf(false) }
    var confirmNew by rememberSaveable { mutableStateOf(false) }

    // Computer spielt nur, solange man zuschaut
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.setActive(true) }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { vm.setActive(false) }
    DisposableEffect(Unit) {
        vm.setActive(true)
        onDispose { vm.setActive(false) }
    }
    val view = LocalView.current
    DisposableEffect(ui.inProgress) {
        view.keepScreenOn = ui.inProgress
        onDispose { view.keepScreenOn = false }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mensch ärgere dich nicht") },
                navigationIcon = { IconButton(onClick = onBack) { Text("←", fontSize = 22.sp) } },
                actions = {
                    if (ui.state != null) {
                        IconButton(onClick = vm::toggleFast) {
                            Text(if (ui.fast) "⏩" else "▶", fontSize = 20.sp)
                        }
                    }
                    IconButton(onClick = { showRules = true }) { Text("ℹ", fontSize = 20.sp) }
                    if (ui.state != null) {
                        TextButton(onClick = { if (ui.inProgress) confirmNew = true else vm.newGame() }) { Text("Neu") }
                    }
                },
            )
        }
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            val state = ui.state
            when {
                !ui.loaded -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                state == null -> Setup(ui, vm)
                else -> Play(state, ui, vm)
            }
        }
    }

    if (showRules) {
        AlertDialog(
            onDismissRequest = { showRules = false },
            title = { Text("Regeln") },
            text = { Text(RULES, modifier = Modifier.verticalScroll(rememberScrollState())) },
            confirmButton = { TextButton(onClick = { showRules = false }) { Text("OK") } },
        )
    }
    if (confirmNew) {
        AlertDialog(
            onDismissRequest = { confirmNew = false },
            title = { Text("Neues Spiel?") },
            text = { Text("Das laufende Spiel wird abgebrochen.") },
            confirmButton = { TextButton(onClick = { confirmNew = false; vm.newGame() }) { Text("Neues Spiel") } },
            dismissButton = { TextButton(onClick = { confirmNew = false }) { Text("Weiterspielen") } },
        )
    }
    val state = ui.state
    if (state != null && state.over && ui.showResult) {
        ResultDialog(state, onRematch = vm::rematch, onDismiss = vm::dismissResult)
    }
}

// ---------------------------------------------------------------------- Plätze wählen

@Composable
private fun Setup(ui: MaednUi, vm: MaednViewModel) {
    val computers = ui.setup.count { it.computer }
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Text("Wer spielt welche Farbe?", style = MaterialTheme.typography.titleMedium)
        ui.setup.forEachIndexed { i, seat ->
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        Modifier
                            .size(22.dp)
                            .background(MaednBoard.COLORS[i], CircleShape)
                    )
                    Text(MaednBoard.NAMES[i], fontWeight = FontWeight.Bold, modifier = Modifier.width(52.dp))
                    FilterChip(selected = !seat.computer, onClick = { vm.setComputer(i, false) }, label = { Text("Mensch") })
                    FilterChip(selected = seat.computer, onClick = { vm.setComputer(i, true) }, label = { Text("Computer") })
                }
                if (!seat.computer) {
                    OutlinedTextField(
                        value = seat.name,
                        onValueChange = { vm.setName(i, it) },
                        label = { Text("Name") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
        if (computers !in 1..3) {
            Text(
                "Es braucht 1 bis 3 Computer-Spieler.",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Button(onClick = vm::start, enabled = computers in 1..3, modifier = Modifier.fillMaxWidth()) {
            Text("Spiel starten")
        }
        Text("Rot beginnt, dann geht es im Uhrzeigersinn weiter.", style = MaterialTheme.typography.bodySmall)
    }
}

// ---------------------------------------------------------------------- Spielen

@Composable
private fun Play(state: MaednState, ui: MaednUi, vm: MaednViewModel) {
    val p = state.current
    val seat = state.seats[p]
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
    ) {
        BoxWithConstraints(
            Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.TopCenter,
        ) {
            val side = min(maxWidth.value, maxHeight.value).dp
            Board(ui, state, vm::pick, Modifier.size(side))
        }

        if (state.over) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Spiel beendet", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = vm::rematch) { Text("Revanche") }
                Button(onClick = vm::newGame) { Text("Neues Spiel") }
            }
            return@Column
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            val canRoll = ui.phase == Phase.ROLL && ui.humanTurn
            DieFace(
                value = ui.shownDie ?: ui.die ?: 6,
                held = false,
                faded = ui.die == null && ui.shownDie == null,
                size = 64.dp,
                onClick = { if (canRoll) vm.roll() },
            )
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(
                        Modifier
                            .size(14.dp)
                            .background(MaednBoard.COLORS[p], CircleShape)
                    )
                    Text(
                        if (seat.computer) "${MaednBoard.NAMES[p]} (Computer)" else seat.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Text(
                    ui.message ?: when {
                        seat.computer -> "spielt …"
                        ui.phase == Phase.ROLL && ui.triesLeft > 1 -> "Würfeln – ${ui.triesLeft} Versuche für eine 6"
                        ui.phase == Phase.ROLL -> "Würfeln"
                        ui.phase == Phase.MOVE -> "Figur antippen"
                        else -> " "
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (!seat.computer) {
                Button(onClick = vm::roll, enabled = ui.phase == Phase.ROLL) { Text("Würfeln") }
            }
        }
    }
}

@Composable
private fun Board(ui: MaednUi, state: MaednState, onPick: (Int) -> Unit, modifier: Modifier) {
    val pieces = ui.pieces ?: state.pieces
    val movable = if (ui.phase == Phase.MOVE && ui.humanTurn) ui.movable else emptyList()
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(550), RepeatMode.Reverse),
        label = "pulse",
    )
    Canvas(
        modifier.pointerInput(movable, state.current) {
            detectTapGestures { pos ->
                val cell = size.width / MaednBoard.SIZE.toFloat()
                val col = (pos.x / cell).toInt()
                val row = (pos.y / cell).toInt()
                movable.firstOrNull { i ->
                    MaednBoard.cellOf(state.current, pieces[state.current][i], i) == BoardCell(col, row)
                }?.let(onPick)
            }
        }
    ) {
        val cell = size.width / MaednBoard.SIZE
        fun center(c: BoardCell) = Offset((c.col + 0.5f) * cell, (c.row + 0.5f) * cell)

        drawRoundRect(BOARD_BG, cornerRadius = CornerRadius(cell * 0.4f))
        // Verbindungslinien der Laufbahn
        val track = MaednBoard.TRACK
        track.indices.forEach { i ->
            drawLine(LINE, center(track[i]), center(track[(i + 1) % track.size]), strokeWidth = cell * 0.06f)
        }
        // Felder
        track.forEachIndexed { i, c ->
            val owner = (0 until 4).firstOrNull { MaednLogic.startOf(it) == i }
            field(center(c), cell, owner?.let { MaednBoard.COLORS[it] })
        }
        for (p in 0 until 4) {
            val light = lerp(MaednBoard.COLORS[p], Color.White, 0.55f)
            MaednBoard.GOAL[p].forEach { field(center(it), cell, light) }
            MaednBoard.HOME[p].forEach { field(center(it), cell, light) }
        }
        // Figuren
        for (p in 0 until 4) {
            pieces[p].forEachIndexed { i, rel ->
                val c = center(MaednBoard.cellOf(p, rel, i))
                val canMove = p == state.current && i in movable
                if (canMove) {
                    drawCircle(Color.White.copy(alpha = pulse), cell * 0.47f, c)
                    drawCircle(Color.Black.copy(alpha = pulse * 0.6f), cell * 0.47f, c, style = Stroke(cell * 0.05f))
                }
                pawn(c, cell, MaednBoard.COLORS[p])
            }
        }
    }
}

private fun DrawScope.field(c: Offset, cell: Float, fill: Color?) {
    drawCircle(fill ?: Color.White, cell * 0.38f, c)
    drawCircle(LINE, cell * 0.38f, c, style = Stroke(cell * 0.05f))
}

private fun DrawScope.pawn(c: Offset, cell: Float, color: Color) {
    drawCircle(Color.Black.copy(alpha = 0.25f), cell * 0.3f, c + Offset(cell * 0.03f, cell * 0.05f))
    drawCircle(color, cell * 0.3f, c)
    drawCircle(lerp(color, Color.Black, 0.4f), cell * 0.3f, c, style = Stroke(cell * 0.05f))
    drawCircle(lerp(color, Color.White, 0.5f), cell * 0.12f, c - Offset(cell * 0.08f, cell * 0.08f))
}

// ---------------------------------------------------------------------- Ergebnis

@Composable
private fun ResultDialog(state: MaednState, onRematch: () -> Unit, onDismiss: () -> Unit) {
    val winner = state.ranking.first()
    val winnerSeat = state.seats[winner]
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (winnerSeat.computer) "${MaednBoard.NAMES[winner]} (Computer) gewinnt"
                else "🏆 ${winnerSeat.name} gewinnt!"
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                state.ranking.forEachIndexed { place, p ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("${place + 1}.", modifier = Modifier.width(24.dp))
                        Box(
                            Modifier
                                .size(16.dp)
                                .background(MaednBoard.COLORS[p], CircleShape)
                        )
                        Text(if (state.seats[p].computer) "${MaednBoard.NAMES[p]} (Computer)" else state.seats[p].name)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onRematch) { Text("Revanche") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Schliessen") } },
    )
}
