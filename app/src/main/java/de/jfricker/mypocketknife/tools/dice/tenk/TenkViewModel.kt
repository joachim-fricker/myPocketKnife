package de.jfricker.mypocketknife.tools.dice.tenk

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.jfricker.mypocketknife.tools.dice.DiceResult
import de.jfricker.mypocketknife.tools.dice.DiceStore
import de.jfricker.mypocketknife.tools.games.HighScoreStore
import de.jfricker.mypocketknife.tools.games.ScoreEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.random.Random

const val TENK_KEY = "zehntausend"

/** Bestenliste 10000: wenigste Runden bis 10000 (kleiner ist besser). */
const val TENK_RECORD_KEY = "zehntausend_runden"

fun roundsLabel(n: Int) = if (n == 1) "1 Runde" else "$n Runden"

data class TenkUi(
    val loaded: Boolean = false,
    val state: TenkState? = null,
    /** Augen während der Würfel-Animation. */
    val shownDice: List<Int>? = null,
    /** Kurzer Hinweis, z. B. wenn ein nicht zählender Würfel angetippt wurde. */
    val hint: String? = null,
    val result: DiceResult? = null,
    val showResult: Boolean = false,
    val setupCount: Int = 2,
    val setupNames: List<String> = List(4) { "Spieler ${it + 1}" },
) {
    val rolling get() = shownDice != null
    val inProgress get() = state != null && !state.finished && (state.rolled || state.players.any { it.total > 0 })
}

class TenkViewModel(app: Application) : AndroidViewModel(app) {

    private val names = DiceStore(app) // gleiche Spielernamen wie bei Kniffel/Yatzy
    private val highScores = HighScoreStore.get(app)
    val scores: StateFlow<Map<String, List<ScoreEntry>>> = highScores.scores

    private val file = File(app.filesDir, "dice_$TENK_KEY.json")
    private val json = Json { ignoreUnknownKeys = true }
    private val lock = Mutex()

    private val _ui = MutableStateFlow(TenkUi(setupCount = names.playerCount, setupNames = names.names()))
    val ui: StateFlow<TenkUi> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            highScores.load()
            val saved = withContext(Dispatchers.IO) {
                if (!file.exists()) null else runCatching { json.decodeFromString<TenkState>(file.readText()) }.getOrNull()
            }
            _ui.update { it.copy(loaded = true, state = saved) }
        }
    }

    // ------------------------------------------------------------------ Einrichten

    fun setCount(n: Int) = _ui.update { it.copy(setupCount = n.coerceIn(1, 4)) }

    fun setName(i: Int, name: String) =
        _ui.update { it.copy(setupNames = it.setupNames.toMutableList().also { l -> l[i] = name.take(16) }) }

    fun start() {
        val u = _ui.value
        val all = u.setupNames.mapIndexed { i, n -> n.trim().ifEmpty { "Spieler ${i + 1}" } }
        names.playerCount = u.setupCount
        names.saveNames(all)
        set(TenkLogic.newGame(all.take(u.setupCount)))
    }

    fun rematch() {
        val players = _ui.value.state?.players?.map { it.name } ?: return
        _ui.update { it.copy(result = null, showResult = false) }
        set(TenkLogic.newGame(players))
    }

    fun newGame() {
        _ui.update { it.copy(result = null, showResult = false, shownDice = null) }
        set(null)
    }

    fun dismissResult() = _ui.update { it.copy(showResult = false) }

    // ------------------------------------------------------------------ Spielen

    fun toggle(i: Int) {
        val s = _ui.value.state ?: return
        if (_ui.value.rolling) return
        // Würfel, die in diesem Wurf nicht zählen können, lassen sich gar nicht erst auswählen
        if (s.rolled && !s.bust && !s.kept[i] && !s.selected[i] && !TenkLogic.countable(s, i)) {
            _ui.update { it.copy(hint = "Eine ${s.dice[i]} zählt in diesem Wurf nicht.") }
            return
        }
        _ui.update { it.copy(hint = null) }
        set(TenkLogic.toggle(s, i))
    }

    fun roll() {
        val s = _ui.value.state ?: return
        if (_ui.value.rolling || !TenkLogic.canRoll(s)) return
        val next = TenkLogic.roll(s)
        _ui.update { it.copy(hint = null) }
        viewModelScope.launch {
            repeat(7) {
                _ui.update { u -> u.copy(shownDice = next.dice.mapIndexed { i, d -> if (next.kept[i]) d else Random.nextInt(1, 7) }) }
                delay(55)
            }
            _ui.update { it.copy(shownDice = null) }
            set(next)
        }
    }

    fun bank() {
        val s = _ui.value.state ?: return
        if (_ui.value.rolling) return
        finishIfDone(TenkLogic.bank(s))
    }

    fun pass() {
        val s = _ui.value.state ?: return
        finishIfDone(TenkLogic.pass(s))
    }

    private fun finishIfDone(next: TenkState) {
        _ui.update { it.copy(hint = null) }
        set(next)
        if (!next.finished) return
        viewModelScope.launch {
            // Bestenliste: wer 10000 erreicht hat, mit der Anzahl Runden; Schnellster zuerst eintragen
            val ranks = next.players.withIndex()
                .filter { it.value.total >= TenkLogic.GOAL }
                .sortedWith(compareBy({ it.value.turns }, { -it.value.total }))
                .associate { (i, p) ->
                    i to highScores.add(TENK_RECORD_KEY, p.turns, next.finishedAt, p.name, lowerIsBetter = true)
                }
            val placed = next.players.withIndex().sortedByDescending { it.value.total }.map { (i, p) ->
                DiceResult.Placed(p.name, p.total, ranks[i], detail = "${roundsLabel(p.turns)} gespielt")
            }
            _ui.update { it.copy(result = DiceResult(placed, next.finishedAt), showResult = true) }
        }
    }

    private fun set(s: TenkState?) {
        _ui.update { it.copy(state = s) }
        viewModelScope.launch {
            lock.withLock {
                withContext(Dispatchers.IO) {
                    if (s == null) file.delete()
                    else {
                        val tmp = File(file.parentFile, file.name + ".tmp")
                        tmp.writeText(json.encodeToString(TenkState.serializer(), s))
                        tmp.renameTo(file)
                    }
                }
            }
        }
    }
}
