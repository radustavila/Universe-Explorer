package com.radustavila.nasaexplorer.core.astro

import com.radustavila.nasaexplorer.core.model.Planet
import io.github.cosinekitty.astronomy.Aberration
import io.github.cosinekitty.astronomy.Body
import io.github.cosinekitty.astronomy.EquatorEpoch
import io.github.cosinekitty.astronomy.Observer
import io.github.cosinekitty.astronomy.Refraction
import io.github.cosinekitty.astronomy.equator
import io.github.cosinekitty.astronomy.horizon
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

/* What the sky event searches share: positions above the observer's horizon and range helpers. */

/** Step of every scan over time: fine enough for horizons and radiants, which move slowly. */
internal val SCAN_STEP: Duration = Duration.ofMinutes(10)

/** Searches resume a little after the previous hit, so they do not find it again. */
internal const val SEARCH_STEP_DAYS = 1.0

/** Altitude of the body's centre with standard refraction, degrees. */
internal fun altitude(body: Body, site: Observer, at: Instant): Double {
    val time = at.toAstronomyTime()
    val ofDate = equator(body, time, site, EquatorEpoch.OfDate, Aberration.Corrected)
    return horizon(time, site, ofDate.ra, ofDate.dec, Refraction.Normal).altitude
}

/** Whether the body's centre is above the horizon at any moment from [from] to [until]. */
internal fun isAboveHorizonDuring(body: Body, site: Observer, from: Instant, until: Instant): Boolean =
    generateSequence(from) { it.plus(SCAN_STEP) }
        .takeWhile { !it.isAfter(until) }
        .plus(until)
        .any { altitude(body, site, it) > 0.0 }

/** The UTC years [range] touches. */
internal fun years(range: ClosedRange<Instant>): IntRange =
    range.start.atOffset(ZoneOffset.UTC).year..range.endInclusive.atOffset(ZoneOffset.UTC).year

internal val Planet.body: Body
    get() = when (this) {
        Planet.MERCURY -> Body.Mercury
        Planet.VENUS -> Body.Venus
        Planet.MARS -> Body.Mars
        Planet.JUPITER -> Body.Jupiter
        Planet.SATURN -> Body.Saturn
    }
