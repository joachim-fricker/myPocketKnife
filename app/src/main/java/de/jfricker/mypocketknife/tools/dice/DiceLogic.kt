package de.jfricker.mypocketknife.tools.dice

import kotlinx.serialization.Serializable
import kotlin.random.Random

@Serializable
data class DicePlayer(
    val name: String,
    /** Belegte Felder: Category.id → Punkte. */
    val scores: Map<String, Int> = emptyMap(),
    /** Kniffel-Zusatzbonus (je weiterer Kniffel +100). */
    val extraBonus: Int = 0,
)

/** Spielstand einer Partie (wird nach jeder Änderung gespeichert). */
@Serializable
data class DiceState(
    val rules: RuleSet,
    val players: List<DicePlayer>,
    val current: Int = 0,
    val dice: List<Int> = listOf(1, 2, 3, 4, 5),
    val held: List<Boolean> = List(5) { false },
    val rollsLeft: Int = 3,
    val finished: Boolean = false,
    /** Zeitpunkt des Spielendes (für die Bestenliste). */
    val finishedAt: Long = 0,
) {
    val player get() = players[current]
    val rolled get() = rollsLeft < 3
}

data class Totals(val upper: Int, val bonus: Int, val lower: Int, val extraBonus: Int) {
    val total get() = upper + bonus + lower + extraBonus
}

object DiceLogic {
    const val THRESHOLD = 63
    const val EXTRA_YAHTZEE = 100

    fun newGame(rules: RuleSet, names: List<String>): DiceState {
        require(names.size in 1..4)
        return DiceState(rules, names.map { DicePlayer(it) })
    }

    /** Würfelt alle nicht gehaltenen Würfel. */
    fun roll(s: DiceState, random: Random = Random.Default): DiceState {
        if (s.finished || s.rollsLeft == 0) return s
        val held = if (s.rolled) s.held else List(5) { false }
        val dice = s.dice.mapIndexed { i, d -> if (held[i]) d else random.nextInt(1, 7) }
        return s.copy(dice = dice, held = held, rollsLeft = s.rollsLeft - 1)
    }

    fun toggleHold(s: DiceState, i: Int): DiceState {
        if (!s.rolled || s.rollsLeft == 0 || s.finished) return s
        return s.copy(held = s.held.toMutableList().also { it[i] = !it[i] })
    }

    private fun isYahtzee(dice: List<Int>) = dice.all { it == dice[0] }

    /** Kniffel-Joker: 5 gleiche und das Kniffel-Feld ist schon belegt. */
    fun joker(s: DiceState, p: DicePlayer = s.player): Boolean =
        s.rules.yahtzeeBonusEnabled && isYahtzee(s.dice) && p.scores.containsKey("yahtzee")

    /** Mögliche Punkte für alle freien Felder des aktuellen Spielers. */
    fun options(s: DiceState): Map<String, Int> {
        if (!s.rolled || s.finished) return emptyMap()
        val p = s.player
        val joker = joker(s)
        return s.rules.categories.filter { it.id !in p.scores }.associate { it.id to s.rules.score(it, s.dice, joker) }
    }

    /** Trägt das Feld [catId] für den aktuellen Spieler ein und gibt an den nächsten weiter. */
    fun score(s: DiceState, catId: String, now: Long = System.currentTimeMillis()): DiceState {
        val p = s.player
        if (!s.rolled || s.finished || catId in p.scores) return s
        val cat = s.rules.categories.first { it.id == catId }
        val points = s.rules.score(cat, s.dice, joker(s))
        val extra = if (s.rules.yahtzeeBonusEnabled && isYahtzee(s.dice) && p.scores["yahtzee"] == 50) EXTRA_YAHTZEE else 0
        val updated = p.copy(scores = p.scores + (catId to points), extraBonus = p.extraBonus + extra)
        val players = s.players.toMutableList().also { it[s.current] = updated }
        val done = players.all { pl -> pl.scores.size == s.rules.categories.size }
        return s.copy(
            players = players,
            current = if (done) s.current else (s.current + 1) % players.size,
            held = List(5) { false },
            rollsLeft = 3,
            finished = done,
            finishedAt = if (done) now else 0,
        )
    }

    fun totals(rules: RuleSet, p: DicePlayer): Totals {
        val upper = rules.upperCategories.sumOf { p.scores[it.id] ?: 0 }
        val lower = rules.lowerCategories.sumOf { p.scores[it.id] ?: 0 }
        return Totals(upper, if (upper >= THRESHOLD) rules.upperBonus else 0, lower, p.extraBonus)
    }

    /** Wie viel oben noch zum Bonus fehlt (0 = erreicht). */
    fun missingForBonus(rules: RuleSet, p: DicePlayer) = (THRESHOLD - totals(rules, p).upper).coerceAtLeast(0)
}
