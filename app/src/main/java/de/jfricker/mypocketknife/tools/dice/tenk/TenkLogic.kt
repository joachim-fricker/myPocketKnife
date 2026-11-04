package de.jfricker.mypocketknife.tools.dice.tenk

import kotlinx.serialization.Serializable
import kotlin.random.Random

@Serializable
/** turns = Anzahl eigener Züge (auch Nieten), für die Bestenliste "wenigste Runden bis 10000". */
data class TenkPlayer(val name: String, val total: Int = 0, val turns: Int = 0)

/** Spielstand 10000 (wird nach jeder Änderung gespeichert). */
@Serializable
data class TenkState(
    val players: List<TenkPlayer>,
    val current: Int = 0,
    val dice: List<Int> = List(TenkLogic.DICE) { it + 1 },
    /** In dieser Runde schon zur Seite gelegt. */
    val kept: List<Boolean> = List(TenkLogic.DICE) { false },
    /** Aus dem aktuellen Wurf ausgewählt. */
    val selected: List<Boolean> = List(TenkLogic.DICE) { false },
    /** Gesicherte Punkte dieser Runde (ohne aktuelle Auswahl). */
    val turnPoints: Int = 0,
    val rolled: Boolean = false,
    /** Niete: der Wurf enthält nichts Wertbares, die Runde ist verloren. */
    val bust: Boolean = false,
    /** Wer 10000 erreicht hat (löst die letzte Runde aus). */
    val finalRoundBy: Int? = null,
    val finished: Boolean = false,
    val finishedAt: Long = 0,
    val message: String? = null,
) {
    val player get() = players[current]
}

/**
 * 10000 (klassisch):
 * - 6 Würfel; nach jedem Wurf mindestens einen wertenden Würfel/eine Kombination zur Seite legen
 * - 1 = 100, 5 = 50; drei gleiche = Augen × 100 (drei Einsen = 1000), jeder weitere gleiche verdoppelt
 * - Strasse 1–6 = 1500, drei Paare = 1500 (nur mit allen 6 Würfeln eines Wurfs)
 * - Weiterwürfeln mit den übrigen Würfeln oder schreiben; Wurf ohne Wertung = Niete, Rundenpunkte weg
 * - Alle 6 Würfel gewertet: mit allen 6 weiterwürfeln (Anschluss)
 * - Schreiben erst ab 350 Punkten in einer Runde (gilt bei jedem Schreiben, nicht nur beim Einstieg)
 * - Wer 10000 erreicht, löst die letzte Runde aus; alle anderen haben noch einen Zug, höchste Punktzahl gewinnt
 */
object TenkLogic {
    const val DICE = 6
    const val GOAL = 10000
    /** Mindestpunkte einer Runde, um zu schreiben. */
    const val ENTRY = 350
    const val STRAIGHT = 1500
    const val THREE_PAIRS = 1500

    fun newGame(names: List<String>): TenkState {
        require(names.size in 1..4)
        return TenkState(names.map { TenkPlayer(it) })
    }

    /**
     * Punkte für genau diese Würfel (alle müssen zählen), sonst null.
     */
    fun score(values: List<Int>): Int? {
        if (values.isEmpty()) return null
        val counts = IntArray(7).also { c -> values.forEach { c[it]++ } }
        val options = mutableListOf<Int>()
        if (values.size == 6) {
            if ((1..6).all { counts[it] == 1 }) options += STRAIGHT
            if ((1..6).all { counts[it] % 2 == 0 }) options += THREE_PAIRS
        }
        var sum = 0
        var valid = true
        for (face in 1..6) {
            val n = counts[face]
            when {
                n >= 3 -> sum += (if (face == 1) 1000 else face * 100) shl (n - 3)
                n > 0 && face == 1 -> sum += 100 * n
                n > 0 && face == 5 -> sum += 50 * n
                n > 0 -> valid = false
            }
        }
        if (valid) options += sum
        return options.maxOrNull()
    }

    /** Enthält der Wurf irgendetwas Wertbares? */
    fun hasScoring(values: List<Int>): Boolean {
        val counts = IntArray(7).also { c -> values.forEach { c[it]++ } }
        return counts[1] > 0 || counts[5] > 0 || (2..6).any { counts[it] >= 3 } ||
            (values.size == 6 && (1..6).all { counts[it] % 2 == 0 })
    }

