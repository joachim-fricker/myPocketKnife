package de.jfricker.mypocketknife.tools.joke

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy")
private val STAR_COLOR = Color(0xFFF5B301)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JokeScreen(onBack: () -> Unit, vm: JokeViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Witz des Tages") },
                navigationIcon = { IconButton(onClick = onBack) { Text("←", fontSize = 22.sp) } },
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            PrimaryTabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Heute") })
                Tab(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    text = { Text("Meine Witze (${state.data.favorites.size})") },
                )
            }
            when (tab) {
                0 -> TodayTab(state, vm)
                else -> FavoritesTab(state, vm)
            }
        }
    }
}

@Composable
private fun TodayTab(state: JokeUiState, vm: JokeViewModel) {
    val context = LocalContext.current
    val joke = state.current

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Text(
            if (state.isDaily) "Witz des Tages · ${LocalDate.now().format(DATE)}"
            else "Zufallswitz" + (state.category?.let { " · $it" } ?: ""),
            style = MaterialTheme.typography.titleMedium,
        )

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Box(Modifier.padding(20.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                when {
                    state.loading && joke == null -> CircularProgressIndicator()
                    joke != null -> Text(joke.text, fontSize = 20.sp, lineHeight = 28.sp)
                    else -> Text("Noch kein Witz geladen.")
                }
            }
        }

        joke?.let { j ->
            Text("Quelle: ${j.source}", style = MaterialTheme.typography.labelSmall)
            val stars = state.starsOf(j.id)
            StarRating(stars = stars, onRate = { vm.rate(j.text, j.source, it) }, size = 36)
            Text(
                if (stars == 0) "Mit Sternen bewerten, um den Witz zu speichern."
                else "Gespeichert. Gleiche Sternzahl nochmals antippen zum Entfernen.",
                style = MaterialTheme.typography.bodySmall,
            )
        }

        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = vm::next, enabled = !state.loading) {
                if (state.loading && joke != null) {
                    CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                } else Text("Noch einer")
            }
            if (joke != null) OutlinedButton(onClick = { share(context, joke.text) }) { Text("Teilen") }
            if (!state.isDaily) TextButton(onClick = vm::showDaily) { Text("Witz des Tages") }
        }

        Text("Kategorie für „Noch einer“", style = MaterialTheme.typography.labelLarge)
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState()),
        ) {
            FilterChip(selected = state.category == null, onClick = { vm.setCategory(null) }, label = { Text("Alle") })
            JOKE_CATEGORIES.forEach { c ->
                FilterChip(selected = state.category == c, onClick = { vm.setCategory(c) }, label = { Text(c) })
            }
        }
    }
}

@Composable
private fun FavoritesTab(state: JokeUiState, vm: JokeViewModel) {
    val context = LocalContext.current
    val favorites = state.favorites

    if (favorites.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Text("Noch keine gespeicherten Witze.\nBewerte einen Witz mit Sternen, dann landet er hier.")
        }
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item {
            TextButton(onClick = vm::toggleSort) {
                Text(if (state.sortBest) "Sortierung: Beste zuerst ⇅" else "Sortierung: Neueste zuerst ⇅")
            }
        }
        items(favorites, key = { it.id }) { fav ->
            Card(Modifier.fillMaxWidth().animateItem()) {
                Column(Modifier.padding(16.dp)) {
                    Text(fav.text, style = MaterialTheme.typography.bodyLarge)
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        StarRating(fav.stars, onRate = { vm.rate(fav.text, fav.source, it) }, size = 24)
                        Text(
                            Instant.ofEpochMilli(fav.savedAt).atZone(ZoneId.systemDefault()).toLocalDate().format(DATE),
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.weight(1f).padding(start = 8.dp),
                        )
                        IconButton(onClick = { share(context, fav.text) }) { Text("↗", fontSize = 18.sp) }
                        IconButton(onClick = { vm.delete(fav.id) }) {
                            Text("✕", fontSize = 16.sp, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StarRating(stars: Int, onRate: (Int) -> Unit, size: Int) {
    Row {
        for (i in 1..5) {
            Text(
                if (i <= stars) "★" else "☆",
                fontSize = size.sp,
                color = if (i <= stars) STAR_COLOR else MaterialTheme.colorScheme.outline,
                modifier = Modifier
                    .padding(end = 4.dp)
                    .clickable { onRate(i) },
            )
        }
    }
}

private fun share(context: Context, text: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(send, "Witz teilen"))
}
