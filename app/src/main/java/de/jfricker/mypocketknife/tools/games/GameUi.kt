package de.jfricker.mypocketknife.tools.games

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Ergebnis eines beendeten Spiels. rank = Platz in der Bestenliste oder null. */
data class GameOver(val score: Int, val rank: Int?, val at: Long, val note: String? = null)

private val DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy")
private val SWISS = Locale.forLanguageTag("de-CH")
val GOLD = Color(0xFFF5B301)

fun formatScore(score: Int): String = String.format(SWISS, "%,d", score)

fun formatDate(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate().format(DATE)

/** Top 5 mit Datum; [highlightAt] markiert das gerade erspielte Ergebnis. */
@Composable
fun HighScoreTable(
    entries: List<ScoreEntry>,
    highlightAt: Long? = null,
    modifier: Modifier = Modifier,
    scoreLabel: (Int) -> String = ::formatScore,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        if (entries.isEmpty()) {
            Text("Noch keine Ergebnisse", style = MaterialTheme.typography.bodyMedium)
        }
        entries.forEachIndexed { i, e ->
            val mine = e.at == highlightAt
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        if (mine) GOLD.copy(alpha = 0.3f) else Color.Transparent,
                        RoundedCornerShape(6.dp),
                    )
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            ) {
                Text(
                    if (i == 0) "🏆" else "${i + 1}.",
                    modifier = Modifier.width(32.dp),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    scoreLabel(e.score),
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.End,
                    modifier = Modifier.widthIn(min = 64.dp),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    e.name.orEmpty(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 10.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    formatDate(e.at),
                    textAlign = TextAlign.End,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
fun GameOverDialog(over: GameOver, entries: List<ScoreEntry>, onNewGame: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                when (over.rank) {
                    1 -> "Neuer Rekord! 🏆"
                    null -> "Spiel vorbei"
                    else -> "Platz ${over.rank} in der Bestenliste"
                }
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("${formatScore(over.score)} Punkte", fontSize = 28.sp, fontWeight = FontWeight.Bold)
                over.note?.let { Text(it) }
                Text("Bestenliste", style = MaterialTheme.typography.titleSmall)
                HighScoreTable(entries, highlightAt = if (over.rank != null) over.at else null)
            }
        },
        confirmButton = { TextButton(onClick = onNewGame) { Text("Neues Spiel") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Schliessen") } },
    )
}

/**
 * Gerüst für ein Spiel: Titelleiste mit Zurück, eigenen [actions], Bestenliste und "Neu" (fragt nach, wenn ein Spiel läuft).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameScaffold(
    game: Game,
    entries: List<ScoreEntry>,
    inProgress: Boolean,
    onBack: () -> Unit,
    onNewGame: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    GameScaffold(game.title, entries, inProgress, onBack, onNewGame, actions, content = content)
}

/** Wie oben, aber mit freiem Titel (z. B. für die Würfelspiele). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameScaffold(
    title: String,
    entries: List<ScoreEntry>,
    inProgress: Boolean,
    onBack: () -> Unit,
    onNewGame: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
    scoreLabel: (Int) -> String = ::formatScore,
    content: @Composable (PaddingValues) -> Unit,
) {
    var confirmNew by rememberSaveable { mutableStateOf(false) }
    var showScores by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = { IconButton(onClick = onBack) { Text("←", fontSize = 22.sp) } },
                actions = {
                    actions()
                    IconButton(onClick = { showScores = true }) { Text("🏆", fontSize = 20.sp) }
                    TextButton(onClick = { if (inProgress) confirmNew = true else onNewGame() }) { Text("Neu") }
                },
            )
        },
        content = content,
    )

    if (confirmNew) {
        AlertDialog(
            onDismissRequest = { confirmNew = false },
            title = { Text("Neues Spiel?") },
            text = { Text("Das laufende Spiel wird abgebrochen und nicht gewertet.") },
            confirmButton = {
                TextButton(onClick = { confirmNew = false; onNewGame() }) { Text("Neues Spiel") }
            },
            dismissButton = { TextButton(onClick = { confirmNew = false }) { Text("Weiterspielen") } },
        )
    }
    if (showScores) {
        AlertDialog(
            onDismissRequest = { showScores = false },
            title = { Text("Bestenliste $title") },
            text = { HighScoreTable(entries, scoreLabel = scoreLabel) },
            confirmButton = { TextButton(onClick = { showScores = false }) { Text("OK") } },
        )
    }
}
