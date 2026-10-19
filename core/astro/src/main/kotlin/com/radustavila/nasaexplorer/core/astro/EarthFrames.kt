package com.radustavila.nasaexplorer.core.astro

import com.radustavila.nasaexplorer.core.model.GeoCoordinate
import java.time.Instant
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Look angles from an observer to a satellite, degrees. */
internal data class LookAngles(val elevationDegrees: Double, val azimuthDegrees: Double)

/**
 * The few frame conversions satellite predictions need: TEME (where SGP4's output lives) to the
 * Earth-fixed frame through Greenwich mean sidereal time, and WGS-84 geodetic coordinates.
 *
 * Polar motion and UT1 - UTC (under a second) are ignored; together they move a pass by well
 * under a second.
 */
@Suppress("MagicNumber")
internal object EarthFrames {

    private const val WGS84_A_KM = 6378.137
    private const val WGS84_F = 1.0 / 298.257223563
    private const val WGS84_E2 = WGS84_F * (2.0 - WGS84_F)
    private const val TWO_PI = 2.0 * PI
    private const val JULIAN_DATE_UNIX_EPOCH = 2440587.5
    private const val JULIAN_DATE_J2000 = 2451545.0
    private const val MILLIS_PER_DAY = 86_400_000.0

    fun julianDate(at: Instant): Double = JULIAN_DATE_UNIX_EPOCH + at.toEpochMilli() / MILLIS_PER_DAY

    /** Greenwich mean sidereal time (IAU 1982), radians, as Vallado's `gstime`. */
    fun gmst(at: Instant): Double {
        val tut1 = (julianDate(at) - JULIAN_DATE_J2000) / 36525.0
        var seconds = -6.2e-6 * tut1 * tut1 * tut1 + 0.093104 * tut1 * tut1 +
            (876600.0 * 3600.0 + 8640184.812866) * tut1 + 67310.54841
        seconds = (Math.toRadians(seconds) / 240.0).rem(TWO_PI)
        return if (seconds < 0.0) seconds + TWO_PI else seconds
    }

    /** Rotates a TEME vector into the Earth-fixed frame. */
    fun temeToEcef(teme: Vector3, at: Instant): Vector3 {
        val theta = gmst(at)
        val c = cos(theta)
        val s = sin(theta)
        return Vector3(c * teme.x + s * teme.y, -s * teme.x + c * teme.y, teme.z)
    }

    fun geodeticToEcef(coordinate: GeoCoordinate, heightKm: Double = 0.0): Vector3 {
        val lat = Math.toRadians(coordinate.latitude)
        val lon = Math.toRadians(coordinate.longitude)
        val n = WGS84_A_KM / sqrt(1.0 - WGS84_E2 * sin(lat) * sin(lat))
        return Vector3(
            (n + heightKm) * cos(lat) * cos(lon),
            (n + heightKm) * cos(lat) * sin(lon),
            (n * (1.0 - WGS84_E2) + heightKm) * sin(lat),
        )
    }

    /** Sub-point and height above the WGS-84 ellipsoid of an Earth-fixed position. */
    fun ecefToGeodetic(ecef: Vector3): Pair<GeoCoordinate, Double> {
        val lon = atan2(ecef.y, ecef.x)
        val p = sqrt(ecef.x * ecef.x + ecef.y * ecef.y)
        var lat = atan2(ecef.z, p * (1.0 - WGS84_E2))
        var height = 0.0
        repeat(6) {
            val n = WGS84_A_KM / sqrt(1.0 - WGS84_E2 * sin(lat) * sin(lat))
            height = p / cos(lat) - n
            lat = atan2(ecef.z, p * (1.0 - WGS84_E2 * n / (n + height)))
        }
        val coordinate = GeoCoordinate(
            latitude = Math.toDegrees(lat).coerceIn(-90.0, 90.0),
            longitude = Math.toDegrees(lon).coerceIn(-180.0, 180.0),
        )
        return coordinate to height
    }

    /** Geometric elevation and azimuth of an Earth-fixed [target] seen from [observer]. */
    fun lookAngles(observer: GeoCoordinate, observerEcef: Vector3, target: Vector3): LookAngles {
        val lat = Math.toRadians(observer.latitude)
        val lon = Math.toRadians(observer.longitude)
        val range = target - observerEcef
        // East-north-up components of the range vector.
        val east = -sin(lon) * range.x + cos(lon) * range.y
        val north = -sin(lat) * cos(lon) * range.x - sin(lat) * sin(lon) * range.y + cos(lat) * range.z
        val up = cos(lat) * cos(lon) * range.x + cos(lat) * sin(lon) * range.y + sin(lat) * range.z
        val elevation = Math.toDegrees(asin(up / range.length))
        var azimuth = Math.toDegrees(atan2(east, north))
        if (azimuth < 0.0) azimuth += 360.0
        return LookAngles(elevation, azimuth)
    }
}
