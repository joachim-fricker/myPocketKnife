package de.jfricker.mypocketknife.tools.dice.tenk

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.jfricker.mypocketknife.tools.dice.DiceResultDialog
import de.jfricker.mypocketknife.tools.dice.DieFace
import de.jfricker.mypocketknife.tools.dice.PlayerSetup
import de.jfricker.mypocketknife.tools.games.GameScaffold
import de.jfricker.mypocketknife.tools.games.formatScore

private val RULES = """
    Reihum würfeln mit 6 Würfeln. Nach jedem Wurf mindestens einen wertenden Würfel antippen (zur Seite legen).
    Dann weiterwürfeln mit den übrigen Würfeln – oder die Rundenpunkte schreiben.
    Ein Wurf ohne Wertung ist eine Niete: alle Punkte dieser Runde sind weg.
    Sind alle 6 Würfel gewertet, darf man wieder mit allen 6 würfeln.

    Wertung (nur innerhalb eines Wurfs):
    • 1 = 100, 5 = 50
    • drei gleiche = Augen × 100, drei Einsen = 1000
    • jeder weitere gleiche Würfel verdoppelt (vier Dreier = 600, fünf = 1200 …)
    • Strasse 1–6 = 1500, drei Paare = 1500

    Schreiben darf man nur, wenn die Runde mindestens 350 Punkte ergibt – jedes Mal.
    Wer 10000 erreicht, löst die letzte Runde aus: Alle anderen haben noch einen Zug, die höchste Punktzahl gewinnt.

    Bestenliste: wer 10000 erreicht, mit den wenigsten Runden (jeder eigene Zug zählt, auch Nieten).
""".trimIndent()

@Composable
fun TenkScreen(onBack: () -> Unit, vm: TenkViewModel = viewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val scores by vm.scores.collectAsStateWithLifecycle()
    val entries = scores[TENK_RECORD_KEY].orEmpty()
    var showRules by rememberSaveable { mutableStateOf(false) }

    GameScaffold(
        title = "10000",
        entries = entries,
        inProgress = ui.inProgress,
        onBack = onBack,
        onNewGame = vm::newGame,
        actions = { IconButton(onClick = { showRules = true }) { Text("ℹ", fontSize = 20.sp) } },
        scoreLabel = ::roundsLabel,
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            val state = ui.state
            when {
                !ui.loaded -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                state == null -> PlayerSetup(ui.setupCount, ui.setupNames, vm::setCount, vm::setName, vm::start)
                else -> Play(state, ui, vm)
            }
        }

        val result = ui.result
        if (result != null && ui.showResult) {
            DiceResultDialog(result, "10000", entries, onRematch = vm::rematch, onDismiss = vm::dismissResult, scoreLabel = ::roundsLabel)
        }
        if (showRules) {
            AlertDialog(
                onDismissRequest = { showRules = false },
                title = { Text("Regeln 10000") },
                text = { Text(RULES, modifier = Modifier.verticalScroll(rememberScrollState())) },
                confirmButton = { TextButton(onClick = { showRules = false }) { Text("OK") } },
            )
        }
    }
}

@Composable
private fun Play(state: TenkState, ui: TenkUi, vm: TenkViewModel) {
    val sel = if (state.rolled && !state.bust) TenkLogic.selectionScore(state) else null
    val anySelected = state.selected.any { it }
    val bankable = TenkLogic.bankable(state)

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        // Punktestand aller Spieler
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            state.players.forEachIndexed { i, p ->
                val current = i == state.current && !state.finished
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            if (current) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                            RoundedCornerShape(8.dp),
                        )
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    Text(
                        p.name + if (i == state.finalRoundBy) " 🏁" else "",
                        fontWeight = if (current) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(formatScore(p.total), fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
            }
        }
        if (state.finalRoundBy != null && !state.finished) {
            Text(
                "Letzte Runde! ${state.players[state.finalRoundBy].name} hat 10000 erreicht.",
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
        }
        HorizontalDivider()

        if (state.finished) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Spiel beendet", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (state.players.size > 1) TextButton(onClick = vm::rematch) { Text("Revanche") }
                Button(onClick = vm::newGame) { Text("Neues Spiel") }
            }
            return@Column
        }

        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                "${state.player.name} ist dran",
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text("Runde ${state.player.turns + 1}", style = MaterialTheme.typography.labelLarge)
        }
        state.message?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline) }

        // Würfel: zur Seite gelegte grün (zählen schon), ausgewählte gold
        val dice = ui.shownDice ?: state.dice
        Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
            dice.forEachIndexed { i, d ->
                DieFace(
                    value = d,
                    held = state.selected[i],
                    faded = !state.rolled && !ui.rolling,
                    size = 50.dp,
                    onClick = { vm.toggle(i) },
                    kept = state.kept[i] && state.rolled,
                )
            }
        }

        ui.hint?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }

        // Punkte dieser Runde
        Text(
            buildString {
                append("Runde: ${formatScore(state.turnPoints)}")
                if (anySelected) append(if (sel != null) "  +  Auswahl ${formatScore(sel)}" else "  +  Auswahl zählt nicht")
            },
            style = MaterialTheme.typography.titleMedium,
            color = if (anySelected && sel == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        )

        if (state.bust) {
            Text(
                "Niete! " + if (state.turnPoints > 0) "Die ${formatScore(state.turnPoints)} Punkte dieser Runde sind weg." else "Kein Punkt in diesem Wurf.",
                color = MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.Bold,
            )
            Button(onClick = vm::pass, modifier = Modifier.fillMaxWidth()) { Text("Weiter zum nächsten Spieler") }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = vm::roll,
                    enabled = TenkLogic.canRoll(state) && !ui.rolling,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(if (!state.rolled || sel == null) "Würfeln" else "Würfeln (${TenkLogic.diceForNextRoll(state)})")
                }
                OutlinedButton(
                    onClick = vm::bank,
                    enabled = bankable != null && !ui.rolling,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(if (bankable != null) "Schreiben ${formatScore(bankable)}" else "Schreiben")
                }
            }
            Text(
                when {
                    !state.rolled -> "Mit allen 6 Würfeln würfeln."
                    !anySelected -> "Wertende Würfel antippen (1, 5, Pasch …)."
                    sel == null -> "Nur Würfel wählen, die zählen."
                    bankable == null ->
                        "Schreiben erst ab ${TenkLogic.ENTRY} Punkten in einer Runde."
                    else -> "Weiterwürfeln oder Punkte schreiben."
                },
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
