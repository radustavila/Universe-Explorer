package com.radustavila.nasaexplorer.core.astro

import com.radustavila.nasaexplorer.core.model.MeteorShowerPeak
import com.radustavila.nasaexplorer.core.model.MeteorViewing
import io.github.cosinekitty.astronomy.Body
import io.github.cosinekitty.astronomy.Direction
import io.github.cosinekitty.astronomy.Observer
import io.github.cosinekitty.astronomy.Refraction
import io.github.cosinekitty.astronomy.Time
import io.github.cosinekitty.astronomy.constellation
import io.github.cosinekitty.astronomy.horizon
import io.github.cosinekitty.astronomy.illumination
import io.github.cosinekitty.astronomy.searchAltitude
import io.github.cosinekitty.astronomy.searchSunLongitude
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Peaks of the showers in [MeteorShowerTable], with the Moon at the peak and the best night to watch. */
internal object MeteorShowerSearch {

    private const val NAUTICAL_TWILIGHT = -12.0
    private const val DEGREES_PER_HOUR = 15.0
    private const val FULL_TURN = 360.0
    private const val J2000_YEAR = 2000.0
    private const val YEARS_PER_CENTURY = 100.0
    private const val PRECESSION_DEGREES_PER_CENTURY = 1.3969713
    private const val DAYS_PER_YEAR_SEARCH = 366.0
    private const val VIEWING_NIGHTS = 3
    private val VIEWING_SEARCH_BEFORE: Duration = Duration.ofHours(30)

    fun peaks(range: ClosedRange<Instant>, site: Observer?): List<MeteorShowerPeak> =
        years(range).flatMap { year ->
            MeteorShowerTable.showers.mapNotNull { data ->
                peak(data, year)
                    ?.takeIf { it.toInstant() in range }
                    ?.let { peak -> peakEvent(data, peak, site) }
            }
        }

    private fun peakEvent(data: MeteorShowerData, peak: Time, site: Observer?): MeteorShowerPeak {
        val peakInstant = peak.toInstant()
        val (activeFrom, activeUntil) = activity(data, peakInstant.atOffset(ZoneOffset.UTC).toLocalDate())
        val radiantRaHours = data.radiantRaDegrees / DEGREES_PER_HOUR
        return MeteorShowerPeak(
            time = peakInstant,
            shower = data.shower,
            activeFrom = activeFrom,
            activeUntil = activeUntil,
            zenithalHourlyRate = data.zenithalHourlyRate,
            parentBody = data.parentBody,
            radiantConstellation = constellation(radiantRaHours, data.radiantDecDegrees).name,
            moonIlluminatedFraction = illumination(Body.Moon, peak).phaseFraction,
            viewing = site?.let { viewing(it, peakInstant, radiantRaHours, data.radiantDecDegrees) },
        )
    }

    /** The activity dates around [peakDate]; a window that wraps the new year starts in the previous one. */
    private fun activity(data: MeteorShowerData, peakDate: LocalDate): Pair<LocalDate, LocalDate> {
        val wrapsNewYear = data.activeFrom > data.activeUntil
        return if (wrapsNewYear && peakDate.monthValue <= data.activeUntil.monthValue) {
            data.activeFrom.atYear(peakDate.year - 1) to data.activeUntil.atYear(peakDate.year)
        } else if (wrapsNewYear) {
            data.activeFrom.atYear(peakDate.year) to data.activeUntil.atYear(peakDate.year + 1)
        } else {
            data.activeFrom.atYear(peakDate.year) to data.activeUntil.atYear(peakDate.year)
        }
    }

    /**
     * The maximum in [year]: when the Sun reaches the shower's solar longitude. The table's
     * longitudes are referred to the J2000 equinox and Astronomy Engine searches the equinox of
     * date, so precession since 2000 is added (about 0.36 degrees, nine hours, by 2026).
     */
    private fun peak(data: MeteorShowerData, year: Int): Time? {
        val centuries = (year - J2000_YEAR) / YEARS_PER_CENTURY
        val longitudeOfDate = (data.peakSolarLongitude + PRECESSION_DEGREES_PER_CENTURY * centuries) % FULL_TURN
        return searchSunLongitude(longitudeOfDate, Time(year, 1, 1, 0, 0, 0.0), DAYS_PER_YEAR_SEARCH)
    }

    /**
     * The darkness (Sun 12 degrees down) closest to [peak], and when the radiant is highest in it.
     * The radiant's J2000 position is used as of date: a quarter of a degree changes nothing here.
     */
    private fun viewing(site: Observer, peak: Instant, raHours: Double, decDegrees: Double): MeteorViewing? {
        val (darkFrom, darkUntil) = darkNights(site, peak)
            .minByOrNull { (dusk, dawn) -> minutesBetween(peak, dusk, dawn) } ?: return null
        val best = generateSequence(darkFrom) { it.plus(SCAN_STEP) }
            .takeWhile { !it.isAfter(darkUntil) }
            .map { at -> at to horizon(at.toAstronomyTime(), site, raHours, decDegrees, Refraction.None) }
            .maxBy { (_, position) -> position.altitude }
        return MeteorViewing(
            darkFrom = darkFrom,
            darkUntil = darkUntil,
            bestTime = best.first,
            radiantAltitudeDegrees = best.second.altitude,
            radiantAzimuthDegrees = best.second.azimuth,
        )
    }

    /** How far [at] is from the night [dusk] to [dawn]: 0 inside it. */
    private fun minutesBetween(at: Instant, dusk: Instant, dawn: Instant): Long = when {
        at.isBefore(dusk) -> Duration.between(at, dusk).toMinutes()
        at.isAfter(dawn) -> Duration.between(dawn, at).toMinutes()
        else -> 0L
    }

    /** The nights around [peak], each from nautical dusk to nautical dawn; none in a polar summer. */
    private fun darkNights(site: Observer, peak: Instant): List<Pair<Instant, Instant>> =
        generateSequence(peak.minus(VIEWING_SEARCH_BEFORE).toAstronomyTime()) { it.addDays(1.0) }
            .take(VIEWING_NIGHTS)
            .mapNotNull { searchFrom ->
                val dusk = searchAltitude(Body.Sun, site, Direction.Set, searchFrom, 1.0, NAUTICAL_TWILIGHT)
                val dawn = dusk?.let { searchAltitude(Body.Sun, site, Direction.Rise, it, 1.0, NAUTICAL_TWILIGHT) }
                if (dusk == null || dawn == null) null else dusk.toInstant() to dawn.toInstant()
            }
            .distinct()
            .toList()
}
