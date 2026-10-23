package com.radustavila.nasaexplorer.core.astro

import com.radustavila.nasaexplorer.core.domain.moon.MoonIlluminationCalculator
import com.radustavila.nasaexplorer.core.model.MoonIllumination
import com.radustavila.nasaexplorer.core.model.SelenographicPoint
import io.github.cosinekitty.astronomy.Aberration
import io.github.cosinekitty.astronomy.Body
import io.github.cosinekitty.astronomy.Vector
import io.github.cosinekitty.astronomy.geoMoon
import io.github.cosinekitty.astronomy.geoVector
import io.github.cosinekitty.astronomy.rotationAxis
import java.time.Instant
import javax.inject.Inject
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * The subsolar point on the Moon (F25): the Moon-to-Sun direction (J2000, from the geocentric Sun
 * and Moon) turned into the Moon's body frame with the IAU rotation model Astronomy Engine
 * implements (pole and prime meridian angle W). That model approximates the mean Earth / polar axis
 * frame the LRO mosaic and the landing-site table use, to well under a degree: plenty for a
 * terminator drawn on a map.
 */
class AstronomyEngineMoonIlluminationCalculator @Inject constructor() : MoonIlluminationCalculator {

    override fun illumination(instant: Instant): MoonIllumination {
        val time = instant.toAstronomyTime()
        val moonToSun = geoVector(Body.Sun, time, Aberration.None) - geoMoon(time)
        val axis = rotationAxis(Body.Moon, time)
        return MoonIllumination(instant, moonToSun.toSelenographic(axis.north, axis.spin))
    }

    private fun Vector.toSelenographic(north: Vector, spinDegrees: Double): SelenographicPoint {
        // Ascending node of the Moon's equator on the Earth's (J2000) equator: z × north.
        val nodeLength = kotlin.math.hypot(north.x, north.y)
        val node = doubleArrayOf(-north.y / nodeLength, north.x / nodeLength, 0.0)
        val pole = doubleArrayOf(north.x, north.y, north.z)
        val poleCrossNode = cross(pole, node)
        val w = Math.toRadians(spinDegrees)
        val primeMeridian = DoubleArray(3) { cos(w) * node[it] + sin(w) * poleCrossNode[it] }
        val east = cross(pole, primeMeridian)

        val length = length()
        val v = doubleArrayOf(x / length, y / length, z / length)
        val latitude = Math.toDegrees(asin(dot(v, pole).coerceIn(-1.0, 1.0)))
        val longitude = Math.toDegrees(atan2(dot(v, east), dot(v, primeMeridian)))
        return SelenographicPoint(latitude, longitude)
    }

    private fun cross(a: DoubleArray, b: DoubleArray) = doubleArrayOf(
        a[1] * b[2] - a[2] * b[1],
        a[2] * b[0] - a[0] * b[2],
        a[0] * b[1] - a[1] * b[0],
    )

    private fun dot(a: DoubleArray, b: DoubleArray) = a[0] * b[0] + a[1] * b[1] + a[2] * b[2]
}
