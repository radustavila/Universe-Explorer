package com.radustavila.nasaexplorer.core.astro

import com.radustavila.nasaexplorer.core.domain.sky.MilkyWayCalculator
import com.radustavila.nasaexplorer.core.domain.stargazing.DarkSkyCalculator
import com.radustavila.nasaexplorer.core.model.DarkNight
import com.radustavila.nasaexplorer.core.model.GalacticCentre
import com.radustavila.nasaexplorer.core.model.GeoCoordinate
import com.radustavila.nasaexplorer.core.model.HorizontalCoordinate
import com.radustavila.nasaexplorer.core.model.MilkyWayNight
import com.radustavila.nasaexplorer.core.model.MilkyWayWindow
import com.radustavila.nasaexplorer.core.model.NightDarkness
import com.radustavila.nasaexplorer.core.model.TimeInterval
import io.github.cosinekitty.astronomy.Body
import io.github.cosinekitty.astronomy.Observer
import io.github.cosinekitty.astronomy.Refraction
import io.github.cosinekitty.astronomy.Spherical
import io.github.cosinekitty.astronomy.horizon
import io.github.cosinekitty.astronomy.rotationEqjEqd
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.Month
import java.time.ZoneOffset
import javax.inject.Inject

/**
 * [MilkyWayCalculator] on Astronomy Engine. The nights and their darkness come from the
 * stargazing forecast's [DarkSkyCalculator] (F11), so both screens agree on when it is dark; this
 * adds the core (Sgr A*, precessed to the night's date), the Moon above the horizon and the
 * twilights.
 *
 * Crossings are found by a scan every few minutes refined by bisection to [PRECISION]; a core or
 * Moon that dips under the horizon for less than a scan step can be missed, which only happens
 * close to the poles where neither matters.
 */
