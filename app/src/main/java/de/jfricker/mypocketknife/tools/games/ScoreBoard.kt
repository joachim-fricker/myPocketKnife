package de.jfricker.mypocketknife.tools.games

import kotlinx.serialization.Serializable

/** Die Spiele in der Spiele-Kachel. key = Schlüssel in der Bestenlisten-Datei (nicht ändern). */
enum class Game(val key: String, val title: String, val subtitle: String, val icon: String) {
    SAME_GAME("samegame", "SameGame", "Gleiche Steine abräumen", "🔴"),
    BEJEWELED("bejeweled", "Bejeweled", "Drei in einer Reihe", "💎"),
    INVADERS("invaders", "Invaders", "Angriff aus dem All abwehren", "👾"),
    SNAKE("snake", "Snake", "Fressen, wachsen, nicht anstossen", "🐍"),
    ASTEROIDS("asteroids", "Asteroids", "Felsen zerschiessen", "☄️"),
}

/** name = Spielername (bei Mehrspieler-Spielen), sonst null. */
@Serializable
data class ScoreEntry(val score: Int, val at: Long, val name: String? = null)

object ScoreBoard {
    const val MAX_ENTRIES = 5

    /**
     * Fügt [entry] ein und behält die besten [MAX_ENTRIES].
     * Bei gleicher Punktzahl bleibt das ältere Ergebnis vorne.
     * [lowerIsBetter] = kleinere Zahl ist besser (z. B. Runden bei 10000).
     * Liefert die neue Liste und den Platz (1…5) oder null, wenn es nicht für die Liste reicht.
     */
    fun insert(list: List<ScoreEntry>, entry: ScoreEntry, lowerIsBetter: Boolean = false): Pair<List<ScoreEntry>, Int?> {
        if (entry.score <= 0) return list to null
        val pos = list.indexOfFirst { if (lowerIsBetter) it.score > entry.score else it.score < entry.score }
            .let { if (it < 0) list.size else it }
        if (pos >= MAX_ENTRIES) return list to null
        val updated = (list.take(pos) + entry + list.drop(pos)).take(MAX_ENTRIES)
        return updated to pos + 1
    }
}
