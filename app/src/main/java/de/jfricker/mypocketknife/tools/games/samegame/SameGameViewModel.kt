package de.jfricker.mypocketknife.tools.games.samegame

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.jfricker.mypocketknife.tools.games.BoardView
import de.jfricker.mypocketknife.tools.games.Game
import de.jfricker.mypocketknife.tools.games.GameOver
import de.jfricker.mypocketknife.tools.games.HighScoreStore
import de.jfricker.mypocketknife.tools.games.ScoreEntry
import de.jfricker.mypocketknife.tools.games.formatScore
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SameGameUiState(
    val board: BoardView = BoardView(SameGameLogic.newGrid()),
    /** Markierte Gruppe (1. Tipp), 2. Tipp darauf entfernt sie. */
    val selection: Set<Int> = emptySet(),
    val score: Int = 0,
    val gameOver: GameOver? = null,
    val showGameOver: Boolean = false,
) {
    val selectionPoints get() = SameGameLogic.points(selection.size)
    val remaining get() = board.grid.pieceCount
    val inProgress get() = gameOver == null && score > 0
}

class SameGameViewModel(app: Application) : AndroidViewModel(app) {

    private val store = HighScoreStore.get(app)
    private val _state = MutableStateFlow(SameGameUiState())
    val state: StateFlow<SameGameUiState> = _state.asStateFlow()
    val highScores: StateFlow<Map<String, List<ScoreEntry>>> = store.scores
    private var animation: Job? = null

    init {
        viewModelScope.launch { store.load() }
    }

    fun dismissGameOver() = _state.update { it.copy(showGameOver = false) }

    fun tap(index: Int) {
        val s = _state.value
        if (s.gameOver != null || animation?.isActive == true) return
        val grid = s.board.grid
        if (index in s.selection && s.selection.size >= 2) {
            remove(s)
            return
        }
        val group = SameGameLogic.group(grid, index)
        _state.update { it.copy(selection = if (group.size >= 2) group else emptySet()) }
    }

    private fun remove(s: SameGameUiState) {
        val (grid, moves) = SameGameLogic.remove(s.board.grid, s.selection)
        val score = s.score + s.selectionPoints
        _state.value = s.copy(
            board = BoardView(grid, moves, s.board.token + 1, MOVE_MS),
            selection = emptySet(),
            score = score,
        )
        if (!SameGameLogic.hasMoves(grid)) {
            animation = viewModelScope.launch {
                delay(MOVE_MS.toLong() + 150)
                val cleared = grid.pieceCount == 0
                val total = score + if (cleared) SameGameLogic.CLEAR_BONUS else 0
                val at = System.currentTimeMillis()
                val rank = store.add(Game.SAME_GAME, total, at)
                val note = if (cleared) "Feld geleert! +${formatScore(SameGameLogic.CLEAR_BONUS)} Bonus"
                else "${grid.pieceCount} ${if (grid.pieceCount == 1) "Stein" else "Steine"} übrig"
                _state.update { it.copy(score = total, gameOver = GameOver(total, rank, at, note), showGameOver = true) }
            }
        }
    }

    fun newGame() {
        animation?.cancel()
        val token = _state.value.board.token + 1
        _state.value = SameGameUiState(board = BoardView(SameGameLogic.newGrid(), token = token))
    }

    private companion object {
        const val MOVE_MS = 260
    }
}
