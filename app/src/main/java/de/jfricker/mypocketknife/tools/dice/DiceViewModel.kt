package de.jfricker.mypocketknife.tools.dice

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
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

/** Ergebnis einer beendeten Partie: Spieler nach Punkten sortiert, mit Platz in der Bestenliste. */
data class DiceResult(val ranking: List<Placed>, val at: Long) {
    /** detail = Zusatzzeile unter dem Namen (z. B. "in 23 Runden"). */
    data class Placed(val name: String, val total: Int, val highScoreRank: Int?, val detail: String? = null)
}

data class DiceUi(
    val loaded: Boolean = false,
    val state: DiceState? = null,
    /** Während der Würfel-Animation angezeigte Augen. */
    val shownDice: List<Int>? = null,
    val canUndo: Boolean = false,
    val result: DiceResult? = null,
    val showResult: Boolean = false,
    val setupCount: Int = 2,
    val setupNames: List<String> = List(4) { "Spieler ${it + 1}" },
) {
    val rolling get() = shownDice != null
    val inProgress get() = state != null && !state.finished && state.players.any { it.scores.isNotEmpty() }
}

class DiceViewModel(app: Application, val rules: RuleSet) : AndroidViewModel(app) {

    private val store = DiceStore(app)
    private val highScores = HighScoreStore.get(app)
    val scores: StateFlow<Map<String, List<ScoreEntry>>> = highScores.scores

    private val _ui = MutableStateFlow(DiceUi(setupCount = store.playerCount, setupNames = store.names()))
    val ui: StateFlow<DiceUi> = _ui.asStateFlow()

    private var undoState: DiceState? = null
    private var rollJob: Job? = null

    init {
        viewModelScope.launch {
            highScores.load()
            val saved = store.load(rules)
            _ui.update { it.copy(loaded = true, state = saved) }
        }
    }

    // ------------------------------------------------------------------ Einrichten

    fun setCount(n: Int) = _ui.update { it.copy(setupCount = n.coerceIn(1, 4)) }

    fun setName(i: Int, name: String) =
        _ui.update { it.copy(setupNames = it.setupNames.toMutableList().also { l -> l[i] = name.take(16) }) }

    fun start() {
        val u = _ui.value
        val names = u.setupNames.take(u.setupCount).mapIndexed { i, n -> n.trim().ifEmpty { "Spieler ${i + 1}" } }
        store.playerCount = u.setupCount
        store.saveNames(u.setupNames.mapIndexed { i, n -> n.trim().ifEmpty { "Spieler ${i + 1}" } })
        set(DiceLogic.newGame(rules, names))
    }

    /** Gleiche Spieler nochmals. */
    fun rematch() {
        val names = _ui.value.state?.players?.map { it.name } ?: return
        _ui.update { it.copy(showResult = false, result = null) }
        set(DiceLogic.newGame(rules, names))
    }

    /** Zurück zur Spielerauswahl, laufende Partie wird verworfen. */
    fun newGame() {
        rollJob?.cancel()
        _ui.update { it.copy(showResult = false, result = null, shownDice = null) }
        set(null)
    }

    fun dismissResult() = _ui.update { it.copy(showResult = false) }

    // ------------------------------------------------------------------ Spielen

    fun roll() {
        val s = _ui.value.state ?: return
        if (_ui.value.rolling || s.finished || s.rollsLeft == 0) return
        val next = DiceLogic.roll(s)
        undoState = null
        rollJob = viewModelScope.launch {
            val heldNow = next.held
            repeat(7) {
                _ui.update { u -> u.copy(shownDice = next.dice.mapIndexed { i, d -> if (heldNow[i]) d else Random.nextInt(1, 7) }) }
                delay(55)
            }
            _ui.update { it.copy(shownDice = null, canUndo = false) }
            set(next)
        }
    }

    fun toggleHold(i: Int) {
        val s = _ui.value.state ?: return
        if (_ui.value.rolling) return
        set(DiceLogic.toggleHold(s, i))
    }

    fun pick(catId: String) {
        val s = _ui.value.state ?: return
        if (_ui.value.rolling) return
        val next = DiceLogic.score(s, catId)
        if (next == s) return
        undoState = s
        _ui.update { it.copy(canUndo = !next.finished) }
        set(next)
        if (next.finished) record(next)
    }

    /** Letzten Eintrag zurücknehmen (bis zum nächsten Wurf). */
    fun undo() {
        val prev = undoState ?: return
        undoState = null
        _ui.update { it.copy(canUndo = false) }
        set(prev)
    }

    private fun record(s: DiceState) {
        viewModelScope.launch {
            // Bester zuerst eintragen, damit die gemeldeten Plätze stimmen
            val placed = s.players
                .map { it to DiceLogic.totals(rules, it).total }
                .sortedByDescending { it.second }
                .map { (p, total) -> DiceResult.Placed(p.name, total, highScores.add(rules.key, total, s.finishedAt, p.name)) }
            _ui.update { it.copy(result = DiceResult(placed, s.finishedAt), showResult = true) }
        }
    }

    private fun set(s: DiceState?) {
        _ui.update { it.copy(state = s) }
        viewModelScope.launch { store.save(rules, s) }
    }

    companion object {
        fun factory(rules: RuleSet) = viewModelFactory {
            initializer { DiceViewModel(this[APPLICATION_KEY]!!, rules) }
        }
    }
}
