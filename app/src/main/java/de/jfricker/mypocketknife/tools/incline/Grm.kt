package de.jfricker.mypocketknife.tools.incline

import android.content.Context
import androidx.core.content.edit

/**
 * Grafische Reduktionsmethode (GRM), aus der Originalgrafik ausgemessen.
 *
 * X-Achse durchgehend: 0..1 = gering, 1..2 = mässig, 2..3 = erheblich, 3..4 = gross.
 * Die Farbgrenzen sind in der Grafik Geraden (Abweichung der Messung < 0,2°):
 *   grün → orange:  41,45° − 4,89° × x
 *   orange → rot:   48,28° − 4,89° × x
 * Die schwarze Linie (Einsteiger): gering 40°, mässig 35°, erheblich 30°, gross unter 25°.
 */
object Grm {
    const val SLOPE = 4.89
    const val GREEN_ORANGE_0 = 41.45
    const val ORANGE_RED_0 = 48.28

    /** Achsenbereich der Grafik */
    const val MIN_DEG = 25.0
    const val MAX_DEG = 45.0

    fun greenLimit(x: Double) = GREEN_ORANGE_0 - SLOPE * x
    fun redLimit(x: Double) = ORANGE_RED_0 - SLOPE * x

    /** Schwarze Einsteigerlinie (Treppe) */
    fun beginnerLimit(x: Double): Double = when {
        x < 1 -> 40.0
        x < 2 -> 35.0
        x < 3 -> 30.0
        else -> 25.0
    }

    fun zone(x: Double, angle: Double): Zone = when {
        angle >= redLimit(x) -> Zone.RED
        angle >= greenLimit(x) -> Zone.ORANGE
        else -> Zone.GREEN
    }

    /**
     * Position auf der X-Achse. Ausserhalb der Kernzone (kritische Expositionen/Höhenlagen
     * laut Bulletin) darf eine Stufe tiefer angenommen werden – minimal "gering".
     */
    fun position(level: DangerLevel, outsideCoreZone: Boolean): Double =
        if (outsideCoreZone) (level.x - 1.0).coerceAtLeast(DangerLevel.L1.x) else level.x
}

enum class Zone(val title: String, val text: String) {
    GREEN("Grün – tiefes Risiko", "Hang liegt im grünen Bereich."),
    ORANGE("Orange – erhöhtes Risiko", "Nur mit guter Beurteilung und Vorsichtsmassnahmen."),
    RED("Rot – hohes Risiko", "Hang meiden."),
}

/** Gefahrenstufen inkl. Zwischenstufen wie im SLF-Bulletin, mit Lage auf der X-Achse. */
enum class DangerLevel(val label: String, val x: Double, val base: Int) {
    L1("1", 0.5, 1),
    L2_LOW("2−", 1.0 + 1.0 / 6, 2),
    L2_MID("2=", 1.5, 2),
    L2_HIGH("2+", 1.0 + 5.0 / 6, 2),
    L3_LOW("3−", 2.0 + 1.0 / 6, 3),
    L3_MID("3=", 2.5, 3),
    L3_HIGH("3+", 2.0 + 5.0 / 6, 3),
    L4("4", 3.5, 4);

    val word: String
        get() = when (base) {
            1 -> "gering"
            2 -> "mässig"
            3 -> "erheblich"
            else -> "gross"
        }

    /** Wo die steilste Stelle gesucht werden muss */
    val relevantArea: String
        get() = when (base) {
            1 -> "Spur und steilste Stelle bis 20 m daneben"
            2 -> "Bereich 20 m rund um die Spur"
            3 -> "ganzer Hang"
            else -> "ganzer Hang inkl. Auslaufbereich"
        }
}

/** Letzte Eingaben merken. */
class GrmSettings(context: Context) {
    private val prefs = context.getSharedPreferences("grm", Context.MODE_PRIVATE)

    var level: DangerLevel
        get() = runCatching { DangerLevel.valueOf(prefs.getString("level", "L2_MID")!!) }.getOrDefault(DangerLevel.L2_MID)
        set(v) = prefs.edit { putString("level", v.name) }

    var outsideCoreZone: Boolean
        get() = prefs.getBoolean("outside", false)
        set(v) = prefs.edit { putBoolean("outside", v) }
}
