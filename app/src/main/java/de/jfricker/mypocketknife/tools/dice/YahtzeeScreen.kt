package de.jfricker.mypocketknife.tools.dice

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.jfricker.mypocketknife.tools.games.GameScaffold
import de.jfricker.mypocketknife.tools.games.HighScoreTable
import de.jfricker.mypocketknife.tools.games.ScoreEntry
import de.jfricker.mypocketknife.tools.games.formatScore

/** Kniffel bzw. Yatzy für 1–4 Spieler an einem Handy. */
@Composable
fun YahtzeeScreen(rules: RuleSet, onBack: () -> Unit) {
    val vm: DiceViewModel = viewModel(key = "dice-${rules.key}", factory = DiceViewModel.factory(rules))
    val ui by vm.ui.collectAsStateWithLifecycle()
    val scores by vm.scores.collectAsStateWithLifecycle()
    val entries = scores[rules.key].orEmpty()
    var showRules by rememberSaveable { mutableStateOf(false) }

    GameScaffold(
        title = rules.title,
        entries = entries,
        inProgress = ui.inProgress,
        onBack = onBack,
        onNewGame = vm::newGame,
        actions = {
            if (ui.canUndo) IconButton(onClick = vm::undo) { Text("↶", fontSize = 22.sp) }
            IconButton(onClick = { showRules = true }) { Text("ℹ", fontSize = 20.sp) }
        },
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

        val result = ui.result
        if (result != null && ui.showResult) {
            DiceResultDialog(result, rules.title, entries, onRematch = vm::rematch, onDismiss = vm::dismissResult)
        }
        if (showRules) {
            AlertDialog(
                onDismissRequest = { showRules = false },
                title = { Text("Regeln ${rules.title}") },
                text = { Text(rules.rulesText, modifier = Modifier.verticalScroll(rememberScrollState())) },
                confirmButton = { TextButton(onClick = { showRules = false }) { Text("OK") } },
            )
        }
    }
}

// ---------------------------------------------------------------------- Spieler wählen

@Composable
private fun Setup(ui: DiceUi, vm: DiceViewModel) =
    PlayerSetup(ui.setupCount, ui.setupNames, vm::setCount, vm::setName, vm::start)

/** Spielerwahl (1–4 Namen), auch für andere Würfelspiele. */
@Composable
internal fun PlayerSetup(
    count: Int,
    names: List<String>,
    onCount: (Int) -> Unit,
    onName: (Int, String) -> Unit,
    onStart: () -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Text("Anzahl Spieler", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (1..4).forEach { n ->
                FilterChip(selected = count == n, onClick = { onCount(n) }, label = { Text("$n") })
            }
        }
        Text("Namen", style = MaterialTheme.typography.titleMedium)
        (0 until count).forEach { i ->
            OutlinedTextField(
                value = names[i],
                onValueChange = { onName(i, it) },
                label = { Text("Spieler ${i + 1}") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = if (i < count - 1) ImeAction.Next else ImeAction.Done,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) { Text("Spiel starten") }
        Text(
            "Die Namen erscheinen in der Bestenliste. Gespielt wird reihum an diesem Handy.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

// ---------------------------------------------------------------------- Spielen

@Composable
private fun Play(state: DiceState, ui: DiceUi, vm: DiceViewModel) {
    val rules = state.rules
    val options = if (ui.rolling) emptyMap() else DiceLogic.options(state)
    val round = (state.player.scores.size + 1).coerceAtMost(rules.categories.size)

    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        if (state.finished) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Spiel beendet", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (state.players.size > 1) TextButton(onClick = vm::rematch) { Text("Revanche") }
                Button(onClick = vm::newGame) { Text("Neues Spiel") }
            }
        } else {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    "${state.player.name} ist dran",
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text("Runde $round/${rules.categories.size}", style = MaterialTheme.typography.labelLarge)
            }

            val dice = ui.shownDice ?: state.dice
            Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
                dice.forEachIndexed { i, d ->
                    DieFace(
                        value = d,
                        held = state.held[i] && state.rolled,
                        faded = !state.rolled && !ui.rolling,
                        size = 54.dp,
                        onClick = { vm.toggleHold(i) },
                    )
                }
            }
            Button(
                onClick = vm::roll,
                enabled = state.rollsLeft > 0 && !ui.rolling,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    when (state.rollsLeft) {
                        3 -> "Würfeln"
                        0 -> "Feld in der Tabelle wählen"
                        else -> "Nochmals würfeln (${state.rollsLeft} übrig)"
                    }
                )
            }
            Text(
                if (state.rolled) "Würfel antippen = behalten · Feld in der Tabelle antippen = eintragen"
                else "Zuerst würfeln",
                style = MaterialTheme.typography.bodySmall,
            )
        }

        ScoreCard(
            state = state,
            options = options,
            onPick = vm::pick,
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        )
    }
}

// ---------------------------------------------------------------------- Block

