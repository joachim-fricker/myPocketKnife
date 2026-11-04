package de.jfricker.mypocketknife.tools.games

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
import de.jfricker.mypocketknife.tools.games.asteroids.AsteroidsScreen
import de.jfricker.mypocketknife.tools.games.bejeweled.BejeweledScreen
import de.jfricker.mypocketknife.tools.games.invaders.InvadersScreen
import de.jfricker.mypocketknife.tools.games.samegame.SameGameScreen
import de.jfricker.mypocketknife.tools.games.snake.SnakeScreen

/** Spiele-Kachel: Spieleauswahl als Kacheln (wie der Startbildschirm), von hier aus wird ein Spiel geöffnet. */
@Composable
fun GamesScreen(onBack: () -> Unit) {
    var open by rememberSaveable { mutableStateOf<Game?>(null) }
    BackHandler(enabled = open != null) { open = null }

    when (open) {
        null -> GamesOverview(onBack = onBack, onOpen = { open = it })
        Game.SAME_GAME -> SameGameScreen(onBack = { open = null })
        Game.BEJEWELED -> BejeweledScreen(onBack = { open = null })
        Game.INVADERS -> InvadersScreen(onBack = { open = null })
        Game.SNAKE -> SnakeScreen(onBack = { open = null })
        Game.ASTEROIDS -> AsteroidsScreen(onBack = { open = null })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GamesOverview(onBack: () -> Unit, onOpen: (Game) -> Unit) {
    val store = HighScoreStore.get(LocalContext.current)
    LaunchedEffect(store) { store.load() }
    val scores by store.scores.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Spiele") },
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
            items(Game.entries) { game ->
                GameTile(game, best = scores[game.key]?.firstOrNull()?.score, onClick = { onOpen(game) })
            }
        }
    }
}

/** Kachel wie auf dem Startbildschirm, zusätzlich mit dem Rekord (Top 5: 🏆 im Spiel). */
@Composable
private fun GameTile(game: Game, best: Int?, onClick: () -> Unit) {
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
            Text(game.icon, fontSize = 44.sp)
            Text(
                game.title,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp),
            )
            Text(game.subtitle, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
            Text(
                if (best != null) "🏆 ${formatScore(best)}" else "noch kein Rekord",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}
