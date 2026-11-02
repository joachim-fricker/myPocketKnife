package de.jfricker.mypocketknife.home

/** Alle Werkzeuge der App. available = false zeigt die Kachel als "Bald" an. */
enum class Tool(
    val title: String,
    val subtitle: String,
    val icon: String,
    val available: Boolean,
) {
    CURRENCY("Währungsrechner", "Tageskurse der EZB", "💱", true),
    MOON("Mondphase", "Voll- und Neumond", "🌙", false),
    JOKE("Witz des Tages", "Bewerten und sammeln", "😄", false),
}
