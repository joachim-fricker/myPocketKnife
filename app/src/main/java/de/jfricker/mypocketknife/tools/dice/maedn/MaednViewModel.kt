package de.jfricker.mypocketknife.tools.dice.maedn

import android.app.Application
import android.content.Context
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.random.Random

enum class Phase { ROLL, ROLLING, MOVE, MOVING, OVER }

data class MaednUi(
    val loaded: Boolean = false,
    val state: MaednState? = null,
    val phase: Phase = Phase.ROLL,
    val die: Int? = null,
    /** Anzeige während der Würfel-Animation. */
    val shownDie: Int? = null,
    val triesLeft: Int = 1,
    val movable: List<Int> = emptyList(),
    /** Figuren-Positionen während einer Zug-Animation (sonst state.pieces). */
    val shownPieces: List<List<Int>>? = null,
    val message: String? = null,
    val fast: Boolean = false,
    val showResult: Boolean = false,
    val setup: List<Seat> = defaultSeats(),
) {
    val pieces get() = shownPieces ?: state?.pieces
    val humanTurn get() = state?.let { !it.seats[it.current].computer } ?: false
    val inProgress get() = state != null && !state.over
}

fun defaultSeats() = listOf(
    Seat("Spieler 1", computer = false),
    Seat("Computer", computer = true),
    Seat("Computer", computer = true),
    Seat("Computer", computer = true),
)

/** Spielstand in `files/maedn.json`, Sitzordnung in den SharedPreferences "maedn". */
private class MaednStore(context: Context) {
    private val file = File(context.filesDir, "maedn.json")
    private val prefs = context.getSharedPreferences("maedn", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val lock = Mutex()

    suspend fun load(): MaednState? = withContext(Dispatchers.IO) {
        if (!file.exists()) null else runCatching { json.decodeFromString<MaednState>(file.readText()) }.getOrNull()
    }

    suspend fun save(s: MaednState?) = lock.withLock {
        withContext(Dispatchers.IO) {
            if (s == null) file.delete()
            else {
                val tmp = File(file.parentFile, "maedn.json.tmp")
                tmp.writeText(json.encodeToString(MaednState.serializer(), s))
                tmp.renameTo(file)
            }
        }
    }

    fun seats(): List<Seat> = defaultSeats().mapIndexed { i, d ->
        Seat(prefs.getString("name$i", null) ?: d.name, prefs.getBoolean("computer$i", d.computer))
    }

    fun saveSeats(seats: List<Seat>) = prefs.edit {
        seats.forEachIndexed { i, s ->
            putString("name$i", s.name)
            putBoolean("computer$i", s.computer)
        }
    }
}

class MaednViewModel(app: Application) : AndroidViewModel(app) {

    private val store = MaednStore(app)
    private val sound = CaptureSound()
    private val _ui = MutableStateFlow(MaednUi(setup = store.seats()))
    val ui: StateFlow<MaednUi> = _ui.asStateFlow()

    /** Computer spielt nur, solange der Bildschirm sichtbar ist. */
    private val active = MutableStateFlow(false)
    private var job: Job? = null

    init {
        viewModelScope.launch {
            val saved = store.load()
            _ui.update { it.copy(loaded = true, state = saved, phase = if (saved?.over == true) Phase.OVER else Phase.ROLL) }
            if (saved != null && !saved.over) beginTurn()
        }
    }

    fun setActive(on: Boolean) {
        active.value = on
    }

    // ------------------------------------------------------------------ Einrichten

    fun setComputer(i: Int, computer: Boolean) = _ui.update {
        it.copy(setup = it.setup.toMutableList().also { l ->
            l[i] = l[i].copy(computer = computer, name = if (computer) "Computer" else humanName(l[i].name, i))
        })
    }

    private fun humanName(old: String, i: Int) = if (old == "Computer") "Spieler ${i + 1}" else old

    fun setName(i: Int, name: String) = _ui.update {
        it.copy(setup = it.setup.toMutableList().also { l -> l[i] = l[i].copy(name = name.take(16)) })
    }

    val setupValid get() = _ui.value.setup.count { it.computer } in 1..3

    fun start() {
        if (!setupValid) return
        val seats = _ui.value.setup.mapIndexed { i, s ->
            if (s.computer) s.copy(name = "Computer") else s.copy(name = s.name.trim().ifEmpty { "Spieler ${i + 1}" })
        }
        store.saveSeats(seats)
        startWith(seats)
    }

    fun rematch() {
        val seats = _ui.value.state?.seats ?: return
        startWith(seats)
    }

    private fun startWith(seats: List<Seat>) {
        job?.cancel()
        _ui.update { it.copy(state = MaednState(seats), phase = Phase.ROLL, die = null, message = null, showResult = false, shownPieces = null) }
        beginTurn()
    }

    fun newGame() {
        job?.cancel()
        _ui.update { it.copy(state = null, showResult = false, shownPieces = null, message = null, die = null) }
        viewModelScope.launch { store.save(null) }
    }

    fun toggleFast() = _ui.update { it.copy(fast = !it.fast) }
    fun dismissResult() = _ui.update { it.copy(showResult = false) }

    // ------------------------------------------------------------------ Menschliche Eingaben

    fun roll() {
        val u = _ui.value
        if (u.phase != Phase.ROLL || !u.humanTurn) return
        doRoll()
    }

    fun pick(piece: Int) {
        val u = _ui.value
        if (u.phase != Phase.MOVE || !u.humanTurn || piece !in u.movable) return
        doMove(piece)
    }