    fun selectedValues(s: TenkState) = s.dice.filterIndexed { i, _ -> s.selected[i] }

    /** Punkte der aktuellen Auswahl oder null (leer/ungültig). */
    fun selectionScore(s: TenkState): Int? = score(selectedValues(s))

    fun canRoll(s: TenkState) = !s.finished && !s.bust && (!s.rolled || selectionScore(s) != null)

    /** Punkte, die man jetzt schreiben würde, oder null, wenn Schreiben nicht erlaubt ist. */
    fun bankable(s: TenkState): Int? {
        if (s.finished || s.bust || !s.rolled) return null
        val sel = selectionScore(s) ?: return null
        val points = s.turnPoints + sel
        if (points < ENTRY) return null
        return points
    }

    /** Anzahl Würfel, mit denen als Nächstes gewürfelt würde. */
    fun diceForNextRoll(s: TenkState): Int {
        if (!s.rolled) return DICE
        val free = (0 until DICE).count { !s.kept[it] && !s.selected[it] }
        return if (free == 0) DICE else free
    }

    /**
     * Kann der Würfel [i] in diesem Wurf überhaupt zählen? (1, 5, Teil eines Paschs, einer Strasse oder von drei Paaren)
     */
    fun countable(s: TenkState, i: Int): Boolean {
        if (!s.rolled || s.kept[i]) return false
        val free = s.dice.filterIndexed { j, _ -> !s.kept[j] }
        val v = s.dice[i]
        if (v == 1 || v == 5) return true
        if (free.count { it == v } >= 3) return true
        return free.size == 6 && score(free) != null // Strasse oder drei Paare
    }

    fun toggle(s: TenkState, i: Int): TenkState {
        if (!s.rolled || s.bust || s.finished || s.kept[i]) return s
        return s.copy(selected = s.selected.toMutableList().also { it[i] = !it[i] })
    }

    fun roll(s: TenkState, random: Random = Random.Default): TenkState {
        if (!canRoll(s)) return s
        var kept = s.kept
        var turnPoints = s.turnPoints
        if (s.rolled) {
            turnPoints += selectionScore(s)!!
            kept = kept.mapIndexed { i, k -> k || s.selected[i] }
            if (kept.all { it }) kept = List(DICE) { false } // Anschluss: alle 6 nochmals
        } else {
            kept = List(DICE) { false }
        }
        val dice = s.dice.mapIndexed { i, d -> if (kept[i]) d else random.nextInt(1, 7) }
        val free = dice.filterIndexed { i, _ -> !kept[i] }
        return s.copy(
            dice = dice,
            kept = kept,
            selected = List(DICE) { false },
            turnPoints = turnPoints,
            rolled = true,
            bust = !hasScoring(free),
            message = null,
        )
    }

    /** Punkte schreiben und an den Nächsten weitergeben. */
    fun bank(s: TenkState, now: Long = System.currentTimeMillis()): TenkState {
        val points = bankable(s) ?: return s
        val p = s.player
        val total = p.total + points
        val players = s.players.toMutableList().also { it[s.current] = p.copy(total = total, turns = p.turns + 1) }
        return nextTurn(
            s.copy(players = players, message = "${p.name} schreibt $points → $total"),
            reachedGoal = total >= GOAL,
            now = now,
        )
    }

    /** Nach einer Niete weitergeben. */
    fun pass(s: TenkState, now: Long = System.currentTimeMillis()): TenkState {
        if (!s.bust) return s
        val players = s.players.toMutableList().also { it[s.current] = s.player.copy(turns = s.player.turns + 1) }
        return nextTurn(s.copy(players = players, message = "${s.player.name}: Niete"), reachedGoal = false, now = now)
    }

    private fun nextTurn(s: TenkState, reachedGoal: Boolean, now: Long): TenkState {
        val finalBy = s.finalRoundBy ?: if (reachedGoal) s.current else null
        val next = (s.current + 1) % s.players.size
        val done = finalBy != null && next == finalBy
        return s.copy(
            current = if (done) s.current else next,
            kept = List(DICE) { false },
            selected = List(DICE) { false },
            turnPoints = 0,
            rolled = false,
            bust = false,
            finalRoundBy = finalBy,
            finished = done,
            finishedAt = if (done) now else 0,
        )
    }
}
