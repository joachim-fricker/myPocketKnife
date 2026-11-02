package de.jfricker.mypocketknife.tools.sun

import org.shredzone.commons.suncalc.SunPosition
import org.shredzone.commons.suncalc.SunTimes
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/** Zeitfenster; null = findet an diesem Tag nicht statt (z. B. im hohen Norden). */
data class TimeWindow(val start: ZonedDateTime?, val end: ZonedDateTime?)

data class SunDay(
    val date: LocalDate,
    val rise: ZonedDateTime?,
    val set: ZonedDateTime?,
    val noon: ZonedDateTime?,
    val noonAltitude: Double?,
    val alwaysUp: Boolean,
    val alwaysDown: Boolean,
    val dayLength: Duration?,
    /** Sonne zwischen -4° und +6° */
    val goldenMorning: TimeWindow,
    val goldenEvening: TimeWindow,
    /** Sonne zwischen -8° und -4° */
    val blueMorning: TimeWindow,
    val blueEvening: TimeWindow,
    /** Beginn am Morgen / Ende am Abend */
    val civil: TimeWindow,
    val nautical: TimeWindow,
    val astronomical: TimeWindow,
    /** Sonnenhöhe alle 10 Minuten über den Tag (144 Werte) – für das Tagesband */
    val altitudes: List<Double>,
    // nur für heute:
    val nowAltitude: Double?,
    val nowAzimuth: Double?,
)

/** Sonnenzeiten offline mit commons-suncalc. */
object SunCalculator {

    const val SAMPLE_MINUTES = 10

    fun compute(date: LocalDate, zone: ZoneId, lat: Double, lng: Double, now: ZonedDateTime?): SunDay {
        val start = date.atStartOfDay(zone)

        fun times(t: SunTimes.Twilight): SunTimes =
            SunTimes.compute().on(start).timezone(zone).at(lat, lng).twilight(t).oneDay().execute()

        val visual = times(SunTimes.Twilight.VISUAL)
        val golden = times(SunTimes.Twilight.GOLDEN_HOUR)   // +6°
        val blue = times(SunTimes.Twilight.BLUE_HOUR)       // -4°
        val night = times(SunTimes.Twilight.NIGHT_HOUR)     // -8°
        val civil = times(SunTimes.Twilight.CIVIL)
        val nautical = times(SunTimes.Twilight.NAUTICAL)
        val astro = times(SunTimes.Twilight.ASTRONOMICAL)

        fun z(t: ZonedDateTime?) = t?.withZoneSameInstant(zone)

        val rise = z(visual.rise)
        val set = z(visual.set)
        val noon = z(visual.noon)

        val altitudes = (0 until 24 * 60 / SAMPLE_MINUTES).map { i ->
            position(start.plusMinutes(i.toLong() * SAMPLE_MINUTES), lat, lng).altitude
        }

        val nowPos = now?.let { position(it, lat, lng) }

        return SunDay(
            date = date,
            rise = rise,
            set = set,
            noon = noon,
            noonAltitude = noon?.let { position(it, lat, lng).altitude },
            alwaysUp = visual.isAlwaysUp,
            alwaysDown = visual.isAlwaysDown,
            dayLength = when {
                visual.isAlwaysUp -> Duration.ofHours(24)
                visual.isAlwaysDown -> Duration.ZERO
                rise != null && set != null && set.isAfter(rise) -> Duration.between(rise, set)
                else -> null
            },
            goldenMorning = TimeWindow(z(blue.rise), z(golden.rise)),
            goldenEvening = TimeWindow(z(golden.set), z(blue.set)),
            blueMorning = TimeWindow(z(night.rise), z(blue.rise)),
            blueEvening = TimeWindow(z(blue.set), z(night.set)),
            civil = TimeWindow(z(civil.rise), z(civil.set)),
            nautical = TimeWindow(z(nautical.rise), z(nautical.set)),
            astronomical = TimeWindow(z(astro.rise), z(astro.set)),
            altitudes = altitudes,
            nowAltitude = nowPos?.altitude,
            nowAzimuth = nowPos?.azimuth,
        )
    }

    private fun position(t: ZonedDateTime, lat: Double, lng: Double): SunPosition =
        SunPosition.compute().on(t).at(lat, lng).execute()

    /** Wie heisst die Lichtphase bei dieser Sonnenhöhe? */
    fun phaseOf(altitude: Double): String = when {
        altitude >= 6 -> "Tag"
        altitude >= -4 -> "Goldene Stunde ✨"
        altitude >= -8 -> "Blaue Stunde"
        altitude >= -12 -> "Nautische Dämmerung"
        altitude >= -18 -> "Astronomische Dämmerung"
        else -> "Nacht"
    }
}
