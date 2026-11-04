package de.jfricker.mypocketknife.tools.dice

/** Art der Wertung eines Felds. */
enum class Kind { UPPER, PAIR, TWO_PAIRS, THREE_KIND, FOUR_KIND, FULL_HOUSE, SMALL_STRAIGHT, LARGE_STRAIGHT, YAHTZEE, CHANCE }

/** Ein Feld im Block. face = Augenzahl bei den oberen Feldern. */
data class Category(val id: String, val label: String, val kind: Kind, val face: Int = 0) {
    val upper get() = kind == Kind.UPPER
}

/**
 * Die beiden Regelwerke.
 * key = Schlüssel für Bestenliste und Spielstand-Datei (nicht ändern).
 */
enum class RuleSet(
    val key: String,
    val title: String,
    val subtitle: String,
    val upperBonus: Int,
    val categories: List<Category>,
    val rulesText: String,
) {
    KNIFFEL(
        key = "kniffel",
        title = "Kniffel",
        subtitle = "Yahtzee-Regeln",
        upperBonus = 35,
        categories = upper() + listOf(
            Category("three", "Dreierpasch", Kind.THREE_KIND),
            Category("four", "Viererpasch", Kind.FOUR_KIND),
            Category("full", "Full House", Kind.FULL_HOUSE),
            Category("small", "Kleine Strasse", Kind.SMALL_STRAIGHT),
            Category("large", "Grosse Strasse", Kind.LARGE_STRAIGHT),
            Category("yahtzee", "Kniffel", Kind.YAHTZEE),
            Category("chance", "Chance", Kind.CHANCE),
        ),
        rulesText = """
            Pro Zug bis zu 3 Würfe; Würfel antippen = behalten. Danach ein freies Feld wählen (0 Punkte ist erlaubt).
            Oben: Summe der jeweiligen Augenzahl. Bonus 35, wenn oben mindestens 63.
            Dreier-/Viererpasch: mind. 3/4 gleiche → Summe aller Würfel.
            Full House (3 + 2 gleiche): 25 · Kleine Strasse (4 in Folge): 30 · Grosse Strasse (5 in Folge): 40.
            Kniffel (5 gleiche): 50 · Chance: Summe aller Würfel.
            Weitere Kniffel: +100 Bonus, wenn das Kniffel-Feld mit 50 belegt ist. Ist das Kniffel-Feld schon belegt, zählt ein Kniffel als Joker für Full House und Strassen.
        """.trimIndent(),
    ),
    YATZY(
        key = "yatzy",
        title = "Yatzy",
        subtitle = "Skandinavische Regeln",
        upperBonus = 50,
        categories = upper() + listOf(
            Category("pair", "Ein Paar", Kind.PAIR),
            Category("twopairs", "Zwei Paare", Kind.TWO_PAIRS),
            Category("three", "Drilling", Kind.THREE_KIND),
            Category("four", "Vierling", Kind.FOUR_KIND),
            Category("small", "Kleine Strasse", Kind.SMALL_STRAIGHT),
            Category("large", "Grosse Strasse", Kind.LARGE_STRAIGHT),
            Category("full", "Full House", Kind.FULL_HOUSE),
            Category("chance", "Chance", Kind.CHANCE),
            Category("yahtzee", "Yatzy", Kind.YAHTZEE),
        ),
        rulesText = """
            Pro Zug bis zu 3 Würfe; Würfel antippen = behalten. Danach ein freies Feld wählen (0 Punkte ist erlaubt).
            Oben: Summe der jeweiligen Augenzahl. Bonus 50, wenn oben mindestens 63.
            Ein Paar / Zwei Paare (verschiedene) / Drilling / Vierling: Summe nur dieser Würfel (beim Paar das höchste).
            Kleine Strasse 1-2-3-4-5: 15 · Grosse Strasse 2-3-4-5-6: 20.
            Full House (3 + 2 verschiedene): Summe aller Würfel · Chance: Summe aller Würfel · Yatzy (5 gleiche): 50.
        """.trimIndent(),
    );

    val upperCategories get() = categories.filter { it.upper }
    val lowerCategories get() = categories.filter { !it.upper }
    val yahtzeeBonusEnabled get() = this == KNIFFEL

    /**
     * Punkte für [dice] im Feld [cat]. [joker] = Kniffel-Joker (5 gleiche, Kniffel-Feld schon belegt).
     */
    fun score(cat: Category, dice: List<Int>, joker: Boolean = false): Int {
        val counts = IntArray(7).also { c -> dice.forEach { c[it]++ } }
        val sum = dice.sum()
        val faces = dice.toSet()
        fun highestWith(n: Int) = (6 downTo 1).firstOrNull { counts[it] >= n }
        return when (cat.kind) {
            Kind.UPPER -> counts[cat.face] * cat.face
            Kind.CHANCE -> sum
            Kind.YAHTZEE -> if (counts.any { it == 5 }) 50 else 0
            Kind.PAIR -> highestWith(2)?.let { it * 2 } ?: 0
            Kind.TWO_PAIRS -> {
                val pairs = (6 downTo 1).filter { counts[it] >= 2 }
                if (pairs.size >= 2) pairs[0] * 2 + pairs[1] * 2 else 0
            }
            Kind.THREE_KIND -> when (this) {
                KNIFFEL -> if (highestWith(3) != null) sum else 0
                YATZY -> highestWith(3)?.let { it * 3 } ?: 0
            }
            Kind.FOUR_KIND -> when (this) {
                KNIFFEL -> if (highestWith(4) != null) sum else 0
                YATZY -> highestWith(4)?.let { it * 4 } ?: 0
            }
            Kind.FULL_HOUSE -> {
                val isFull = counts.count { it == 3 } == 1 && counts.count { it == 2 } == 1
                when (this) {
                    KNIFFEL -> if (isFull || joker) 25 else 0
                    YATZY -> if (isFull) sum else 0
                }
            }
            Kind.SMALL_STRAIGHT -> when (this) {
                KNIFFEL -> if (joker || listOf(1..4, 2..5, 3..6).any { r -> r.all { it in faces } }) 30 else 0
                YATZY -> if (faces == setOf(1, 2, 3, 4, 5)) 15 else 0
            }
            Kind.LARGE_STRAIGHT -> when (this) {
                KNIFFEL -> if (joker || faces == setOf(1, 2, 3, 4, 5) || faces == setOf(2, 3, 4, 5, 6)) 40 else 0
                YATZY -> if (faces == setOf(2, 3, 4, 5, 6)) 20 else 0
            }
        }
    }
}

private fun upper() = listOf("Einer", "Zweier", "Dreier", "Vierer", "Fünfer", "Sechser")
    .mapIndexed { i, label -> Category("u${i + 1}", label, Kind.UPPER, face = i + 1) }
