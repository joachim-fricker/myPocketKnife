package de.jfricker.mypocketknife.tools.dice

import de.jfricker.mypocketknife.tools.dice.maedn.BoardCell
import de.jfricker.mypocketknife.tools.dice.maedn.MaednBoard
import de.jfricker.mypocketknife.tools.dice.maedn.MaednLogic
import de.jfricker.mypocketknife.tools.dice.maedn.MaednLogic.HOME
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class MaednLogicTest {

    private val allHome = List(4) { List(4) { HOME } }

    private fun with(player: Int, vararg pos: Int): List<List<Int>> =
        allHome.toMutableList().also { it[player] = pos.toList() }

    @Test
    fun boardGeometry() {
        val t = MaednBoard.TRACK
        assertEquals(40, t.toSet().size)
        t.indices.forEach { i ->
            val a = t[i]
            val b = t[(i + 1) % t.size]
            assertEquals(1, kotlin.math.abs(a.col - b.col) + kotlin.math.abs(a.row - b.row))
        }
        assertEquals(BoardCell(0, 4), t[0])
        assertEquals(BoardCell(6, 0), t[10])
        // Ziel schliesst an das Feld vor dem eigenen Start an, Haus/Ziel/Bahn überschneiden sich nicht
        for (p in 0 until 4) {
            val before = t[(MaednLogic.startOf(p) + 39) % 40]
            val g = MaednBoard.GOAL[p][0]
            assertEquals(1, kotlin.math.abs(before.col - g.col) + kotlin.math.abs(before.row - g.row))
        }
        val all = t + MaednBoard.GOAL.flatten() + MaednBoard.HOME.flatten() + BoardCell(5, 5)
        assertEquals(all.size, all.toSet().size)
    }

    @Test
    fun onlySixBringsAPieceOut() {
        assertTrue(MaednLogic.legalMoves(allHome, 0, 5).isEmpty())
        assertEquals(listOf(0, 1, 2, 3), MaednLogic.legalMoves(allHome, 0, 6))
        assertTrue(MaednLogic.threeTries(allHome, 0))
    }

    @Test
    fun sixForcesLeavingHomeAndStartMustBeCleared() {
        val b = with(0, 5, HOME, HOME, HOME)
        assertEquals(listOf(1, 2, 3), MaednLogic.legalMoves(b, 0, 6)) // raus ist Pflicht
        val s = with(0, 0, 12, HOME, HOME)
        assertEquals(listOf(0), MaednLogic.legalMoves(s, 0, 3)) // Start räumen
        assertEquals(listOf(0), MaednLogic.legalMoves(s, 0, 6)) // Start besetzt → erst räumen
        assertFalse(MaednLogic.threeTries(s, 0))
    }

    @Test
    fun captureSendsOpponentHome() {
        // Spieler 1 steht auf absolutem Feld 4 (relativ 34), Spieler 0 auf 1 und würfelt 3
        val b = listOf(listOf(1, HOME, HOME, HOME), listOf(34, HOME, HOME, HOME), List(4) { HOME }, List(4) { HOME })
        val r = MaednLogic.move(b, 0, 0, 3)
        assertEquals(4, r.pieces[0][0])
        assertEquals(HOME, r.pieces[1][0])
        assertEquals(1 to 0, r.captured)
        assertEquals(listOf(2, 3, 4), r.path)
    }

    @Test
    fun ownPiecesBlock() {
        val b = with(0, 10, 13, HOME, HOME)
        assertNull(MaednLogic.target(b, 0, 0, 3))
        assertEquals(14, MaednLogic.target(b, 0, 0, 4)) // eigene Figur darf übersprungen werden
    }

    @Test
    fun noJumpingInsideTheGoal() {
        val g = with(0, 38, 41, HOME, HOME)
        assertNull(MaednLogic.target(g, 0, 0, 4)) // 38 → 42 würde 41 überspringen
        assertEquals(40, MaednLogic.target(g, 0, 0, 2))
        assertNull(MaednLogic.target(g, 0, 1, 3)) // 41 + 3 = 44 > 43
        assertEquals(43, MaednLogic.target(g, 0, 1, 2))
    }

    @Test
    fun threeTriesWhenRestIsSettledInGoal() {
        val g = with(0, 43, 42, HOME, HOME)
        assertTrue(MaednLogic.threeTries(g, 0))
        val h = with(0, 40, HOME, HOME, HOME)
        assertFalse(MaednLogic.threeTries(h, 0)) // die Figur im Ziel kann noch vorrücken
    }

    @Test
    fun computerPrefersCapture() {
        val b = listOf(listOf(1, 20, HOME, HOME), listOf(34, HOME, HOME, HOME), List(4) { HOME }, List(4) { HOME })
        assertEquals(0, MaednLogic.chooseMove(b, 0, 3, Random(1)))
    }

    @Test
    fun randomComputerGamesFinish() {
        val rnd = Random(3)
        repeat(20) {
            var b = allHome
            var player = 0
            var turns = 0
            val done = mutableSetOf<Int>()
            while (done.size < 3 && turns < 20_000) {
                turns++
                if (player in done) { player = (player + 1) % 4; continue }
                var tries = if (MaednLogic.threeTries(b, player)) 3 else 1
                var again: Boolean
                do {
                    val die = rnd.nextInt(1, 7)
                    val moves = MaednLogic.legalMoves(b, player, die)
                    if (moves.isNotEmpty()) {
                        b = MaednLogic.move(b, player, MaednLogic.chooseMove(b, player, die, rnd), die).pieces
                        tries = 0
                    } else tries--
                    again = die == 6 && !MaednLogic.finished(b[player])
                } while ((again || tries > 0) && !MaednLogic.finished(b[player]))
                if (MaednLogic.finished(b[player])) done += player
                // jede Figur genau einmal, keine zwei eigenen auf einem Feld
                b.forEach { own -> assertEquals(own.filter { it != HOME }.size, own.filter { it != HOME }.toSet().size) }
                player = (player + 1) % 4
            }
            assertEquals(3, done.size)
        }
    }
}
