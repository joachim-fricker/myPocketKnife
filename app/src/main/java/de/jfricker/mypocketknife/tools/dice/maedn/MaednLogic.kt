package de.jfricker.mypocketknife.tools.dice.maedn

import kotlinx.serialization.Serializable
import kotlin.random.Random

/**
 * Mensch ärgere dich nicht – Regeln (ohne Schlagzwang):
 * - 40 Felder, Start von Spieler p auf Feld p·10; 4 Figuren im Haus, 4 Zielfelder
 * - Position einer Figur relativ zum eigenen Start: [HOME] = im Haus, 0…39 = Laufbahn, 40…43 = Ziel
 * - Mit einer 6 muss eine Figur raus (falls das Startfeld nicht von einer eigenen besetzt ist), nach einer 6 nochmals würfeln
 * - Solange Figuren im Haus sind, muss das Startfeld geräumt werden
 * - Kann man ohne 6 nicht ziehen (alles im Haus bzw. fertig im Ziel), hat man 3 Versuche
 * - Gegnerische Figur auf dem Zielfeld wird geschlagen (zurück ins Haus); eigene Figuren blockieren
 * - Im Ziel wird nicht übersprungen, genaue Augenzahl nötig
 */
object MaednLogic {
    const val HOME = -1
    const val TRACK = 40
    const val GOAL_END = 43
    const val PLAYERS = 4
    const val PIECES = 4

    fun startOf(player: Int) = player * 10

    /** Absolutes Laufbahn-Feld (0…39) oder null für Haus/Ziel. */
    fun absolute(player: Int, rel: Int): Int? = if (rel in 0 until TRACK) (startOf(player) + rel) % TRACK else null

    fun finished(pieces: List<Int>) = pieces.all { it >= TRACK }

    /** Zielposition der Figur bei Augenzahl [die] oder null, wenn der Zug nach den Grundregeln nicht geht. */
    fun target(all: List<List<Int>>, player: Int, piece: Int, die: Int): Int? {
        val own = all[player]
        val r = own[piece]
        if (r == HOME) {
            if (die != 6) return null
            return if (own.any { it == 0 }) null else 0
        }
        if (r >= TRACK && r == GOAL_END) return null
        val t = r + die
        if (t > GOAL_END) return null
        if (own.withIndex().any { (i, p) -> i != piece && p == t }) return null
        // im Ziel nicht überspringen
        for (k in maxOf(TRACK, r + 1) until t) if (own.contains(k)) return null
        return t
    }

    /** Erlaubte Figuren für diesen Wurf (inkl. Pflicht "rauskommen" und "Start räumen"). */
    fun legalMoves(all: List<List<Int>>, player: Int, die: Int): List<Int> {
        val own = all[player]
        val possible = (0 until PIECES).filter { target(all, player, it, die) != null }
        if (possible.isEmpty()) return possible
        val hasHome = own.contains(HOME)
        if (die == 6 && hasHome) {
            val out = possible.filter { own[it] == HOME }
            if (out.isNotEmpty()) return out
        }
        if (hasHome) {
            val onStart = possible.filter { own[it] == 0 }
            if (onStart.isNotEmpty()) return onStart
        }
        return possible
    }

    /** Hat der Spieler drei Versuche (kein Zug ohne 6 möglich)? */
    fun threeTries(all: List<List<Int>>, player: Int): Boolean =
        (1..5).none { d -> legalMoves(all, player, d).isNotEmpty() }

    data class MoveResult(val pieces: List<List<Int>>, val captured: Pair<Int, Int>?, val path: List<Int>)

    /** Führt den Zug aus. path = Zwischenpositionen (relativ) für die Animation. */
    fun move(all: List<List<Int>>, player: Int, piece: Int, die: Int): MoveResult {
        val from = all[player][piece]
        val to = target(all, player, piece, die) ?: error("ungültiger Zug")
        val path = if (from == HOME) listOf(0) else ((from + 1)..to).toList()
        val result = all.map { it.toMutableList() }
        result[player][piece] = to
        var captured: Pair<Int, Int>? = null
        val abs = absolute(player, to)
        if (abs != null) {
            for (p in 0 until PLAYERS) {
                if (p == player) continue
                for (i in 0 until PIECES) {
                    if (absolute(p, result[p][i]) == abs) {
                        result[p][i] = HOME
                        captured = p to i
                    }
                }
            }
        }
        return MoveResult(result, captured, path)
    }

    // ------------------------------------------------------------------ Computer

    /** Wie viele gegnerische Figuren könnten das Feld [abs] im nächsten Zug treffen? */
    private fun threats(all: List<List<Int>>, player: Int, abs: Int): Int {
        var n = 0
        for (p in 0 until PLAYERS) {
            if (p == player) continue
            for (r in all[p]) {
                val a = absolute(p, r)
                if (a != null) {
                    val dist = (abs - a + TRACK) % TRACK
                    // Gegner muss das Feld noch auf seiner Laufbahn erreichen (nicht schon Richtung Ziel vorbei)
                    if (dist in 1..6 && r + dist < TRACK) n++
                }
            }
            // Figuren im Haus kommen mit einer 6 auf ihr Startfeld
            if (abs == startOf(p) && all[p].contains(HOME)) n++
        }
        return n
    }

    /** Wählt den Zug des Computers (einfache Bewertung mit etwas Zufall). */
    fun chooseMove(all: List<List<Int>>, player: Int, die: Int, random: Random = Random.Default): Int {
        val moves = legalMoves(all, player, die)
        require(moves.isNotEmpty())
        return moves.maxBy { piece -> evaluate(all, player, piece, die) + random.nextDouble() * 5 }
    }

    fun evaluate(all: List<List<Int>>, player: Int, piece: Int, die: Int): Double {
        val from = all[player][piece]
        val res = move(all, player, piece, die)
        val to = res.pieces[player][piece]
        var score = 0.0
        if (res.captured != null) {
            val (cp, ci) = res.captured
            score += 500 + all[cp][ci] * 5
        }
        if (from == HOME) score += 400
        if (to >= TRACK && from < TRACK) score += 300 // ins Ziel
        if (to >= TRACK) score += (to - TRACK) * 20.0 // im Ziel weit nach hinten
        if (from == 0 && all[player].contains(HOME)) score += 150 // Start freimachen

        val fromAbs = absolute(player, from)
        val toAbs = absolute(player, to)
        if (fromAbs != null) score += threats(all, player, fromAbs) * 120.0 // Flucht
        if (toAbs != null) score -= threats(res.pieces, player, toAbs) * 140.0 // neue Gefahr
        score += to * 1.5 // Fortschritt
        return score
    }
}

@Serializable
data class Seat(val name: String, val computer: Boolean)

/** Gespeicherter Spielstand (immer am Anfang eines Wurfs). */
@Serializable
data class MaednState(
    val seats: List<Seat>,
    val pieces: List<List<Int>> = List(MaednLogic.PLAYERS) { List(MaednLogic.PIECES) { MaednLogic.HOME } },
    val current: Int = 0,
    /** Reihenfolge, in der Spieler fertig wurden. */
    val ranking: List<Int> = emptyList(),
    val over: Boolean = false,
)
