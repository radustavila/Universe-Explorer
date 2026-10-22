package com.radustavila.nasaexplorer.core.astro

import com.radustavila.nasaexplorer.core.domain.sky.SkyCalculator
import com.radustavila.nasaexplorer.core.model.GeoCoordinate
import com.radustavila.nasaexplorer.core.model.MoonPhase
import com.radustavila.nasaexplorer.core.model.MoonTonight
import com.radustavila.nasaexplorer.core.model.NightWindow
import com.radustavila.nasaexplorer.core.model.Planet
import com.radustavila.nasaexplorer.core.model.PlanetTonight
import com.radustavila.nasaexplorer.core.model.SkyTonight
import io.github.cosinekitty.astronomy.Aberration
import io.github.cosinekitty.astronomy.Body
import io.github.cosinekitty.astronomy.Direction
import io.github.cosinekitty.astronomy.EquatorEpoch
import io.github.cosinekitty.astronomy.Observer
import io.github.cosinekitty.astronomy.Refraction
import io.github.cosinekitty.astronomy.Time
import io.github.cosinekitty.astronomy.Topocentric
import io.github.cosinekitty.astronomy.constellation
import io.github.cosinekitty.astronomy.equator
import io.github.cosinekitty.astronomy.horizon
import io.github.cosinekitty.astronomy.illumination
import io.github.cosinekitty.astronomy.moonPhase
import io.github.cosinekitty.astronomy.searchAltitude
import io.github.cosinekitty.astronomy.searchMoonPhase
import io.github.cosinekitty.astronomy.searchRiseSet
import java.time.Duration
import java.time.Instant
import javax.inject.Inject

/**
 * [SkyCalculator] on Astronomy Engine (about one arcminute, rise and set times to within a minute).
 *
 * Rise and set times follow the almanac convention: the top of the Sun's or Moon's disc touching
 * the horizon, with standard refraction. Twilight and planet altitudes use the body's centre.
 */
class AstronomyEngineSkyCalculator @Inject constructor() : SkyCalculator {

    override fun skyTonight(observer: GeoCoordinate, now: Instant): SkyTonight {
        val site = Observer(observer.latitude, observer.longitude, 0.0)
        val time = now.toAstronomyTime()
        val night = nightWindow(site, time)
        // Rise and set times are looked up from the start of the night: the evening's sunset, or
        // now in a polar day.
        val windowStart = night.sunset ?: now
        val windowEnd = night.sunrise ?: windowStart.plus(Duration.ofDays(1))
        return SkyTonight(
            computedAt = now,
            night = night,
            moon = moonTonight(site, time, windowStart.toAstronomyTime()),
            planets = Planet.entries
                .map { planet -> planetTonight(planet, site, night, windowStart, windowEnd) }
                .sortedWith(compareByDescending<PlanetTonight> { it.isVisibleTonight }.thenBy { it.bestTime }),
        )
    }

    /** Tonight's sunset to tomorrow's sunrise; tonight is the night under way when the Sun is down. */
    private fun nightWindow(site: Observer, now: Time): NightWindow {
        val lastSet = searchRiseSet(Body.Sun, site, Direction.Set, now, -SEARCH_DAYS)
        val lastRise = searchRiseSet(Body.Sun, site, Direction.Rise, now, -SEARCH_DAYS)
        val isNight = lastSet != null && (lastRise == null || lastSet.ut > lastRise.ut)
        val sunset = if (isNight) lastSet else searchRiseSet(Body.Sun, site, Direction.Set, now, SEARCH_DAYS)
        val start = sunset ?: now
        val sunrise = searchRiseSet(Body.Sun, site, Direction.Rise, start, SEARCH_DAYS)
        val end = sunrise ?: start.addDays(SEARCH_DAYS)

        fun dusk(altitude: Double) = searchAltitude(Body.Sun, site, Direction.Set, start, SEARCH_DAYS, altitude)
            ?.takeIf { it.ut < end.ut }

        fun dawn(altitude: Double) = searchAltitude(Body.Sun, site, Direction.Rise, start, SEARCH_DAYS, altitude)
            ?.takeIf { it.ut <= end.ut }

        return NightWindow(
            sunset = sunset?.toInstant(),
            civilDusk = dusk(CIVIL)?.toInstant(),
            nauticalDusk = dusk(NAUTICAL)?.toInstant(),
            astronomicalDusk = dusk(ASTRONOMICAL)?.toInstant(),
            astronomicalDawn = dawn(ASTRONOMICAL)?.toInstant(),
            nauticalDawn = dawn(NAUTICAL)?.toInstant(),
            civilDawn = dawn(CIVIL)?.toInstant(),
            sunrise = sunrise?.toInstant(),
        )
    }