class AstronomyEngineMilkyWayCalculator @Inject constructor(
    private val darkSky: DarkSkyCalculator,
) : MilkyWayCalculator {

    override fun nights(observer: GeoCoordinate, now: Instant, count: Int): List<MilkyWayNight> {
        val site = Observer(observer.latitude, observer.longitude, 0.0)
        var previousEnd = now
        return darkSky.nights(observer, now, count).map { dark ->
            night(site, dark, fallbackStart = previousEnd).also { previousEnd = it.end }
        }
    }

    override fun season(observer: GeoCoordinate, year: Int): Set<Month> {
        if (GalacticCentre.highestAltitude(observer.latitude) < GalacticCentre.MIN_ALTITUDE_DEGREES) return emptySet()
        val site = Observer(observer.latitude, observer.longitude, 0.0)
        return Month.entries.filterTo(mutableSetOf()) { month ->
            SEASON_SAMPLE_DAYS.any { day ->
                val noon = LocalDate.of(year, month, day).atTime(SEASON_SAMPLE_HOUR, 0).toInstant(ZoneOffset.UTC)
                val dark = darkSky.nights(observer, noon, count = 1).single()
                val darkPart = dark.astronomicalPart() ?: return@any false
                val core = CoreTracker(site, darkPart.start)
                intervalsAbove(darkPart, CORE_STEP, GalacticCentre.MIN_ALTITUDE_DEGREES) { core.altitude(it) }
                    .any { it.duration >= MIN_WINDOW }
            }
        }
    }

    private fun night(site: Observer, dark: DarkNight, fallbackStart: Instant): MilkyWayNight {
        val start = dark.sunset ?: dark.darkStart ?: fallbackStart
        val end = dark.sunrise ?: dark.darkEnd ?: start.plus(ONE_DAY)
        val span = TimeInterval(start, end)
        val twilight = Twilight.nightWindow(
            site = site,
            sunset = dark.sunset,
            sunrise = dark.sunrise,
            start = start.toAstronomyTime(),
            end = end.toAstronomyTime(),
            searchDays = SEARCH_DAYS,
        )
        val core = CoreTracker(site, start.plus(span.duration.dividedBy(2)))
        val coreAboveHorizon = intervalsAbove(span, CORE_STEP, threshold = 0.0) { core.altitude(it) }
        val coreUp = intervalsAbove(span, CORE_STEP, GalacticCentre.MIN_ALTITUDE_DEGREES) { core.altitude(it) }
        val moonUp = intervalsAbove(span, MOON_STEP, threshold = 0.0) { altitude(Body.Moon, site, it) }
        return MilkyWayNight(
            start = start,
            end = end,
            twilight = twilight,
            evening = Twilight.evening(site, twilight, end.toAstronomyTime(), SEARCH_DAYS),
            morning = Twilight.morning(site, twilight, start.toAstronomyTime(), SEARCH_DAYS),
            darkness = dark.darkness,
            moonIlluminatedFraction = dark.moonIlluminatedFraction,
            moonUp = moonUp,
            coreUp = coreUp,
            coreRise = coreAboveHorizon.firstOrNull()?.start?.takeIf { it.isAfter(start) },
            coreTransit = core.transit(span),
            coreSet = coreAboveHorizon.lastOrNull()?.end?.takeIf { it.isBefore(end) },
            window = window(dark, coreUp, moonUp, span, core),
        )
    }

    /**
     * The longest stretch with the Sun 18° down, the core up and the Moon down (or under
     * [GalacticCentre.MAX_MOON_FRACTION] lit); null when none lasts [MIN_WINDOW].
     */
    private fun window(
        dark: DarkNight,
        coreUp: List<TimeInterval>,
        moonUp: List<TimeInterval>,
        span: TimeInterval,
        core: CoreTracker,
    ): MilkyWayWindow? {
        val moonFree = if (dark.moonIlluminatedFraction < GalacticCentre.MAX_MOON_FRACTION) {
            listOf(span)
        } else {
            complement(moonUp, span)
        }
        val longest = intersect(intersect(coreUp, listOfNotNull(dark.astronomicalPart())), moonFree)
            .filter { it.duration >= MIN_WINDOW }
            .maxByOrNull { it.duration }
        return longest?.let {
            val best = core.highestPoint(it) ?: it.start
            MilkyWayWindow(start = it.start, end = it.end, best = best, coreAtBest = core.horizontal(best))
        }
    }

    /** Where the core is, with refraction, from its J2000 place precessed to [epoch]. */
    private class CoreTracker(private val site: Observer, epoch: Instant) {
        private val rightAscensionHours: Double
        private val declinationDegrees: Double

        init {
            val time = epoch.toAstronomyTime()
            val j2000 = Spherical(
                GalacticCentre.POSITION.declinationDegrees,
                GalacticCentre.POSITION.rightAscensionDegrees,
                1.0,
            ).toVector(time)
            val ofDate = rotationEqjEqd(time).rotate(j2000).toEquatorial()
            rightAscensionHours = ofDate.ra
            declinationDegrees = ofDate.dec
        }

        fun horizontal(at: Instant): HorizontalCoordinate {
            val time = at.toAstronomyTime()
            val topocentric = horizon(time, site, rightAscensionHours, declinationDegrees, Refraction.Normal)
            return HorizontalCoordinate(altitudeDegrees = topocentric.altitude, azimuthDegrees = topocentric.azimuth)
        }

        fun altitude(at: Instant): Double = horizontal(at).altitudeDegrees

        /** When the core culminates above the horizon within [interval]; null at either end of it. */
        fun transit(interval: TimeInterval): Instant? {
            val inside = TimeInterval(interval.start.plus(CORE_STEP), interval.end.minus(CORE_STEP))
            return highestPoint(interval)?.takeIf { it > inside.start && it < inside.end && altitude(it) > 0.0 }
        }

        /** When the core is highest within [interval]: a scan, then a ternary search around its top. */
        fun highestPoint(interval: TimeInterval): Instant? {
            if (!interval.end.isAfter(interval.start)) return null
            val samples = samples(interval, CORE_STEP)
            val top = samples.maxBy(::altitude)
            var low = maxOf(interval.start, top.minus(CORE_STEP))
            var high = minOf(interval.end, top.plus(CORE_STEP))
            while (Duration.between(low, high) > PRECISION) {
                val third = Duration.between(low, high).dividedBy(THIRDS)
                val left = low.plus(third)
                val right = high.minus(third)
                if (altitude(left) < altitude(right)) low = left else high = right
            }
            return low.plus(Duration.between(low, high).dividedBy(2))
        }
    }

    private companion object {
        const val SEARCH_DAYS = 1.1
        const val THIRDS = 3L
        val ONE_DAY: Duration = Duration.ofDays(1)
        val CORE_STEP: Duration = Duration.ofMinutes(5)
        val MOON_STEP: Duration = Duration.ofMinutes(10)
        val PRECISION: Duration = Duration.ofSeconds(15)

        /** Shorter than this is not worth setting up a tripod for. */
        val MIN_WINDOW: Duration = Duration.ofMinutes(15)

        /** Two nights sampled per month for the season. */
        val SEASON_SAMPLE_DAYS = listOf(1, 15)
        const val SEASON_SAMPLE_HOUR = 12

        /** The night's astronomically dark part; null when the Sun never gets 18° down. */
        fun DarkNight.astronomicalPart(): TimeInterval? {
            val from = darkStart ?: return null
            val until = darkEnd?.takeIf { it.isAfter(from) }
            return if (darkness == NightDarkness.ASTRONOMICAL && until != null) TimeInterval(from, until) else null
        }

        /**
         * The parts of [interval] where [value] is above [threshold], from a scan every [step]
         * refined by bisection to [PRECISION].
         */
        fun intervalsAbove(
            interval: TimeInterval,
            step: Duration,
            threshold: Double,
            value: (Instant) -> Double,
        ): List<TimeInterval> {
            val samples = samples(interval, step)
            val above = samples.map { value(it) > threshold }
            val result = mutableListOf<TimeInterval>()
            var openedAt: Instant? = if (above.first()) interval.start else null
            for (i in 1 until samples.size) {
                if (above[i] == above[i - 1]) continue
                val crossing = bisect(samples[i - 1], samples[i]) { value(it) > threshold == above[i - 1] }
                if (above[i]) {
                    openedAt = crossing
                } else {
                    openedAt?.let { result += TimeInterval(it, crossing) }
                    openedAt = null
                }
            }
            openedAt?.let { result += TimeInterval(it, interval.end) }
            return result
        }

        /** The moment between [from] and [until] where [isLikeStart] stops holding. */
        fun bisect(from: Instant, until: Instant, isLikeStart: (Instant) -> Boolean): Instant {
            var low = from
            var high = until
            while (Duration.between(low, high) > PRECISION) {
                val middle = low.plus(Duration.between(low, high).dividedBy(2))
                if (isLikeStart(middle)) low = middle else high = middle
            }
            return low.plus(Duration.between(low, high).dividedBy(2))
        }

        /** [interval]'s start, every [step] after it, and its end. */
        fun samples(interval: TimeInterval, step: Duration): List<Instant> =
            generateSequence(interval.start) { it.plus(step) }
                .takeWhile { it.isBefore(interval.end) }
                .plus(interval.end)
                .toList()

        fun intersect(first: List<TimeInterval>, second: List<TimeInterval>): List<TimeInterval> =
            first.flatMap { a ->
                second.mapNotNull { b ->
                    val start = maxOf(a.start, b.start)
                    val end = minOf(a.end, b.end)
                    if (end.isAfter(start)) TimeInterval(start, end) else null
                }
            }

        /** The parts of [within] outside every one of [intervals] (sorted, not overlapping). */
        fun complement(intervals: List<TimeInterval>, within: TimeInterval): List<TimeInterval> {
            val result = mutableListOf<TimeInterval>()
            var cursor = within.start
            intervals.forEach { interval ->
                if (interval.start.isAfter(cursor)) result += TimeInterval(cursor, minOf(interval.start, within.end))
                cursor = maxOf(cursor, interval.end)
            }
            if (within.end.isAfter(cursor)) result += TimeInterval(cursor, within.end)
            return result.filter { it.end.isAfter(it.start) }
        }
    }
}
