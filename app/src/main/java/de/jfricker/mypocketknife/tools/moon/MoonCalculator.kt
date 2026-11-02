package de.jfricker.mypocketknife.tools.moon

import org.shredzone.commons.suncalc.MoonIllumination
import org.shredzone.commons.suncalc.MoonPhase
import org.shredzone.commons.suncalc.MoonPosition
import org.shredzone.commons.suncalc.MoonTimes
import java.time.Duration
import java.time.ZonedDateTime
import kotlin.math.abs

data class MoonEvent(val type: Type, val time: ZonedDateTime, val superMoon: Boolean) {
    enum class Type { FULL, NEW }
}

data class MoonInfo(
    val now: ZonedDateTime,
    /** Beleuchteter Anteil 0.0 (Neumond) … 1.0 (Vollmond) */
    val fraction: Double,
    val waxing: Boolean,
    val phaseName: String,
    /** Tage seit dem letzten Neumond */
    val ageDays: Double,
    val nextFull: MoonEvent,
    val nextNew: MoonEvent,
    /** Die nächsten Voll- und Neumonde chronologisch */
    val upcoming: List<MoonEvent>,
    // --- nur mit Standort ---
    val hasLocation: Boolean,
    val rise: ZonedDateTime? = null,
    val set: ZonedDateTime? = null,
    val alwaysUp: Boolean = false,
    val alwaysDown: Boolean = false,
    /** Höhe über dem Horizont in Grad (negativ = unter dem Horizont) */
    val altitude: Double? = null,
    /** Himmelsrichtung in Grad, 0 = Norden, 90 = Osten */
    val azimuth: Double? = null,
    /** Südhalbkugel: Mond erscheint gespiegelt */
    val southern: Boolean = false,
)

/** Alle Mondberechnungen, offline mit commons-suncalc. */
object MoonCalculator {

    fun compute(now: ZonedDateTime, lat: Double?, lng: Double?): MoonInfo {
        val zone = now.zone
        val hasLoc = lat != null && lng != null

        var illumParams = MoonIllumination.compute().on(now)
        if (hasLoc) illumParams = illumParams.at(lat!!, lng!!)
        val illum = illumParams.execute()

        // Phasenwinkel: -180 = Neumond, -90 = erstes Viertel, 0 = Vollmond, +90 = letztes Viertel
        val phase = illum.phase
        val waxing = phase < 0

        val fulls = events(MoonEvent.Type.FULL, now, 3)
        val news = events(MoonEvent.Type.NEW, now, 3)

        val prevNew = previousNewMoon(now)
        val age = Duration.between(prevNew, now).toMinutes() / (60.0 * 24.0)

        var info = MoonInfo(
            now = now,
            fraction = illum.fraction,
            waxing = waxing,
            phaseName = phaseName(phase),
            ageDays = age,
            nextFull = fulls.first(),
            nextNew = news.first(),
            upcoming = (fulls + news).sortedBy { it.time }.take(6),
            hasLocation = hasLoc,
        )

        if (hasLoc) {
            val startOfDay = now.toLocalDate().atStartOfDay(zone)
            val times = MoonTimes.compute()
                .on(startOfDay)
                .timezone(zone)
                .at(lat!!, lng!!)
                .oneDay()
                .execute()
            val pos = MoonPosition.compute().on(now).at(lat, lng).execute()
            info = info.copy(
                rise = times.rise?.withZoneSameInstant(zone),
                set = times.set?.withZoneSameInstant(zone),
                alwaysUp = times.isAlwaysUp,
                alwaysDown = times.isAlwaysDown,
                altitude = pos.altitude,
                azimuth = pos.azimuth,
                southern = lat < 0,
            )
        }
        return info
    }

    /** Die nächsten [count] Ereignisse eines Typs ab [from]. */
    private fun events(type: MoonEvent.Type, from: ZonedDateTime, count: Int): List<MoonEvent> {
        val phase = if (type == MoonEvent.Type.FULL) MoonPhase.Phase.FULL_MOON else MoonPhase.Phase.NEW_MOON
        val result = mutableListOf<MoonEvent>()
        var t = from
        repeat(count) {
            val p = MoonPhase.compute().phase(phase).on(t).execute()
            val time = p.time.withZoneSameInstant(from.zone)
            result += MoonEvent(type, time, superMoon = type == MoonEvent.Type.FULL && p.isSuperMoon)
            t = time.plusDays(1)
        }
        return result
    }

    private fun previousNewMoon(now: ZonedDateTime): ZonedDateTime {
        // Eine Lunation dauert mind. ~29,3 Tage: im Fenster der letzten 29 Tage liegt höchstens ein Neumond.
        val a = MoonPhase.compute().phase(MoonPhase.Phase.NEW_MOON).on(now.minusDays(29)).execute().time
        if (!a.isAfter(now)) return a
        return MoonPhase.compute().phase(MoonPhase.Phase.NEW_MOON).on(now.minusDays(31)).execute().time
    }

    fun phaseName(phase: Double): String {
        val a = abs(phase)
        return when {
            a > 168.75 -> "Neumond"
            a < 11.25 -> "Vollmond"
            abs(a - 90) < 11.25 -> if (phase < 0) "Erstes Viertel (Halbmond)" else "Letztes Viertel (Halbmond)"
            a > 90 -> if (phase < 0) "Zunehmende Sichel" else "Abnehmende Sichel"
            else -> if (phase < 0) "Zunehmender Mond" else "Abnehmender Mond"
        }
    }

    /** 0° = N, 90° = O … in 8 Richtungen */
    fun compass(azimuth: Double): String {
        val names = listOf("Norden", "Nordosten", "Osten", "Südosten", "Süden", "Südwesten", "Westen", "Nordwesten")
        val i = (((azimuth % 360 + 360) % 360 + 22.5) / 45).toInt() % 8
        return names[i]
    }
}
