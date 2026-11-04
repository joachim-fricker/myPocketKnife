package de.jfricker.mypocketknife.tools.dice

import de.jfricker.mypocketknife.tools.dice.tenk.TenkLogic
import de.jfricker.mypocketknife.tools.dice.tenk.TenkPlayer
import de.jfricker.mypocketknife.tools.dice.tenk.TenkState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class TenkLogicTest {

    private fun sc(vararg d: Int) = TenkLogic.score(d.toList())

    @Test
    fun scoring() {
        assertEquals(100, sc(1))
        assertEquals(50, sc(5))
        assertEquals(150, sc(1, 5))
        assertNull(sc(1, 2))
        assertNull(sc())
        assertEquals(1000, sc(1, 1, 1))
        assertEquals(2000, sc(1, 1, 1, 1))
        assertEquals(300, sc(3, 3, 3))
        assertEquals(600, sc(3, 3, 3, 3))
        assertEquals(1200, sc(3, 3, 3, 3, 3))
        assertEquals(2400, sc(3, 3, 3, 3, 3, 3))
        assertEquals(1000, sc(5, 5, 5, 5))
        assertEquals(1500, sc(1, 2, 3, 4, 5, 6))
        assertEquals(1500, sc(2, 2, 4, 4, 6, 6))
        assertEquals(1500, sc(2, 2, 2, 2, 6, 6)) // 4 + 2 gilt als drei Paare
        assertEquals(1050, sc(1, 1, 1, 5))
        assertNull(sc(2, 2, 3))
    }

    @Test
    fun countableDice() {
        // drei 5er zur Seite gelegt, frei: 4, 5, 1 → die 4 zählt nicht
        val s = TenkState(
            listOf(TenkPlayer("A")),
            dice = listOf(4, 5, 5, 5, 1, 5),
            kept = listOf(false, true, false, true, false, true),
            rolled = true,
        )
        assertFalse(TenkLogic.countable(s, 0))
        assertTrue(TenkLogic.countable(s, 2))
        assertTrue(TenkLogic.countable(s, 4))
        assertFalse(TenkLogic.countable(s, 1)) // schon zur Seite gelegt
        val pasch = TenkState(listOf(TenkPlayer("A")), dice = listOf(3, 3, 3, 2, 6, 4), rolled = true)
        assertTrue(TenkLogic.countable(pasch, 0))
        assertFalse(TenkLogic.countable(pasch, 3))
        val strasse = TenkState(listOf(TenkPlayer("A")), dice = listOf(6, 2, 4, 1, 3, 5), rolled = true)
        assertTrue(TenkLogic.countable(strasse, 0))
    }

    @Test
    fun scoringDetection() {
        assertTrue(TenkLogic.hasScoring(listOf(2, 3, 4, 6, 6, 1)))
        assertFalse(TenkLogic.hasScoring(listOf(2, 3, 4, 6, 6, 3)))
        assertTrue(TenkLogic.hasScoring(listOf(2, 2, 3, 3, 4, 4)))
        assertTrue(TenkLogic.hasScoring(listOf(4, 4, 4)))
        assertFalse(TenkLogic.hasScoring(listOf(2, 3)))
    }

    @Test
    fun writingAlwaysNeeds350() {
        var s = TenkState(listOf(TenkPlayer("A"), TenkPlayer("B")), dice = listOf(1, 5, 2, 3, 4, 6), rolled = true)
        s = TenkLogic.toggle(s, 0) // 100
        assertNull(TenkLogic.bankable(s))
        s = TenkLogic.toggle(s, 1) // 150
        assertNull(TenkLogic.bankable(s))
        // auch wer schon Punkte hat, braucht 350
        val entered = s.copy(players = listOf(TenkPlayer("A", 500), TenkPlayer("B")))
        assertNull(TenkLogic.bankable(entered))
        assertEquals(entered, TenkLogic.bank(entered))
        val enough = entered.copy(turnPoints = 200) // 200 + 150
        assertEquals(350, TenkLogic.bankable(enough))
        val banked = TenkLogic.bank(enough)
        assertEquals(850, banked.players[0].total)
        assertEquals(1, banked.players[0].turns)
        assertEquals(1, banked.current)
        assertFalse(banked.rolled)
    }

    @Test
    fun keptDiceStayAndHotDiceRollAllSix() {
        var s = TenkState(listOf(TenkPlayer("A", 1000)), dice = listOf(1, 1, 1, 5, 5, 5), rolled = true)
        s = (0 until 6).fold(s) { acc, i -> TenkLogic.toggle(acc, i) }
        assertEquals(1500, TenkLogic.selectionScore(s))
        assertEquals(6, TenkLogic.diceForNextRoll(s))
        val r = TenkLogic.roll(s, Random(1))
        assertEquals(1500, r.turnPoints)
        assertTrue(r.kept.none { it }) // Anschluss
    }

    @Test
    fun bustLosesTurnAndPasses() {
        val s = TenkState(listOf(TenkPlayer("A", 1000), TenkPlayer("B")), turnPoints = 800, rolled = true, bust = true)
        assertNull(TenkLogic.bankable(s))
        val p = TenkLogic.pass(s)
        assertEquals(1000, p.players[0].total)
        assertEquals(1, p.players[0].turns) // Niete zählt als Runde
        assertEquals(1, p.current)
        assertEquals(0, p.turnPoints)
    }

    @Test
    fun reachingGoalTriggersFinalRound() {
        var s = TenkState(
            listOf(TenkPlayer("A", 9900), TenkPlayer("B", 5000), TenkPlayer("C", 7000)),
            dice = listOf(1, 2, 3, 4, 6, 6), rolled = true,
        )
        s = TenkLogic.toggle(s, 0)
        s = TenkLogic.bank(s, now = 7L) // A: 10000
        assertEquals(0, s.finalRoundBy)
        assertFalse(s.finished)
        assertEquals(1, s.current)
        s = TenkLogic.pass(s.copy(rolled = true, bust = true)) // B
        assertEquals(2, s.current)
        s = TenkLogic.pass(s.copy(rolled = true, bust = true), now = 9L) // C → Ende
        assertTrue(s.finished)
        assertEquals(9L, s.finishedAt)
    }

    @Test
    fun randomGamesEnd() {
        val rnd = Random(5)
        repeat(30) {
            var s = TenkLogic.newGame(listOf("A", "B", "C"))
            var steps = 0
            while (!s.finished && steps < 200_000) {
                steps++
                s = when {
                    !s.rolled -> TenkLogic.roll(s, rnd)
                    s.bust -> TenkLogic.pass(s)
                    else -> {
                        // alle wertenden Einzelwürfel/Pasche wählen: probiere ganze freie Menge, sonst Einsen/Fünfen
                        val free = (0 until 6).filter { !s.kept[it] }
                        var t = s
                        val all = free.fold(t) { acc, i -> TenkLogic.toggle(acc, i) }
                        t = if (TenkLogic.selectionScore(all) != null) all else {
                            val ones = free.filter { s.dice[it] == 1 || s.dice[it] == 5 }
                            if (ones.isNotEmpty()) ones.fold(s) { acc, i -> TenkLogic.toggle(acc, i) }
                            else {
                                // Pasch suchen
                                val face = (2..6).first { f -> free.count { s.dice[it] == f } >= 3 }
                                free.filter { s.dice[it] == face }.fold(s) { acc, i -> TenkLogic.toggle(acc, i) }
                            }
                        }
                        if (TenkLogic.bankable(t) != null && (t.turnPoints >= 300 || TenkLogic.diceForNextRoll(t) <= 2)) TenkLogic.bank(t)
                        else TenkLogic.roll(t, rnd)
                    }
                }
            }
            assertTrue(s.finished)
            assertTrue(s.players.any { it.total >= TenkLogic.GOAL })
        }
    }
}
