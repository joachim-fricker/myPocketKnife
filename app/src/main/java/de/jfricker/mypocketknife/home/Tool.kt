package de.jfricker.mypocketknife.home

/** Alle Werkzeuge der App. available = false zeigt die Kachel als "Bald" an. */
enum class Tool(
    val title: String,
    val subtitle: String,
    val icon: String,
    val available: Boolean,
) {
    INCLINE("Neigungsmesser", "Hangsteilheit messen", "📐", true),
    CURRENCY("Währungsrechner", "Tageskurse, ~170 Währungen", "💱", true),
    MOON("Mondphase", "Voll- und Neumond", "🌙", true),
    SUN("Sonne", "Goldene & blaue Stunde", "🌅", true),
    JOKE("Witz des Tages", "Bewerten und sammeln", "😄", true),
}
