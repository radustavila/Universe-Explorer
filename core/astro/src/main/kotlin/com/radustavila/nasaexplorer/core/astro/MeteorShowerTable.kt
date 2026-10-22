package com.radustavila.nasaexplorer.core.astro

import com.radustavila.nasaexplorer.core.model.MeteorShower
import java.time.MonthDay

/**
 * What the calendar knows about one yearly meteor shower. Peaks are fixed by where the Earth is in
 * its orbit, so they are kept as the Sun's ecliptic longitude: the date then follows for any year,
 * including leap-year shifts.
 */
data class MeteorShowerData(
    val shower: MeteorShower,
    /** First day of activity; can fall in the previous year (the Quadrantids start in December). */
    val activeFrom: MonthDay,
    val activeUntil: MonthDay,
    /** Solar longitude of the maximum, degrees (J2000 equinox). */
    val peakSolarLongitude: Double,
    /** Zenithal hourly rate at a normal maximum. */
    val zenithalHourlyRate: Int,
    /** Radiant at the maximum, J2000: right ascension in degrees, declination in degrees. */
    val radiantRaDegrees: Double,
    val radiantDecDegrees: Double,
    val parentBody: String,
)

/**
 * The major annual showers, from the International Meteor Organization's working list of visual
 * meteor showers (activity dates, maximum solar longitude, ZHR, radiant) and the parent bodies
 * named in the literature. Facts only; reviewed 2026-10. Rates of the Quadrantids, Draconids and
 * Eta Aquariids vary a lot from year to year: the table holds a typical value.
 */
object MeteorShowerTable {

    val showers: List<MeteorShowerData> = listOf(
        MeteorShowerData(
            shower = MeteorShower.QUADRANTIDS,
            activeFrom = MonthDay.of(12, 28),
            activeUntil = MonthDay.of(1, 12),
            peakSolarLongitude = 283.15,
            zenithalHourlyRate = 80,
            radiantRaDegrees = 230.0,
            radiantDecDegrees = 49.0,
            parentBody = "2003 EH1",
        ),
        MeteorShowerData(
            shower = MeteorShower.LYRIDS,
            activeFrom = MonthDay.of(4, 14),
            activeUntil = MonthDay.of(4, 30),
            peakSolarLongitude = 32.32,
            zenithalHourlyRate = 18,
            radiantRaDegrees = 271.0,
            radiantDecDegrees = 34.0,
            parentBody = "C/1861 G1 (Thatcher)",
        ),
        MeteorShowerData(
            shower = MeteorShower.ETA_AQUARIIDS,
            activeFrom = MonthDay.of(4, 19),
            activeUntil = MonthDay.of(5, 28),
            peakSolarLongitude = 45.5,
            zenithalHourlyRate = 50,
            radiantRaDegrees = 338.0,
            radiantDecDegrees = -1.0,
            parentBody = "1P/Halley",
        ),
        MeteorShowerData(
            shower = MeteorShower.SOUTHERN_DELTA_AQUARIIDS,
            activeFrom = MonthDay.of(7, 12),
            activeUntil = MonthDay.of(8, 23),
            peakSolarLongitude = 127.0,
            zenithalHourlyRate = 25,
            radiantRaDegrees = 340.0,
            radiantDecDegrees = -16.0,
            parentBody = "96P/Machholz",
        ),
        MeteorShowerData(
            shower = MeteorShower.PERSEIDS,
            activeFrom = MonthDay.of(7, 17),
            activeUntil = MonthDay.of(8, 24),
            peakSolarLongitude = 140.0,
            zenithalHourlyRate = 100,
            radiantRaDegrees = 48.0,
            radiantDecDegrees = 58.0,
            parentBody = "109P/Swift–Tuttle",
        ),
        MeteorShowerData(
            shower = MeteorShower.DRACONIDS,
            activeFrom = MonthDay.of(10, 6),
            activeUntil = MonthDay.of(10, 10),
            peakSolarLongitude = 195.4,
            zenithalHourlyRate = 10,
            radiantRaDegrees = 262.0,
            radiantDecDegrees = 54.0,
            parentBody = "21P/Giacobini–Zinner",
        ),
        MeteorShowerData(
            shower = MeteorShower.ORIONIDS,
            activeFrom = MonthDay.of(10, 2),
            activeUntil = MonthDay.of(11, 7),
            peakSolarLongitude = 208.0,
            zenithalHourlyRate = 20,
            radiantRaDegrees = 95.0,
            radiantDecDegrees = 16.0,
            parentBody = "1P/Halley",
        ),
        MeteorShowerData(
            shower = MeteorShower.LEONIDS,
            activeFrom = MonthDay.of(11, 6),
            activeUntil = MonthDay.of(11, 30),
            peakSolarLongitude = 235.27,
            zenithalHourlyRate = 15,
            radiantRaDegrees = 152.0,
            radiantDecDegrees = 22.0,
            parentBody = "55P/Tempel–Tuttle",
        ),
        MeteorShowerData(
            shower = MeteorShower.GEMINIDS,
            activeFrom = MonthDay.of(12, 4),
            activeUntil = MonthDay.of(12, 20),
            peakSolarLongitude = 262.2,
            zenithalHourlyRate = 150,
            radiantRaDegrees = 112.0,
            radiantDecDegrees = 33.0,
            parentBody = "3200 Phaethon",
        ),
        MeteorShowerData(
            shower = MeteorShower.URSIDS,
            activeFrom = MonthDay.of(12, 17),
            activeUntil = MonthDay.of(12, 26),
            peakSolarLongitude = 270.7,
            zenithalHourlyRate = 10,
            radiantRaDegrees = 217.0,
            radiantDecDegrees = 76.0,
            parentBody = "8P/Tuttle",
        ),
    )

    fun of(shower: MeteorShower): MeteorShowerData = showers.first { it.shower == shower }
}
