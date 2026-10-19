package com.radustavila.nasaexplorer.core.model

import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin

/**
 * Which Kp brings the aurora to a place.
 *
 * The auroral oval sits around the geomagnetic pole, not the geographic one, so the place is first
 * converted to geomagnetic latitude (centred dipole, IGRF-14 pole for 2025: 80.8° N, 72.6° W). The
 * oval's equatorward edge then follows the usual Kp table: about 66.5° geomagnetic at Kp 0, moving
 * about two degrees towards the equator per Kp step, down to 48° at Kp 9.
 */
object AuroraVisibility {

    private const val POLE_LATITUDE = 80.8
    private const val POLE_LONGITUDE = -72.6

    /** Geomagnetic latitude of the oval's equatorward edge, indexed by Kp 0..9. */
    private val EDGE_BY_KP = doubleArrayOf(66.5, 64.5, 62.4, 60.4, 58.3, 56.3, 54.2, 52.2, 50.1, 48.1)

    fun geomagneticLatitude(latitude: Double, longitude: Double): Double {
        val lat = Math.toRadians(latitude)
        val poleLat = Math.toRadians(POLE_LATITUDE)
        val deltaLon = Math.toRadians(longitude - POLE_LONGITUDE)
        val sinMagLat = sin(lat) * sin(poleLat) + cos(lat) * cos(poleLat) * cos(deltaLon)
        return Math.toDegrees(asin(sinMagLat.coerceIn(-1.0, 1.0)))
    }

    /**
     * The smallest whole Kp at which the oval reaches the place, in either hemisphere; null when even
     * Kp 9 stays poleward of it.
     */
    fun minimumKp(latitude: Double, longitude: Double): Int? {
        val magneticLatitude = abs(geomagneticLatitude(latitude, longitude))
        return EDGE_BY_KP.indices.firstOrNull { kp -> magneticLatitude >= EDGE_BY_KP[kp] }
    }

    /** Geomagnetic latitude the oval reaches at [kp], for captions ("Kp 7 reaches about 52°"). */
    fun edgeLatitude(kp: Int): Double = EDGE_BY_KP[kp.coerceIn(0, Kp.MAX)]
}
