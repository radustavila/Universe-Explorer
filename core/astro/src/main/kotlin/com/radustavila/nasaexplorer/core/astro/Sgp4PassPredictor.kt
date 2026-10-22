package com.radustavila.nasaexplorer.core.astro

import com.radustavila.nasaexplorer.core.domain.sky.SatellitePassPredictor
import com.radustavila.nasaexplorer.core.model.GeoCoordinate
import com.radustavila.nasaexplorer.core.model.OrbitalElements
import com.radustavila.nasaexplorer.core.model.SatellitePass
import com.radustavila.nasaexplorer.core.model.SatellitePosition
import io.github.cosinekitty.astronomy.Aberration
import io.github.cosinekitty.astronomy.Body
import io.github.cosinekitty.astronomy.EquatorEpoch
import io.github.cosinekitty.astronomy.Observer
import io.github.cosinekitty.astronomy.Refraction
import io.github.cosinekitty.astronomy.Time
import io.github.cosinekitty.astronomy.equator
import io.github.cosinekitty.astronomy.geoVector
import io.github.cosinekitty.astronomy.horizon
import java.time.Duration
import java.time.Instant
import javax.inject.Inject

/**
 * Satellite passes found by propagating SGP4 in short steps and refining every horizon crossing
 * by bisection; visibility from the Sun's position (Astronomy Engine) and a cylindrical model of
 * the Earth's shadow.
 */
class Sgp4PassPredictor @Inject constructor() : SatellitePassPredictor {

    override fun passes(
        elements: OrbitalElements,
        observer: GeoCoordinate,
        from: Instant,
        to: Instant,
        minElevationDegrees: Double,
    ): List<SatellitePass> = scan(elements, observer, from, to, minElevationDegrees, skipFarBelow = false)

    /**
     * With [skipFarBelow], the step grows while the satellite is far below the threshold: seen from
     * the ground, a satellite under the horizon climbs at most [MAX_ELEVATION_RATE], so a pass
     * cannot begin before the gap could have closed at that rate.
     */
    private fun scan(
        elements: OrbitalElements,
        observer: GeoCoordinate,
        from: Instant,
        to: Instant,
        minElevationDegrees: Double,
        skipFarBelow: Boolean,
    ): List<SatellitePass> {
        val sgp4 = elements.propagatorOrNull() ?: return emptyList()
        val observerEcef = EarthFrames.geodeticToEcef(observer)
        val aboveThreshold: (Instant) -> Double = { at ->
            sgp4.lookAngles(observer, observerEcef, at).elevationDegrees - minElevationDegrees
        }

        val passes = mutableListOf<SatellitePass>()
        // Start early enough to catch the rise of a pass already under way at [from], and run past
        // [to] to find the end of one that peaks just before it.
        var previous = from.minus(MAX_PASS_DURATION)
        val scanEnd = to.plus(MAX_PASS_DURATION)
        var previousValue = aboveThreshold(previous)
        var rise: Instant? = null
        try {
            while (previous.isBefore(scanEnd)) {
                val current = previous.plus(if (skipFarBelow) stepBelow(previousValue) else SCAN_STEP)
                val value = aboveThreshold(current)
                if (previousValue < 0.0 && value >= 0.0) {
                    rise = crossing(previous, current, aboveThreshold)
                } else if (previousValue >= 0.0 && value < 0.0 && rise != null) {
                    val set = crossing(previous, current, aboveThreshold)
                    val pass = buildPass(sgp4, observer, observerEcef, rise, set)
                    if (pass.end.isAfter(from) && !pass.peak.isAfter(to)) passes += pass
                    rise = null
                }
                previous = current
                previousValue = value
            }
        } catch (_: IllegalStateException) {
            // The elements propagate into a decayed or degenerate orbit: keep what was found.
        }
        return passes
    }

    /**
     * Searches only the dark hours (Sun at least 6 degrees down, padded by the longest pass), found
     * once for every satellite: nothing is visible in daylight, and most of a day is daylight or
     * twilight, which makes a set of 150 satellites a few times cheaper.
     */
    override fun visiblePasses(
        elements: List<OrbitalElements>,
        observer: GeoCoordinate,
        from: Instant,
        to: Instant,
        minElevationDegrees: Double,
    ): Map<Int, List<SatellitePass>> {
        val darkWindows = darkWindows(observer, from, to)
        return elements.associate { satellite ->
            satellite.noradId to darkWindows.flatMap { (start, end) ->
                scan(satellite, observer, start, end, minElevationDegrees, skipFarBelow = true)
            }.filter(SatellitePass::isVisible)
        }
    }