/** Gemeinsame Masse/Farben einer Tabellen-Zeile. */
private class CardLayout(val state: DiceState, val colW: Dp, val currentBg: Color)

@Composable
private fun ScoreLine(
    l: CardLayout,
    label: String,
    bold: Boolean = false,
    cell: @Composable (index: Int, p: DicePlayer) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(32.dp)) {
        Text(
            label,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        l.state.players.forEachIndexed { i, p ->
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .width(l.colW)
                    .fillMaxHeight()
                    .background(if (i == l.state.current && !l.state.finished) l.currentBg else Color.Transparent),
            ) { cell(i, p) }
        }
    }
    HorizontalDivider(thickness = 0.5.dp)
}

@Composable
private fun CategoryLine(l: CardLayout, cat: Category, options: Map<String, Int>, onPick: (String) -> Unit) {
    ScoreLine(l, cat.label) { i, p ->
        val v = p.scores[cat.id]
        val option = if (i == l.state.current) options[cat.id] else null
        when {
            v != null -> Text(if (v == 0) "–" else v.toString(), style = MaterialTheme.typography.bodyMedium)
            option != null -> Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .padding(3.dp)
                    .fillMaxSize()
                    .background(
                        MaterialTheme.colorScheme.primary.copy(alpha = if (option > 0) 0.18f else 0.06f),
                        RoundedCornerShape(6.dp),
                    )
                    .clickable { onPick(cat.id) },
            ) {
                Text(
                    option.toString(),
                    color = if (option > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun ScoreCard(state: DiceState, options: Map<String, Int>, onPick: (String) -> Unit, modifier: Modifier) {
    val rules = state.rules
    val l = CardLayout(
        state,
        colW = if (state.players.size <= 2) 76.dp else 58.dp,
        currentBg = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
    )
    val totals = state.players.map { DiceLogic.totals(rules, it) }

    Column(modifier) {
        // Kopf mit Namen
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(36.dp)) {
            Spacer(Modifier.weight(1f))
            state.players.forEachIndexed { i, p ->
                Text(
                    p.name,
                    fontWeight = if (i == state.current && !state.finished) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.width(l.colW),
                )
            }
        }
        HorizontalDivider()
        rules.upperCategories.forEach { CategoryLine(l, it, options, onPick) }
        ScoreLine(l, "Summe oben", bold = true) { i, _ -> Text(totals[i].upper.toString(), fontWeight = FontWeight.Bold) }
        ScoreLine(l, "Bonus (ab ${DiceLogic.THRESHOLD})") { i, p ->
            val upperDone = rules.upperCategories.all { it.id in p.scores }
            when {
                totals[i].bonus > 0 -> Text("+${totals[i].bonus}", color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold)
                upperDone -> Text("–")
                else -> Text(
                    "noch ${DiceLogic.missingForBonus(rules, p)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }
        rules.lowerCategories.forEach { CategoryLine(l, it, options, onPick) }
        if (rules.yahtzeeBonusEnabled) {
            ScoreLine(l, "Kniffel-Bonus") { i, _ -> Text(totals[i].extraBonus.takeIf { it > 0 }?.let { "+$it" } ?: "") }
        }
        ScoreLine(l, "Gesamt", bold = true) { i, _ ->
            Text(formatScore(totals[i].total), fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }
}

// ---------------------------------------------------------------------- Ergebnis

/** Ende-Dialog mit Rangliste und Bestenliste, auch für andere Würfelspiele. */
@Composable
internal fun DiceResultDialog(
    result: DiceResult,
    title: String,
    entries: List<ScoreEntry>,
    onRematch: () -> Unit,
    onDismiss: () -> Unit,
    scoreLabel: (Int) -> String = ::formatScore,
) {
    val best = result.ranking.first()
    val tie = result.ranking.size > 1 && result.ranking[1].total == best.total
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                when {
                    result.ranking.size == 1 -> "${formatScore(best.total)} Punkte"
                    tie -> "Unentschieden!"
                    else -> "🏆 ${best.name} gewinnt!"
                }
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                result.ranking.forEachIndexed { i, p ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${i + 1}.", modifier = Modifier.width(28.dp))
                        Column(Modifier.weight(1f)) {
                            Text(p.name, fontWeight = FontWeight.Bold)
                            p.detail?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                            p.highScoreRank?.let {
                                Text(
                                    if (it == 1) "Neuer Rekord! 🏆" else "Platz $it in der Bestenliste",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                        Text(formatScore(p.total), fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }
                }
                HorizontalDivider()
                Text("Bestenliste $title", style = MaterialTheme.typography.titleSmall)
                HighScoreTable(entries, highlightAt = result.at, scoreLabel = scoreLabel)
            }
        },
        confirmButton = { TextButton(onClick = onRematch) { Text(if (result.ranking.size > 1) "Revanche" else "Nochmals") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Schliessen") } },
    )
}