    // ------------------------------------------------------------------ Ablauf

    /** Wartezeiten; Computer-Züge laufen gemächlicher, ⏩ halbiert sie. */
    private fun pace(ms: Long): Long {
        val s = _ui.value.state
        val computer = s != null && s.seats[s.current].computer
        val base = if (computer) ms * 2 else ms
        return if (_ui.value.fast) base / 2 else base
    }

    private fun seatLabel(s: MaednState, p: Int) =
        MaednBoard.NAMES[p] + if (s.seats[p].computer) " (Computer)" else " (${s.seats[p].name})"

    private fun beginTurn() {
        val s = _ui.value.state ?: return
        viewModelScope.launch { store.save(s) }
        val tries = if (MaednLogic.threeTries(s.pieces, s.current)) 3 else 1
        _ui.update { it.copy(phase = Phase.ROLL, triesLeft = tries, movable = emptyList()) }
        if (s.seats[s.current].computer) {
            job = viewModelScope.launch {
                active.first { it }
                delay(pace(550))
                doRollNow()
            }
        }
    }

    private fun doRoll() {
        job = viewModelScope.launch { doRollNow() }
    }

    private suspend fun doRollNow() {
        val s = _ui.value.state ?: return
        _ui.update { it.copy(phase = Phase.ROLLING, message = null) }
        repeat(7) {
            _ui.update { it.copy(shownDie = Random.nextInt(1, 7)) }
            delay(pace(60))
        }
        val die = Random.nextInt(1, 7)
        val moves = MaednLogic.legalMoves(s.pieces, s.current, die)
        _ui.update { it.copy(shownDie = null, die = die) }
        val computer = s.seats[s.current].computer

        if (moves.isEmpty()) {
            val tries = _ui.value.triesLeft - 1
            val again = die == 6 || tries > 0
            _ui.update {
                it.copy(
                    triesLeft = if (die == 6) it.triesLeft else tries,
                    message = when {
                        die == 6 -> "Kein Zug möglich – nochmals würfeln"
                        tries > 0 -> "Kein Zug möglich – noch $tries ${if (tries == 1) "Versuch" else "Versuche"}"
                        else -> "Kein Zug möglich"
                    },
                    phase = if (again) Phase.ROLL else Phase.ROLLING,
                )
            }
            if (again) {
                if (computer) {
                    active.first { it }
                    delay(pace(700))
                    doRollNow()
                }
            } else {
                delay(pace(900))
                nextPlayer()
            }
            return
        }

        _ui.update { it.copy(phase = Phase.MOVE, movable = moves) }
        when {
            computer -> {
                active.first { it }
                delay(pace(450))
                doMoveNow(MaednLogic.chooseMove(s.pieces, s.current, die))
            }
            moves.size == 1 -> {
                delay(350)
                doMoveNow(moves.first())
            }
        }
    }

    private fun doMove(piece: Int) {
        job = viewModelScope.launch { doMoveNow(piece) }
    }

    private suspend fun doMoveNow(piece: Int) {
        val s = _ui.value.state ?: return
        val die = _ui.value.die ?: return
        val player = s.current
        val res = MaednLogic.move(s.pieces, player, piece, die)
        _ui.update { it.copy(phase = Phase.MOVING, movable = emptyList()) }

        // Schritt für Schritt ziehen
        for (rel in res.path) {
            val shown = s.pieces.map { it.toMutableList() }.also { it[player][piece] = rel }
            _ui.update { it.copy(shownPieces = shown) }
            delay(pace(150))
        }

        var ranking = s.ranking
        if (MaednLogic.finished(res.pieces[player]) && player !in ranking) ranking = ranking + player
        val message = res.captured?.let { (p, _) -> "${MaednBoard.NAMES[player]} schlägt ${MaednBoard.NAMES[p]}!" }
            ?: if (player in ranking && player !in s.ranking) "${seatLabel(s, player)} ist fertig – Platz ${ranking.size}!" else null

        if (res.captured != null) sound.capture()
        var next = s.copy(pieces = res.pieces, ranking = ranking)
        next = checkOver(next)
        _ui.update { it.copy(state = next, shownPieces = null, message = message) }

        if (next.over) {
            store.save(next)
            _ui.update { it.copy(phase = Phase.OVER, showResult = true) }
            return
        }
        if (die == 6 && player !in ranking) {
            delay(pace(250))
            beginTurn() // nochmals würfeln
        } else {
            delay(pace(250))
            nextPlayer()
        }
    }

    /** Ende, wenn nur noch einer übrig ist oder alle Menschen fertig sind; Rest nach Fortschritt einreihen. */
    private fun checkOver(s: MaednState): MaednState {
        val open = (0 until MaednLogic.PLAYERS).filter { it !in s.ranking }
        val humansOpen = open.any { !s.seats[it].computer }
        if (open.size > 1 && humansOpen) return s
        val rest = open.sortedByDescending { p -> s.pieces[p].sumOf { if (it == MaednLogic.HOME) -10 else it } }
        return s.copy(ranking = s.ranking + rest, over = true)
    }

    override fun onCleared() = sound.release()

    private fun nextPlayer() {
        val s = _ui.value.state ?: return
        var p = (s.current + 1) % MaednLogic.PLAYERS
        while (p in s.ranking) p = (p + 1) % MaednLogic.PLAYERS
        _ui.update { it.copy(state = s.copy(current = p), die = null, message = null) }
        beginTurn()
    }
}
