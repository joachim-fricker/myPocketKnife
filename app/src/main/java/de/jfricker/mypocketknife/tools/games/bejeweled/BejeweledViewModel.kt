package de.jfricker.mypocketknife.tools.games.bejeweled

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.jfricker.mypocketknife.tools.games.BoardView
import de.jfricker.mypocketknife.tools.games.From
import de.jfricker.mypocketknife.tools.games.Game
import de.jfricker.mypocketknife.tools.games.GameOver
import de.jfricker.mypocketknife.tools.games.Grid
import de.jfricker.mypocketknife.tools.games.HighScoreStore
import de.jfricker.mypocketknife.tools.games.ScoreEntry
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.random.Random

data class BejeweledUiState(
    val board: BoardView,
    val selected: Int? = null,
    /** Steine, die gerade als Treffer aufleuchten. */
    val matched: Set<Int> = emptySet(),
    val hint: Pair<Int, Int>? = null,
    val score: Int = 0,
    /** Aktuelle Kettenstufe (ab 2 wird "Kombo" angezeigt). */
    val chain: Int = 0,
    /** Punkte des letzten Zugs (alle Ketten zusammen). */
    val lastGain: Int = 0,
    val moves: Int = 0,
    val gameOver: GameOver? = null,
    val showGameOver: Boolean = false,
) {
    val inProgress get() = gameOver == null && score > 0
}

class BejeweledViewModel(app: Application) : AndroidViewModel(app) {

    private val store = HighScoreStore.get(app)
    private val random = Random.Default
    private var ids = BejeweledLogic.Ids()
    private val _state = MutableStateFlow(BejeweledUiState(BoardView(BejeweledLogic.newGrid(random, ids))))
    val state: StateFlow<BejeweledUiState> = _state.asStateFlow()
    val highScores: StateFlow<Map<String, List<ScoreEntry>>> = store.scores
    private var job: Job? = null

    init {
        viewModelScope.launch { store.load() }
    }

    private val busy get() = job?.isActive == true || _state.value.gameOver != null

    fun tap(index: Int) {
        if (busy) return
        val sel = _state.value.selected
        when {
            sel == null -> _state.update { it.copy(selected = index) }
            sel == index -> _state.update { it.copy(selected = null) }
            _state.value.board.grid.isAdjacent(sel, index) -> trySwap(sel, index)
            else -> _state.update { it.copy(selected = index) }
        }
    }

    fun swipe(from: Int, to: Int) {
        if (busy) return
        trySwap(from, to)
    }

    fun showHint() {
        if (busy) return
        _state.update { it.copy(hint = BejeweledLogic.findHint(it.board.grid), selected = null) }
    }

    fun dismissGameOver() = _state.update { it.copy(showGameOver = false) }

    fun newGame() {
        job?.cancel()
        ids = BejeweledLogic.Ids()
        val token = _state.value.board.token + 1
        _state.value = BejeweledUiState(BoardView(BejeweledLogic.newGrid(random, ids), token = token))
    }

    private fun trySwap(a: Int, b: Int) {
        job = viewModelScope.launch {
            val start = _state.value.board.grid
            val swapped = BejeweledLogic.swapped(start, a, b)
            _state.update { it.copy(selected = null, hint = null, lastGain = 0, chain = 0) }
            show(swapped, swapMoves(start, a, b), SWAP_MS)

            var matches = BejeweledLogic.findMatches(swapped)
            if (matches.isEmpty()) {
                // ungültiger Zug: zurücktauschen
                show(start, swapMoves(swapped, a, b), SWAP_MS)
                return@launch
            }

            _state.update { it.copy(moves = it.moves + 1) }
            var grid = swapped
            var chain = 1
            var gain = 0
            while (matches.isNotEmpty()) {
                val points = matches.size * BejeweledLogic.POINTS_PER_GEM * chain
                gain += points
                _state.update { it.copy(matched = matches, chain = chain, score = it.score + points, lastGain = gain) }
                delay(FLASH_MS)
                val (next, moves) = BejeweledLogic.collapse(grid, matches, random, ids)
                _state.update { it.copy(matched = emptySet()) }
                show(next, moves, FALL_MS)
                grid = next
                matches = BejeweledLogic.findMatches(grid)
                chain++
            }

            if (BejeweledLogic.findHint(grid) == null) {
                val s = _state.value
                val at = System.currentTimeMillis()
                val rank = store.add(Game.BEJEWELED, s.score, at)
                val note = "Kein Zug mehr möglich · ${s.moves} Züge"
                _state.update { it.copy(gameOver = GameOver(it.score, rank, at, note), showGameOver = true) }
            }
        }
    }

    /** Zeigt [grid] an, animiert die Bewegungen und wartet, bis sie fertig sind. */
    private suspend fun show(grid: Grid, moves: Map<Int, From>, millis: Int) {
        _state.update { it.copy(board = BoardView(grid, moves, it.board.token + 1, millis)) }
        delay(millis.toLong())
    }

    /** Herkunft der beiden getauschten Steine; [before] = Feld vor dem Tausch. */
    private fun swapMoves(before: Grid, a: Int, b: Int): Map<Int, From> {
        val pa = before[a] ?: return emptyMap()
        val pb = before[b] ?: return emptyMap()
        return mapOf(
            pa.id to From(before.rowOf(a).toFloat(), before.colOf(a).toFloat()),
            pb.id to From(before.rowOf(b).toFloat(), before.colOf(b).toFloat()),
        )
    }

    private companion object {
        const val SWAP_MS = 180
        const val FLASH_MS = 260L
        const val FALL_MS = 300
    }
}
