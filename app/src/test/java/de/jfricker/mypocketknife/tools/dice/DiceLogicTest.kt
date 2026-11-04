package de.jfricker.mypocketknife.tools.dice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class DiceLogicTest {

    private fun RuleSet.s(id: String, vararg dice: Int, joker: Boolean = false) =
        score(categories.first { it.id == id }, dice.toList(), joker)

    @Test
    fun kniffelScoring() {
        val k = RuleSet.KNIFFEL
        assertEquals(15, k.s("u5", 5, 5, 5, 1, 2))
        assertEquals(18, k.s("three", 5, 5, 5, 1, 2))
        assertEquals(0, k.s("four", 5, 5, 5, 1, 2))
        assertEquals(21, k.s("four", 4, 4, 4, 4, 5))
        assertEquals(25, k.s("full", 2, 2, 3, 3, 3))
        assertEquals(0, k.s("full", 3, 3, 3, 3, 3))
        assertEquals(25, k.s("full", 3, 3, 3, 3, 3, joker = true))
        assertEquals(30, k.s("small", 1, 2, 3, 4, 6))
        assertEquals(30, k.s("small", 3, 4, 5, 6, 6))
        assertEquals(0, k.s("small", 1, 2, 3, 5, 6))
        assertEquals(40, k.s("large", 2, 3, 4, 5, 6))
        assertEquals(0, k.s("large", 1, 2, 3, 4, 6))
        assertEquals(50, k.s("yahtzee", 6, 6, 6, 6, 6))
        assertEquals(17, k.s("chance", 1, 2, 3, 5, 6))
    }

    @Test
    fun yatzyScoring() {
        val y = RuleSet.YATZY
        assertEquals(12, y.s("pair", 6, 6, 5, 5, 1))
        assertEquals(22, y.s("twopairs", 6, 6, 5, 5, 1))
        assertEquals(0, y.s("twopairs", 4, 4, 4, 4, 1)) // Vierling ist kein Doppelpaar
        assertEquals(15, y.s("three", 5, 5, 5, 1, 2))
        assertEquals(16, y.s("four", 4, 4, 4, 4, 5))
        assertEquals(15, y.s("small", 1, 2, 3, 4, 5))
        assertEquals(0, y.s("small", 2, 3, 4, 5, 6))
        assertEquals(20, y.s("large", 2, 3, 4, 5, 6))
        assertEquals(13, y.s("full", 2, 2, 3, 3, 3))
        assertEquals(0, y.s("full", 3, 3, 3, 3, 3))
        assertEquals(50, y.s("yahtzee", 2, 2, 2, 2, 2))
        assertEquals(15, RuleSet.YATZY.categories.size)
        assertEquals(13, RuleSet.KNIFFEL.categories.size)
    }

    @Test
    fun turnsRotateAndGameEnds() {
        var s = DiceLogic.newGame(RuleSet.KNIFFEL, listOf("Anna", "Ben"))
        assertEquals(s, DiceLogic.score(s, "chance")) // vor dem ersten Wurf nicht möglich
        val rnd = Random(1)
        var rounds = 0
        while (!s.finished) {
            s = DiceLogic.roll(s, rnd)
            val free = DiceLogic.options(s).keys.first()
            val who = s.current
            s = DiceLogic.score(s, free, now = 42L)
            if (!s.finished) assertEquals((who + 1) % 2, s.current)
            rounds++
        }
        assertEquals(26, rounds)
        assertEquals(42L, s.finishedAt)
        assertTrue(s.players.all { it.scores.size == 13 })
    }

    @Test
    fun rollsAndHolding() {
        var s = DiceLogic.newGame(RuleSet.YATZY, listOf("Jeff"))
        assertFalse(DiceLogic.toggleHold(s, 0).held[0]) // vor dem Wurf nicht halten
        s = DiceLogic.roll(s, Random(2))
        s = DiceLogic.toggleHold(s, 0)
        val kept = s.dice[0]
        repeat(5) { s = DiceLogic.roll(s, Random(it)) }
        assertEquals(0, s.rollsLeft)
        assertEquals(kept, s.dice[0])
    }

    @Test
    fun bonusAndExtraKniffel() {
        val k = RuleSet.KNIFFEL
        val p = DicePlayer("A", mapOf("u1" to 3, "u2" to 6, "u3" to 9, "u4" to 12, "u5" to 15, "u6" to 18, "yahtzee" to 50))
        val t = DiceLogic.totals(k, p)
        assertEquals(63, t.upper)
        assertEquals(35, t.bonus)
        assertEquals(0, DiceLogic.missingForBonus(k, p))

        val s = DiceState(k, listOf(p), dice = listOf(4, 4, 4, 4, 4), rollsLeft = 2)
        val after = DiceLogic.score(s, "full")
        assertEquals(25, after.players[0].scores["full"]) // Joker
        assertEquals(100, after.players[0].extraBonus)
    }
}