    private fun moonTonight(site: Observer, now: Time, windowStart: Time): MoonTonight {
        val phaseAngle = moonPhase(now)
        return MoonTonight(
            phase = MoonPhase.fromPhaseAngle(phaseAngle),
            phaseAngleDegrees = phaseAngle,
            illuminatedFraction = illumination(Body.Moon, now).phaseFraction,
            rise = searchRiseSet(Body.Moon, site, Direction.Rise, windowStart, SEARCH_DAYS)?.toInstant(),
            set = searchRiseSet(Body.Moon, site, Direction.Set, windowStart, SEARCH_DAYS)?.toInstant(),
            isUpAtStart = altitude(Body.Moon, site, windowStart).altitude > 0.0,
            nextFullMoon = checkNotNull(searchMoonPhase(FULL_MOON, now, LUNAR_MONTH_DAYS)).toInstant(),
            nextNewMoon = checkNotNull(searchMoonPhase(NEW_MOON, now, LUNAR_MONTH_DAYS)).toInstant(),
        )
    }

    private fun planetTonight(
        planet: Planet,
        site: Observer,
        night: NightWindow,
        windowStart: Instant,
        windowEnd: Instant,
    ): PlanetTonight {
        val body = planet.body
        val start = windowStart.toAstronomyTime()
        // The planet has to be found against a darkening sky: from civil dusk to civil dawn.
        val darkFrom = night.civilDusk ?: windowStart
        val darkUntil = night.civilDawn ?: windowEnd
        val best = highestPoint(body, site, darkFrom, darkUntil)
            ?.takeIf { (_, position) -> position.altitude >= PlanetTonight.MIN_VISIBLE_ALTITUDE }
        val referenceTime = best?.first ?: windowStart
        val j2000 = equator(body, referenceTime.toAstronomyTime(), site, EquatorEpoch.J2000, Aberration.Corrected)
        return PlanetTonight(
            planet = planet,
            rise = searchRiseSet(body, site, Direction.Rise, start, SEARCH_DAYS)?.toInstant(),
            set = searchRiseSet(body, site, Direction.Set, start, SEARCH_DAYS)?.toInstant(),
            isUpAtStart = altitude(body, site, start).altitude > 0.0,
            bestTime = best?.first,
            bestAltitudeDegrees = best?.second?.altitude,
            bestAzimuthDegrees = best?.second?.azimuth,
            magnitude = illumination(body, referenceTime.toAstronomyTime()).mag,
            constellation = constellation(j2000.ra, j2000.dec).name,
        )
    }

    /** Coarse scan, then a golden-section refinement around the highest sample. */
    private fun highestPoint(body: Body, site: Observer, from: Instant, until: Instant): Pair<Instant, Topocentric>? {
        if (!until.isAfter(from)) return null
        var bestTime = from
        var bestAltitude = Double.NEGATIVE_INFINITY
        var at = from
        while (!at.isAfter(until)) {
            val altitude = altitude(body, site, at.toAstronomyTime()).altitude
            if (altitude > bestAltitude) {
                bestAltitude = altitude
                bestTime = at
            }
            at = at.plus(SCAN_STEP)
        }
        var low = maxOf(from, bestTime.minus(SCAN_STEP)).toEpochMilli().toDouble()
        var high = minOf(until, bestTime.plus(SCAN_STEP)).toEpochMilli().toDouble()
        val altitudeAt: (Double) -> Double = { millis ->
            altitude(body, site, Instant.ofEpochMilli(millis.toLong()).toAstronomyTime()).altitude
        }
        while (high - low > REFINE_PRECISION_MILLIS) {
            val left = high - GOLDEN_RATIO_CONJUGATE * (high - low)
            val right = low + GOLDEN_RATIO_CONJUGATE * (high - low)
            if (altitudeAt(left) < altitudeAt(right)) low = left else high = right
        }
        val peak = Instant.ofEpochMilli(((low + high) / 2).toLong())
        return peak to altitude(body, site, peak.toAstronomyTime())
    }

    private fun altitude(body: Body, site: Observer, time: Time): Topocentric {
        val ofDate = equator(body, time, site, EquatorEpoch.OfDate, Aberration.Corrected)
        return horizon(time, site, ofDate.ra, ofDate.dec, Refraction.Normal)
    }

    private val Planet.body: Body
        get() = when (this) {
            Planet.MERCURY -> Body.Mercury
            Planet.VENUS -> Body.Venus
            Planet.MARS -> Body.Mars
            Planet.JUPITER -> Body.Jupiter
            Planet.SATURN -> Body.Saturn
        }

    private companion object {
        const val SEARCH_DAYS = 1.0
        const val LUNAR_MONTH_DAYS = 32.0
        const val CIVIL = -6.0
        const val NAUTICAL = -12.0
        const val ASTRONOMICAL = -18.0
        const val NEW_MOON = 0.0
        const val FULL_MOON = 180.0
        val SCAN_STEP: Duration = Duration.ofMinutes(10)
        const val REFINE_PRECISION_MILLIS = 30_000.0
        const val GOLDEN_RATIO_CONJUGATE = 0.6180339887498949
    }
}

internal fun Time.toInstant(): Instant = Instant.ofEpochMilli(toMillisecondsSince1970())