    /**
     * Spans between [from] and [to] (each widened by [MAX_PASS_DURATION]) where the Sun is at
     * least 6 degrees down at some sample, so a pass that starts in twilight and peaks in the dark
     * is still found.
     */
    private fun darkWindows(observer: GeoCoordinate, from: Instant, to: Instant): List<Pair<Instant, Instant>> {
        val windows = mutableListOf<Pair<Instant, Instant>>()
        var windowStart: Instant? = null
        var at = from
        while (!at.isAfter(to)) {
            val isDark = sunAltitude(observer, at) <= DARK_SKY_SUN_ALTITUDE + DARK_SAMPLE_MARGIN
            if (isDark && windowStart == null) windowStart = maxOf(from, at.minus(DARK_SAMPLE_STEP))
            if (!isDark && windowStart != null) {
                windows.addMerged(windowStart, minOf(to, at.plus(MAX_PASS_DURATION)))
                windowStart = null
            }
            at = at.plus(DARK_SAMPLE_STEP)
        }
        windowStart?.let { windows.addMerged(it, to) }
        return windows
    }

    /** Joins a window that overlaps the previous one, so no pass is found twice. */
    private fun MutableList<Pair<Instant, Instant>>.addMerged(start: Instant, end: Instant) {
        val last = lastOrNull()
        if (last != null && !start.isAfter(last.second)) {
            set(lastIndex, last.first to maxOf(last.second, end))
        } else {
            add(start to end)
        }
    }

    override fun position(elements: OrbitalElements, at: Instant): SatellitePosition? {
        val sgp4 = elements.propagatorOrNull() ?: return null
        return try {
            sgp4.subPoint(at, sunDirection(at))
        } catch (_: IllegalStateException) {
            null
        }
    }

    override fun groundTrack(
        elements: OrbitalElements,
        from: Instant,
        to: Instant,
        step: Duration,
    ): List<SatellitePosition> {
        val sgp4 = elements.propagatorOrNull() ?: return emptyList()
        require(!step.isNegative && !step.isZero) { "step must be positive" }
        // The Sun moves a quarter of a degree in the track's span: one direction is enough.
        val sun = sunDirection(from)
        val track = mutableListOf<SatellitePosition>()
        var at = from
        try {
            while (!at.isAfter(to)) {
                track += sgp4.subPoint(at, sun)
                at = at.plus(step)
            }
        } catch (_: IllegalStateException) {
            // Decayed partway through: return the track up to that point.
        }
        return track
    }

    private fun buildPass(
        sgp4: Sgp4,
        observer: GeoCoordinate,
        observerEcef: Vector3,
        rise: Instant,
        set: Instant,
    ): SatellitePass {
        val peak = peakOf(rise, set) { sgp4.lookAngles(observer, observerEcef, it).elevationDegrees }
        return SatellitePass(
            start = rise,
            peak = peak,
            end = set,
            maxElevationDegrees = sgp4.lookAngles(observer, observerEcef, peak).elevationDegrees,
            startAzimuthDegrees = sgp4.lookAngles(observer, observerEcef, rise).azimuthDegrees,
            endAzimuthDegrees = sgp4.lookAngles(observer, observerEcef, set).azimuthDegrees,
            isVisible = isVisible(sgp4, observer, rise, set),
        )
    }

    /**
     * Visible when, at some moment of the pass, the observer's sky is dark (Sun at least 6 degrees
     * down) while the satellite is still in sunlight.
     */
    private fun isVisible(sgp4: Sgp4, observer: GeoCoordinate, rise: Instant, set: Instant): Boolean {
        val sun = sunDirection(rise)
        var at = rise
        while (!at.isAfter(set)) {
            if (sunAltitude(observer, at) <= DARK_SKY_SUN_ALTITUDE && sgp4.isSunlit(at, sun)) return true
            at = at.plus(VISIBILITY_STEP)
        }
        return sunAltitude(observer, set) <= DARK_SKY_SUN_ALTITUDE && sgp4.isSunlit(set, sun)
    }

