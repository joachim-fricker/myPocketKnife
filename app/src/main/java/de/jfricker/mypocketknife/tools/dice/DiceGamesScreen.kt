package de.jfricker.mypocketknife.tools.dice

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import de.jfricker.mypocketknife.tools.dice.maedn.MaednScreen
import de.jfricker.mypocketknife.tools.dice.tenk.TENK_KEY
import de.jfricker.mypocketknife.tools.dice.tenk.TENK_RECORD_KEY
import de.jfricker.mypocketknife.tools.dice.tenk.roundsLabel
import de.jfricker.mypocketknife.tools.dice.tenk.TenkScreen
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.jfricker.mypocketknife.tools.games.HighScoreStore
import de.jfricker.mypocketknife.tools.games.formatScore

/** Kachel "Würfelspiele": Auswahl als Kacheln, darin die einzelnen Spiele. */
@Composable
fun DiceGamesScreen(onBack: () -> Unit) {
    // geöffnetes Spiel: RuleSet.key oder MAEDN
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    BackHandler(enabled = open != null) { open = null }

    val rules = RuleSet.entries.firstOrNull { it.key == open }
    when {
        open == null -> DiceOverview(onBack = onBack, onOpen = { open = it })
        open == MAEDN -> MaednScreen(onBack = { open = null })
        open == TENK_KEY -> TenkScreen(onBack = { open = null })
        rules != null -> YahtzeeScreen(rules = rules, onBack = { open = null })
        else -> {}
    }
}

private const val MAEDN = "maedn"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DiceOverview(onBack: () -> Unit, onOpen: (String) -> Unit) {
    val store = HighScoreStore.get(LocalContext.current)
    LaunchedEffect(store) { store.load() }
    val scores by store.scores.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Würfelspiele") },
                navigationIcon = { IconButton(onClick = onBack) { Text("←", fontSize = 22.sp) } },
            )
        }
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 150.dp),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            items(RuleSet.entries) { rules ->
                Tile(
                    icon = "🎲",
                    title = rules.title,
                    subtitle = rules.subtitle,
                    footer = scores[rules.key]?.firstOrNull()?.let { best ->
                        "🏆 ${formatScore(best.score)}" + (best.name?.let { " · $it" } ?: "")
                    } ?: "noch kein Rekord",
                    onClick = { onOpen(rules.key) },
                )
            }
            item {
                Tile(
                    icon = "🎲",
                    title = "10000",
                    subtitle = "Zehntausend, 1–4 Spieler",
                    footer = scores[TENK_RECORD_KEY]?.firstOrNull()?.let { best ->
                        "🏆 ${roundsLabel(best.score)}" + (best.name?.let { " · $it" } ?: "")
                    } ?: "noch kein Rekord",
                    onClick = { onOpen(TENK_KEY) },
                )
            }
            item {
                Tile(
                    icon = "🎯",
                    title = "Mensch ärgere dich nicht",
                    subtitle = "4 Farben, 1–3 Computer",
                    footer = null,
                    onClick = { onOpen(MAEDN) },
                )
            }
        }
    }
}

@Composable
private fun Tile(icon: String, title: String, subtitle: String, footer: String?, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
        ) {
            Text(icon, fontSize = 44.sp)
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp),
            )
            Text(subtitle, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
            if (footer != null) {
                Text(
                    footer,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}
