package com.radustavila.nasaexplorer.core.skymap

import com.radustavila.nasaexplorer.core.model.EquatorialCoordinate
import com.radustavila.nasaexplorer.core.model.GeoCoordinate
import com.radustavila.nasaexplorer.core.model.HorizontalCoordinate
import java.time.Instant
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/**
 * From the catalogue's J2000 coordinates to the observer's sky, following Meeus, Astronomical
 * Algorithms (2nd ed.): mean sidereal time (12.4), IAU 1976 precession (21.2 to 21.4) and the
 * equatorial to horizontal rotation (13.5, 13.6).
 *
 * Nutation, aberration, proper motion and UT1 - UTC are left out: together they move a star by
 * well under a hundredth of a degree in this century, far below what a phone's compass resolves.
 */
@Suppress("MagicNumber")
object CelestialFrames {

    private const val JULIAN_DATE_UNIX_EPOCH = 2440587.5
    private const val JULIAN_DATE_J2000 = 2451545.0
    private const val DAYS_PER_CENTURY = 36525.0
    private const val MILLIS_PER_DAY = 86_400_000.0
    private const val ARCSECONDS_PER_DEGREE = 3600.0
    private const val FULL_TURN = 360.0

    fun julianDate(at: Instant): Double = JULIAN_DATE_UNIX_EPOCH + at.toEpochMilli() / MILLIS_PER_DAY

    /** Greenwich mean sidereal time, degrees in 0 until 360 (Meeus 12.4). */
    fun greenwichMeanSiderealTime(at: Instant): Double {
        val days = julianDate(at) - JULIAN_DATE_J2000
        val t = days / DAYS_PER_CENTURY
        val degrees = 280.46061837 + 360.98564736629 * days + 0.000387933 * t * t - t * t * t / 38_710_000.0
        return degrees.mod(FULL_TURN)
    }

    /** Local mean sidereal time at [longitude] (east positive), degrees. */
    fun localSiderealTime(at: Instant, longitude: Double): Double =
        (greenwichMeanSiderealTime(at) + longitude).mod(FULL_TURN)

    /** Rotates J2000 mean equatorial vectors to the mean equator and equinox of [at] (IAU 1976). */
    fun precession(at: Instant): RotationMatrix {
        val t = (julianDate(at) - JULIAN_DATE_J2000) / DAYS_PER_CENTURY
        val zeta = arcseconds(2306.2181 * t + 0.30188 * t * t + 0.017998 * t * t * t)
        val z = arcseconds(2306.2181 * t + 1.09468 * t * t + 0.018203 * t * t * t)
        val theta = arcseconds(2004.3109 * t - 0.42665 * t * t - 0.041833 * t * t * t)
        val cosZeta = cos(zeta)
        val sinZeta = sin(zeta)
        val cosZ = cos(z)
        val sinZ = sin(z)
        val cosTheta = cos(theta)
        val sinTheta = sin(theta)
        return RotationMatrix(
            doubleArrayOf(
                cosZeta * cosTheta * cosZ - sinZeta * sinZ,
                -sinZeta * cosTheta * cosZ - cosZeta * sinZ,
                -sinTheta * cosZ,
                cosZeta * cosTheta * sinZ + sinZeta * cosZ,
                -sinZeta * cosTheta * sinZ + cosZeta * cosZ,
                -sinTheta * sinZ,
                cosZeta * sinTheta,
                -sinZeta * sinTheta,
                cosTheta,
            ),
        )
    }

    /**
     * Rotates equatorial vectors of date into [observer]'s horizontal frame (east, north, up) at
     * [at]: first by the local sidereal time into hour-angle axes, then by the latitude.
     */
    fun ofDateToHorizontal(observer: GeoCoordinate, at: Instant): RotationMatrix =
        latitudeRotation(observer.latitude) * siderealRotation(localSiderealTime(at, observer.longitude))

    /** Rotates J2000 equatorial vectors (the catalogue's) into [observer]'s horizontal frame at [at]. */
    fun j2000ToHorizontal(observer: GeoCoordinate, at: Instant): RotationMatrix =
        ofDateToHorizontal(observer, at) * precession(at)

    /**
     * Altitude and azimuth of a direction with [hourAngle] (degrees, positive west of the meridian)
     * and [declination], seen from [latitude].
     */
    fun horizontal(hourAngle: Double, declination: Double, latitude: Double): HorizontalCoordinate {
        // A zero sidereal time makes the right ascension the negated hour angle.
        val equatorial = SkyVector.of(EquatorialCoordinate((-hourAngle).mod(FULL_TURN), declination))
        return (latitudeRotation(latitude) * equatorial).toHorizontal()
    }

    /**
     * Atmospheric refraction (Saemundsson's formula, Meeus 16.4) for a geometric altitude, degrees:
     * about 0.48 at the horizon, 0.03 at 30. It fades out below the horizon, where
     * nothing is seen anyway, so objects setting move smoothly.
     */
    fun refraction(geometricAltitude: Double): Double {
        val altitude = geometricAltitude.coerceAtLeast(REFRACTION_FADE_END)
        val arcminutes = 1.02 / tan(Math.toRadians(altitude + 10.3 / (altitude + 5.11))) + 0.0019279
        val degrees = (arcminutes / 60.0).coerceAtLeast(0.0)
        val fade = (geometricAltitude - REFRACTION_FADE_START) / (REFRACTION_FADE_END - REFRACTION_FADE_START)
        return degrees * fade.coerceIn(0.0, 1.0)
    }

    private const val REFRACTION_FADE_START = -5.0
    private const val REFRACTION_FADE_END = -1.0

    private fun arcseconds(value: Double): Double = Math.toRadians(value / ARCSECONDS_PER_DEGREE)

    /** Equatorial axes to hour-angle axes: x on the meridian, y to the east, z to the pole. */
    private fun siderealRotation(siderealTime: Double): RotationMatrix {
        val angle = Math.toRadians(siderealTime)
        val c = cos(angle)
        val s = sin(angle)
        return RotationMatrix(doubleArrayOf(c, s, 0.0, -s, c, 0.0, 0.0, 0.0, 1.0))
    }

    /** Hour-angle axes to east, north, up. */
    private fun latitudeRotation(latitude: Double): RotationMatrix {
        val angle = Math.toRadians(latitude)
        val c = cos(angle)
        val s = sin(angle)
        return RotationMatrix(doubleArrayOf(0.0, 1.0, 0.0, -s, 0.0, c, c, 0.0, s))
    }
}
