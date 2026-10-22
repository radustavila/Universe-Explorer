package com.radustavila.nasaexplorer.core.astro

import com.radustavila.nasaexplorer.core.domain.stargazing.DarkSkyCalculator
import com.radustavila.nasaexplorer.core.model.DarkNight
import com.radustavila.nasaexplorer.core.model.GeoCoordinate
import com.radustavila.nasaexplorer.core.model.MoonSample
import com.radustavila.nasaexplorer.core.model.NightDarkness
import io.github.cosinekitty.astronomy.Aberration
import io.github.cosinekitty.astronomy.Body
import io.github.cosinekitty.astronomy.Direction
import io.github.cosinekitty.astronomy.EquatorEpoch
import io.github.cosinekitty.astronomy.Observer
import io.github.cosinekitty.astronomy.Refraction
import io.github.cosinekitty.astronomy.Time
import io.github.cosinekitty.astronomy.equator
import io.github.cosinekitty.astronomy.horizon
import io.github.cosinekitty.astronomy.illumination
import io.github.cosinekitty.astronomy.searchAltitude
import io.github.cosinekitty.astronomy.searchRiseSet
import java.time.Duration
import java.time.Instant
import javax.inject.Inject

/**
 * [DarkSkyCalculator] on Astronomy Engine: for each night, when the Sun is 18 degrees below the
 * horizon (12 where it never gets that low), and the Moon's altitude and phase every hour of it.
 *
 * A night runs from one sunset to the next sunrise. Where the Sun does not set (polar day) or rise
 * (polar night) within a day, the night is the day that follows: never dark, or dark throughout.
 */
class AstronomyEngineDarkSkyCalculator @Inject constructor() : DarkSkyCalculator {

    override fun nights(observer: GeoCoordinate, now: Instant, count: Int): List<DarkNight> {
        val site = Observer(observer.latitude, observer.longitude, 0.0)
        var span = firstNight(site, now)
        var night = darkNight(site, span)
        // In the twilight before sunrise the dark part is over: tonight is the coming night.
        if (night.darkEnd?.isAfter(now) == false) {
            span = nextNight(site, after = span.end)
            night = darkNight(site, span)
        }
        val nights = mutableListOf(night)
        while (nights.size < count) {
            span = nextNight(site, after = span.end)
            nights += darkNight(site, span)
        }
        return nights.take(count)
    }

    /** The night under way at [now] while the Sun is down, else the coming one. */
    private fun firstNight(site: Observer, now: Instant): NightSpan {
        val time = now.toEngineTime()
        val lastSet = searchRiseSet(Body.Sun, site, Direction.Set, time, -SEARCH_DAYS)
        val lastRise = searchRiseSet(Body.Sun, site, Direction.Rise, time, -SEARCH_DAYS)
        val isNight = lastSet != null && (lastRise == null || lastSet.ut > lastRise.ut)
        val sunset = if (isNight) lastSet else searchRiseSet(Body.Sun, site, Direction.Set, time, SEARCH_DAYS)
        return span(site, sunset = sunset?.toJavaInstant(), from = now)
    }

    private fun nextNight(site: Observer, after: Instant): NightSpan {
        val from = after.plus(STEP_PAST_EVENT)
        val sunset = searchRiseSet(Body.Sun, site, Direction.Set, from.toEngineTime(), SEARCH_DAYS)
        return span(site, sunset = sunset?.toJavaInstant(), from = from)
    }

    private fun span(site: Observer, sunset: Instant?, from: Instant): NightSpan {
        val start = sunset ?: from
        val sunrise = searchRiseSet(Body.Sun, site, Direction.Rise, start.toEngineTime(), SEARCH_DAYS)?.toJavaInstant()
        return NightSpan(sunset = sunset, sunrise = sunrise, start = start, end = sunrise ?: start.plus(ONE_DAY))
    }

    private fun darkNight(site: Observer, span: NightSpan): DarkNight {
        val astronomical = darkPart(site, span, ASTRONOMICAL)
        val nautical = if (astronomical == null) darkPart(site, span, NAUTICAL) else null
        val dark = astronomical ?: nautical
        val darkness = when {
            astronomical != null -> NightDarkness.ASTRONOMICAL
            nautical != null -> NightDarkness.NAUTICAL
            else -> NightDarkness.NONE
        }
        val middle = dark?.let { (from, until) -> from.plus(Duration.between(from, until).dividedBy(2)) }
            ?: span.start.plus(Duration.between(span.start, span.end).dividedBy(2))
        return DarkNight(
            sunset = span.sunset,
            sunrise = span.sunrise,
            darkness = darkness,
            darkStart = dark?.first,
            darkEnd = dark?.second,
            moonSamples = dark?.let { (from, until) -> moonSamples(site, from, until) }.orEmpty(),
            moonIlluminatedFraction = illumination(Body.Moon, middle.toEngineTime()).phaseFraction,
        )
    }

    /** When the Sun is below [altitude] during [span], or null if it never gets that low. */
    private fun darkPart(site: Observer, span: NightSpan, altitude: Double): Pair<Instant, Instant>? {
        val start = span.start.toEngineTime()
        val dusk = if (sunAltitude(site, start) < altitude) {
            span.start
        } else {
            searchAltitude(Body.Sun, site, Direction.Set, start, SEARCH_DAYS, altitude)?.toJavaInstant()
                ?.takeIf { it.isBefore(span.end) }
                ?: return null
        }
        val dawn = searchAltitude(Body.Sun, site, Direction.Rise, dusk.toEngineTime(), SEARCH_DAYS, altitude)
            ?.toJavaInstant()
            ?.takeIf { !it.isAfter(span.end) }
            ?: span.end
        return dusk to dawn
    }

    private fun moonSamples(site: Observer, from: Instant, until: Instant): List<MoonSample> =
        generateSequence(from) { it.plus(SAMPLE_STEP) }
            .takeWhile { it.isBefore(until) }
            .map { at ->
                val time = at.toEngineTime()
                MoonSample(
                    time = at,
                    altitudeDegrees = altitudeOf(Body.Moon, site, time),
                    illuminatedFraction = illumination(Body.Moon, time).phaseFraction,
                )
            }
            .toList()

    private fun sunAltitude(site: Observer, time: Time): Double = altitudeOf(Body.Sun, site, time)

    /** Altitude of the body's centre with standard refraction, degrees. */
    private fun altitudeOf(body: Body, site: Observer, time: Time): Double {
        val ofDate = equator(body, time, site, EquatorEpoch.OfDate, Aberration.Corrected)
        return horizon(time, site, ofDate.ra, ofDate.dec, Refraction.Normal).altitude
    }

    private fun Instant.toEngineTime(): Time = Time.fromMillisecondsSince1970(toEpochMilli())

    private fun Time.toJavaInstant(): Instant = Instant.ofEpochMilli(toMillisecondsSince1970())

    /** From one sunset to the next sunrise, or a day where either is missing. */
    private data class NightSpan(val sunset: Instant?, val sunrise: Instant?, val start: Instant, val end: Instant)

    private companion object {
        /** Searches look a little over a day ahead: sunset to sunrise never takes longer outside the polar regions. */
        const val SEARCH_DAYS = 1.1
        const val ASTRONOMICAL = -18.0
        const val NAUTICAL = -12.0
        val ONE_DAY: Duration = Duration.ofDays(1)
        val SAMPLE_STEP: Duration = Duration.ofMinutes(MoonSample.STEP_MINUTES)

        /** Past a sunrise, so the next search does not find it again. */
        val STEP_PAST_EVENT: Duration = Duration.ofMinutes(1)
    }
}
