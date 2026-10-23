package com.radustavila.nasaexplorer.core.astro

import com.radustavila.nasaexplorer.core.domain.sky.CometSkyCalculator
import com.radustavila.nasaexplorer.core.model.Comet
import com.radustavila.nasaexplorer.core.model.CometEphemerisPoint
import com.radustavila.nasaexplorer.core.model.CometLocalSky
import com.radustavila.nasaexplorer.core.model.CometSkyPosition
import com.radustavila.nasaexplorer.core.model.CometTonight
import com.radustavila.nasaexplorer.core.model.CometVisibility
import com.radustavila.nasaexplorer.core.model.EquatorialCoordinate
import com.radustavila.nasaexplorer.core.model.GeoCoordinate
import com.radustavila.nasaexplorer.core.model.HorizontalCoordinate
import io.github.cosinekitty.astronomy.Body
import io.github.cosinekitty.astronomy.Observer
import io.github.cosinekitty.astronomy.Refraction
import io.github.cosinekitty.astronomy.Spherical
import io.github.cosinekitty.astronomy.constellation
import io.github.cosinekitty.astronomy.horizon
import io.github.cosinekitty.astronomy.rotationEqjEqd
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * [CometSkyCalculator] on Horizons' daily geocentric ephemeris and Astronomy Engine.
 *
 * Between two daily points the direction is interpolated on the unit sphere and the magnitude
 * linearly; parallax is ignored (under 0.03 degrees beyond 0.1 au). The J2000 direction is turned
 * into the equator of date before Astronomy Engine places it in the observer's sky, with standard
 * refraction for Sky tonight and the comets screens and without it for the sky map. Rise, set and
 * the best time come from a 10-minute scan over the next day, refined to a few seconds for rise
 * and set.
 */
class AstronomyEngineCometSkyCalculator @Inject constructor() : CometSkyCalculator {

    override fun tonight(comet: Comet, observer: GeoCoordinate?, now: Instant): CometTonight? {
        val track = comet.ephemeris
        val point = track.pointAt(now) ?: return null
        val peak = track.filter { it.magnitude != null }.minByOrNull { checkNotNull(it.magnitude) }
        return CometTonight(
            comet = comet,
            computedAt = now,
            magnitude = point.magnitude,
            visibility = CometVisibility.fromMagnitude(point.magnitude),
            position = point.position,
            constellation = constellation(
                point.position.rightAscensionDegrees / DEGREES_PER_HOUR,
                point.position.declinationDegrees,
            ).name,
            sunDistanceAu = point.sunDistanceAu,
            earthDistanceAu = point.earthDistanceAu,
            peakMagnitude = peak?.magnitude,
            peakTime = peak?.time,
            sky = observer?.let { localSky(track, Observer(it.latitude, it.longitude, 0.0), now) },
        )
    }

    override fun skyPosition(comet: Comet, observer: GeoCoordinate, at: Instant): CometSkyPosition? {
        val point = comet.ephemeris.pointAt(at) ?: return null
        val site = Observer(observer.latitude, observer.longitude, 0.0)
        return CometSkyPosition(
            designation = comet.designation,
            name = comet.name,
            horizontal = horizontal(point.position, site, at, Refraction.None),
            equatorial = point.position,
            magnitude = point.magnitude,
        )
    }

    private fun localSky(track: List<CometEphemerisPoint>, site: Observer, now: Instant): CometLocalSky {
        val altitudeAt = { at: Instant -> horizontal(track.clampedPointAt(at).position, site, at).altitudeDegrees }
        val samples = generateSequence(now) { it.plus(SCAN_STEP) }
            .takeWhile { !it.isAfter(now.plus(SCAN_SPAN)) }
            .map { at -> Sample(at, altitudeAt(at), altitude(Body.Sun, site, at)) }
            .toList()
        val crossings = samples.zipWithNext()
        val rise = crossings.firstOrNull { (a, b) -> a.altitude <= 0.0 && b.altitude > 0.0 }
            ?.let { (a, b) -> refineCrossing(a.at, b.at, altitudeAt) }
        val set = crossings.firstOrNull { (a, b) -> a.altitude > 0.0 && b.altitude <= 0.0 }
            ?.let { (a, b) -> refineCrossing(a.at, b.at, altitudeAt) }
        val best = firstDarkStretch(samples)
            .maxByOrNull { it.altitude }
            ?.takeIf { it.altitude >= CometLocalSky.MIN_VISIBLE_ALTITUDE }
        return CometLocalSky(
            now = horizontal(track.clampedPointAt(now).position, site, now),
            rise = rise,
            set = set,
            isAlwaysUp = samples.all { it.altitude > 0.0 },
            bestTime = best?.at,
            best = best?.let { horizontal(track.clampedPointAt(it.at).position, site, it.at) },
        )
    }

