package de.jfricker.mypocketknife.tools.joke

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

data class JokeUiState(
    val current: Joke? = null,
    /** true = der angezeigte Witz ist der Witz des Tages */
    val isDaily: Boolean = true,
    val loading: Boolean = false,
    val error: String? = null,
    /** Kategorie für "Noch einer"; null = alle */
    val category: String? = null,
    val data: JokeData = JokeData(),
    val sortBest: Boolean = true,
) {
    fun starsOf(id: String): Int = data.favorites.firstOrNull { it.id == id }?.stars ?: 0

    val favorites: List<SavedJoke>
        get() = if (sortBest) data.favorites.sortedWith(compareByDescending<SavedJoke> { it.stars }.thenByDescending { it.savedAt })
        else data.favorites.sortedByDescending { it.savedAt }

    val daily: Joke?
        get() = data.dailyText?.let { Joke(it, data.dailySource ?: "") }
}

class JokeViewModel(app: Application) : AndroidViewModel(app) {

    private val store = JokeStore(app)
    private val client = JokeClient()

    private val _state = MutableStateFlow(JokeUiState(loading = true))
    val state: StateFlow<JokeUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val data = store.load()
            _state.update { it.copy(data = data, loading = false) }
            val today = LocalDate.now().toString()
            if (data.dailyDate == today && data.dailyText != null) {
                _state.update { it.copy(current = it.daily, isDaily = true) }
            } else {
                loadDaily()
            }
        }
    }

    /** Neuen Witz des Tages holen (einmal pro Tag). */
    private fun loadDaily() = fetch(category = null) { joke ->
        val data = _state.value.data.copy(
            dailyDate = LocalDate.now().toString(),
            dailyText = joke.text,
            dailySource = joke.source,
        )
        persist(data)
        _state.update { it.copy(current = joke, isDaily = true) }
    }

    fun next() = fetch(_state.value.category) { joke ->
        _state.update { it.copy(current = joke, isDaily = false) }
    }

    fun showDaily() {
        val daily = _state.value.daily
        if (daily == null) loadDaily() else _state.update { it.copy(current = daily, isDaily = true, error = null) }
    }

    fun setCategory(category: String?) = _state.update { it.copy(category = category) }

    fun toggleSort() = _state.update { it.copy(sortBest = !it.sortBest) }

    /**
     * Sterne vergeben = Witz speichern bzw. Bewertung ändern.
     * Nochmals auf dieselbe Sternzahl tippen = wieder entfernen.
     */
    fun rate(text: String, source: String, stars: Int) {
        val id = jokeId(text)
        val favs = _state.value.data.favorites
        val existing = favs.firstOrNull { it.id == id }
        val newFavs = when {
            existing != null && existing.stars == stars -> favs - existing
            existing != null -> favs.map { if (it.id == id) it.copy(stars = stars) else it }
            else -> favs + SavedJoke(id, text, source, stars, System.currentTimeMillis())
        }
        persist(_state.value.data.copy(favorites = newFavs))
    }

    fun delete(id: String) =
        persist(_state.value.data.copy(favorites = _state.value.data.favorites.filterNot { it.id == id }))

    // ---------------------------------------------------------------

    private fun fetch(category: String?, onJoke: (Joke) -> Unit) {
        if (_state.value.loading) return
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                val seen = _state.value.data.seen
                // Bis zu 4 Versuche, um schon gesehene Witze zu überspringen
                var joke = client.random(category)
                repeat(3) { if (joke.id in seen) joke = client.random(category) }
                persist(_state.value.data.copy(seen = (seen + joke.id).distinct().takeLast(500)))
                onJoke(joke)
                _state.update { it.copy(loading = false) }
            } catch (_: Exception) {
                _state.update {
                    it.copy(loading = false, error = "Kein Witz geladen – Internetverbindung prüfen.")
                }
            }
        }
    }

    private fun persist(data: JokeData) {
        _state.update { it.copy(data = data) }
        viewModelScope.launch { store.save(data) }
    }
}
