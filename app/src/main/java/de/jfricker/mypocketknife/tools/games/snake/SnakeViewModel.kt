package de.jfricker.mypocketknife.tools.games.snake

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.jfricker.mypocketknife.tools.games.Game
import de.jfricker.mypocketknife.tools.games.GameOver
import de.jfricker.mypocketknife.tools.games.HighScoreStore
import de.jfricker.mypocketknife.tools.games.ScoreEntry
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class SnakeUiState(
    val game: SnakeGame = SnakeLogic.newGame(),
    val running: Boolean = false,
    val started: Boolean = false,
    val gameOver: GameOver? = null,
    val showGameOver: Boolean = false,
) {
    val inProgress get() = started && gameOver == null
}

class SnakeViewModel(app: Application) : AndroidViewModel(app) {

    private val store = HighScoreStore.get(app)
    private val _state = MutableStateFlow(SnakeUiState())
    val state: StateFlow<SnakeUiState> = _state.asStateFlow()
    val highScores: StateFlow<Map<String, List<ScoreEntry>>> = store.scores

    /** Bis zu 2 vorgemerkte Richtungswechsel, damit schnelle Wischer nicht verloren gehen. */
    private val turns = ArrayDeque<Dir>()
    private var loop: Job? = null

    init {
        viewModelScope.launch { store.load() }
    }

    fun turn(d: Dir) {
        if (!_state.value.running) return
        val last = turns.lastOrNull() ?: _state.value.game.dir
        if (d != last && d != last.opposite && turns.size < 2) turns.addLast(d)
    }

    fun start() {
        if (!_state.value.game.alive) {
            if (_state.value.gameOver == null) return // Ergebnis wird gerade eingetragen
            newGame()
        }
        _state.update { it.copy(running = true, started = true) }
        loop?.cancel()
        loop = viewModelScope.launch {
            while (isActive && _state.value.running) {
                delay(SnakeLogic.interval(_state.value.game.eaten))
                if (!_state.value.running) break
                val next = SnakeLogic.step(_state.value.game, turns.removeFirstOrNull())
                _state.update { it.copy(game = next) }
                if (!next.alive) {
                    _state.update { it.copy(running = false) }
                    viewModelScope.launch { finish(next) } // eigener Job, damit "Pause" das Eintragen nicht abbricht
                    break
                }
            }
        }
    }

    fun pause() {
        loop?.cancel()
        _state.update { it.copy(running = false) }
    }

    fun newGame() {
        loop?.cancel()
        turns.clear()
        _state.value = SnakeUiState()
    }

    fun dismissGameOver() = _state.update { it.copy(showGameOver = false) }

    private suspend fun finish(g: SnakeGame) {
        val at = System.currentTimeMillis()
        val rank = store.add(Game.SNAKE, g.score, at)
        val note = if (g.food == null) "Feld voll – gewonnen!" else "Länge ${g.body.size}"
        _state.update { it.copy(gameOver = GameOver(g.score, rank, at, note), showGameOver = true) }
    }
}