    /** The samples of the night under way, or of the next one: the first unbroken run with the Sun 12 degrees down. */
    private fun firstDarkStretch(samples: List<Sample>): List<Sample> =
        samples.dropWhile { it.sunAltitude > DARK_SUN_ALTITUDE }.takeWhile { it.sunAltitude <= DARK_SUN_ALTITUDE }

    /** Bisects between [from] (on one side of the horizon) and [until] (on the other). */
    private fun refineCrossing(from: Instant, until: Instant, altitudeAt: (Instant) -> Double): Instant {
        var low = from
        var high = until
        val lowIsUp = altitudeAt(low) > 0.0
        while (Duration.between(low, high) > REFINE_PRECISION) {
            val middle = low.plus(Duration.between(low, high).dividedBy(2))
            if ((altitudeAt(middle) > 0.0) == lowIsUp) low = middle else high = middle
        }
        return high
    }

    /** Altitude and azimuth of a J2000 direction, with standard refraction unless told otherwise. */
    private fun horizontal(
        position: EquatorialCoordinate,
        site: Observer,
        at: Instant,
        refraction: Refraction = Refraction.Normal,
    ): HorizontalCoordinate {
        val time = at.toAstronomyTime()
        val j2000 = Spherical(position.declinationDegrees, position.rightAscensionDegrees, 1.0).toVector(time)
        val ofDate = rotationEqjEqd(time).rotate(j2000).toEquatorial()
        val topocentric = horizon(time, site, ofDate.ra, ofDate.dec, refraction)
        return HorizontalCoordinate(altitudeDegrees = topocentric.altitude, azimuthDegrees = topocentric.azimuth)
    }

    private data class Sample(val at: Instant, val altitude: Double, val sunAltitude: Double)

    private companion object {
        const val DEGREES_PER_HOUR = 15.0
        const val DARK_SUN_ALTITUDE = -12.0
        val SCAN_STEP: Duration = Duration.ofMinutes(10)
        val SCAN_SPAN: Duration = Duration.ofDays(1)
        val REFINE_PRECISION: Duration = Duration.ofSeconds(10)
    }
}

/** The ephemeris at [at], or null outside it. Points are assumed oldest first. */
internal fun List<CometEphemerisPoint>.pointAt(at: Instant): CometEphemerisPoint? {
    val nextIndex = indexOfFirst { !it.time.isBefore(at) }
    return when {
        isEmpty() || at.isBefore(first().time) || at.isAfter(last().time) -> null
        nextIndex == 0 -> first()
        else -> interpolate(this[nextIndex - 1], this[nextIndex], at)
    }
}

/** As [pointAt], holding the first or last point outside the ephemeris (for a scan past its end). */
internal fun List<CometEphemerisPoint>.clampedPointAt(at: Instant): CometEphemerisPoint = when {
    at.isBefore(first().time) -> first()
    at.isAfter(last().time) -> last()
    else -> checkNotNull(pointAt(at))
}

private fun interpolate(before: CometEphemerisPoint, after: CometEphemerisPoint, at: Instant): CometEphemerisPoint {
    val span = Duration.between(before.time, after.time).toMillis().toDouble()
    val fraction = if (span <= 0.0) 0.0 else Duration.between(before.time, at).toMillis() / span
    fun lerp(a: Double, b: Double) = a + (b - a) * fraction
    val beforeMagnitude = before.magnitude
    val afterMagnitude = after.magnitude
    val magnitude = if (beforeMagnitude != null && afterMagnitude != null) {
        lerp(beforeMagnitude, afterMagnitude)
    } else {
        beforeMagnitude ?: afterMagnitude
    }
    return CometEphemerisPoint(
        time = at,
        position = blendDirection(before.position, after.position, fraction),
        magnitude = magnitude,
        sunDistanceAu = lerp(before.sunDistanceAu, after.sunDistanceAu),
        earthDistanceAu = lerp(before.earthDistanceAu, after.earthDistanceAu),
    )
}

/** Between two directions along the sphere (a normalised linear blend: a day's motion is a few degrees). */
private fun blendDirection(a: EquatorialCoordinate, b: EquatorialCoordinate, fraction: Double): EquatorialCoordinate {
    val (ax, ay, az) = a.unitVector()
    val (bx, by, bz) = b.unitVector()
    val x = ax + (bx - ax) * fraction
    val y = ay + (by - ay) * fraction
    val z = az + (bz - az) * fraction
    val length = sqrt(x * x + y * y + z * z)
    val rightAscension = Math.toDegrees(atan2(y, x)).let { if (it < 0) it + FULL_TURN else it }
    return EquatorialCoordinate(
        rightAscensionDegrees = rightAscension,
        declinationDegrees = Math.toDegrees(asin((z / length).coerceIn(-1.0, 1.0))),
    )
}

private fun EquatorialCoordinate.unitVector(): Triple<Double, Double, Double> {
    val ra = Math.toRadians(rightAscensionDegrees)
    val dec = Math.toRadians(declinationDegrees)
    return Triple(cos(dec) * cos(ra), cos(dec) * sin(ra), sin(dec))
}

private const val FULL_TURN = 360.0