    private companion object {
        val SCAN_STEP: Duration = Duration.ofSeconds(20)
        val VISIBILITY_STEP: Duration = Duration.ofSeconds(30)
        val MAX_PASS_DURATION: Duration = Duration.ofMinutes(20)

        /** Faster than a low satellite rises at the horizon (about 12 degrees a minute), for margin. */
        const val MAX_ELEVATION_RATE = 20.0
        val MAX_SKIP_STEP: Duration = Duration.ofMinutes(5)

        /** [SCAN_STEP] near or above the threshold; longer the further below it [degreesAbove] is. */
        fun stepBelow(degreesAbove: Double): Duration {
            if (degreesAbove >= 0.0) return SCAN_STEP
            val millis = (-degreesAbove / MAX_ELEVATION_RATE * MILLIS_PER_MINUTE).toLong()
            return Duration.ofMillis(millis.coerceIn(SCAN_STEP.toMillis(), MAX_SKIP_STEP.toMillis()))
        }

        const val MILLIS_PER_MINUTE = 60_000.0

        /** The Sun moves about a degree in this time, which [DARK_SAMPLE_MARGIN] covers. */
        val DARK_SAMPLE_STEP: Duration = Duration.ofMinutes(5)
        const val DARK_SAMPLE_MARGIN = 1.5
        val CROSSING_PRECISION: Duration = Duration.ofMillis(100)
        const val DARK_SKY_SUN_ALTITUDE = -6.0
        const val EARTH_RADIUS_KM = 6378.137
        const val SECONDS_PER_HOUR = 3600.0
        const val GOLDEN_RATIO_CONJUGATE = 0.6180339887498949

        fun OrbitalElements.propagatorOrNull(): Sgp4? = try {
            Sgp4(this)
        } catch (_: IllegalArgumentException) {
            null
        }

        fun Sgp4.lookAngles(observer: GeoCoordinate, observerEcef: Vector3, at: Instant): LookAngles =
            EarthFrames.lookAngles(observer, observerEcef, EarthFrames.temeToEcef(propagate(at).position, at))

        fun Sgp4.subPoint(at: Instant, sun: Vector3): SatellitePosition {
            val state = propagate(at)
            val (coordinate, height) = EarthFrames.ecefToGeodetic(EarthFrames.temeToEcef(state.position, at))
            return SatellitePosition(
                coordinate = coordinate,
                altitudeKm = height,
                speedKmPerHour = state.velocity.length * SECONDS_PER_HOUR,
                isSunlit = isSunlit(state.position, sun),
                at = at,
                isLive = false,
            )
        }

        fun Sgp4.isSunlit(at: Instant, sun: Vector3): Boolean = isSunlit(propagate(at).position, sun)

        /** Cylindrical shadow: lit unless behind the Earth and within one Earth radius of the axis. */
        fun isSunlit(position: Vector3, sun: Vector3): Boolean {
            val along = position dot sun
            if (along >= 0.0) return true
            return (position - sun * along).length > EARTH_RADIUS_KM
        }

        /** Unit vector towards the Sun in J2000 equatorial axes, which TEME matches to a fraction of a degree. */
        fun sunDirection(at: Instant): Vector3 {
            val vector = geoVector(Body.Sun, at.toAstronomyTime(), Aberration.None)
            val sun = Vector3(vector.x, vector.y, vector.z)
            return sun * (1.0 / sun.length)
        }

        fun sunAltitude(observer: GeoCoordinate, at: Instant): Double {
            val time = at.toAstronomyTime()
            val site = Observer(observer.latitude, observer.longitude, 0.0)
            val sun = equator(Body.Sun, time, site, EquatorEpoch.OfDate, Aberration.Corrected)
            return horizon(time, site, sun.ra, sun.dec, Refraction.None).altitude
        }

        /** Bisects the sign change of [f] between [before] and [after]. */
        fun crossing(before: Instant, after: Instant, f: (Instant) -> Double): Instant {
            var low = before
            var high = after
            val lowSign = f(low) >= 0.0
            while (Duration.between(low, high) > CROSSING_PRECISION) {
                val middle = low.plus(Duration.between(low, high).dividedBy(2))
                if ((f(middle) >= 0.0) == lowSign) low = middle else high = middle
            }
            return low.plus(Duration.between(low, high).dividedBy(2))
        }

        /** Golden-section search for the maximum of a pass's elevation, which has a single peak. */
        fun peakOf(start: Instant, end: Instant, elevation: (Instant) -> Double): Instant {
            var low = start.toEpochMilli().toDouble()
            var high = end.toEpochMilli().toDouble()
            val at: (Double) -> Double = { millis -> elevation(Instant.ofEpochMilli(millis.toLong())) }
            while (high - low > CROSSING_PRECISION.toMillis()) {
                val left = high - GOLDEN_RATIO_CONJUGATE * (high - low)
                val right = low + GOLDEN_RATIO_CONJUGATE * (high - low)
                if (at(left) < at(right)) low = left else high = right
            }
            return Instant.ofEpochMilli(((low + high) / 2).toLong())
        }
    }
}

internal fun Instant.toAstronomyTime(): Time = Time.fromMillisecondsSince1970(toEpochMilli())
