package com.radustavila.nasaexplorer.core.astro

import com.radustavila.nasaexplorer.core.domain.sky.SkyMapEphemeris
import com.radustavila.nasaexplorer.core.model.EquatorialCoordinate
import com.radustavila.nasaexplorer.core.model.GeoCoordinate
import com.radustavila.nasaexplorer.core.model.HorizontalCoordinate
import com.radustavila.nasaexplorer.core.model.OrbitalElements
import com.radustavila.nasaexplorer.core.model.SolarSystemBody
import com.radustavila.nasaexplorer.core.model.SolarSystemPosition
import io.github.cosinekitty.astronomy.Aberration
import io.github.cosinekitty.astronomy.Body
import io.github.cosinekitty.astronomy.EquatorEpoch
import io.github.cosinekitty.astronomy.Observer
import io.github.cosinekitty.astronomy.Refraction
import io.github.cosinekitty.astronomy.constellation
import io.github.cosinekitty.astronomy.equator
import io.github.cosinekitty.astronomy.horizon
import io.github.cosinekitty.astronomy.illumination
import java.time.Instant
import javax.inject.Inject
import kotlin.math.asin

/**
 * [SkyMapEphemeris] on Astronomy Engine for the Sun, Moon and planets (topocentric, so the Moon
 * sits where it is seen, up to a degree from its geocentric place) and on the SGP4 port for
 * satellites. Positions are geometric: the map adds refraction to everything alike.
 */
class AstronomyEngineSkyMapEphemeris @Inject constructor() : SkyMapEphemeris {

    override fun solarSystem(observer: GeoCoordinate, at: Instant): List<SolarSystemPosition> {
        val site = Observer(observer.latitude, observer.longitude, 0.0)
        val time = at.toAstronomyTime()
        return SolarSystemBody.entries.map { body ->
            val engineBody = body.engineBody
            val ofDate = equator(engineBody, time, site, EquatorEpoch.OfDate, Aberration.Corrected)
            val j2000 = equator(engineBody, time, site, EquatorEpoch.J2000, Aberration.Corrected)
            val topocentric = horizon(time, site, ofDate.ra, ofDate.dec, Refraction.None)
            val light = illumination(engineBody, time)
            SolarSystemPosition(
                body = body,
                horizontal = HorizontalCoordinate(topocentric.altitude, topocentric.azimuth),
                equatorial = EquatorialCoordinate(j2000.ra * DEGREES_PER_HOUR, j2000.dec),
                magnitude = light.mag,
                illuminatedFraction = if (body == SolarSystemBody.SUN) 1.0 else light.phaseFraction,
                angularDiameterDegrees = angularDiameter(body.radiusKm, ofDate.dist),
            )
        }
    }

    override fun satellite(elements: OrbitalElements, observer: GeoCoordinate, at: Instant): HorizontalCoordinate? {
        val sgp4 = try {
            Sgp4(elements)
        } catch (_: IllegalArgumentException) {
            return null
        }
        return try {
            val position = EarthFrames.temeToEcef(sgp4.propagate(at).position, at)
            val look = EarthFrames.lookAngles(observer, EarthFrames.geodeticToEcef(observer), position)
            HorizontalCoordinate(look.elevationDegrees, look.azimuthDegrees)
        } catch (_: IllegalStateException) {
            // Propagated into a decayed or degenerate orbit.
            null
        }
    }

    override fun constellationAt(position: EquatorialCoordinate): String =
        constellation(position.rightAscensionDegrees / DEGREES_PER_HOUR, position.declinationDegrees).symbol

    /** Apparent diameter, degrees, of a sphere of [radiusKm] at [distanceAu]. */
    private fun angularDiameter(radiusKm: Double, distanceAu: Double): Double =
        Math.toDegrees(2.0 * asin((radiusKm / (distanceAu * KM_PER_AU)).coerceAtMost(1.0)))

    private val SolarSystemBody.engineBody: Body
        get() = when (this) {
            SolarSystemBody.SUN -> Body.Sun
            SolarSystemBody.MOON -> Body.Moon
            SolarSystemBody.MERCURY -> Body.Mercury
            SolarSystemBody.VENUS -> Body.Venus
            SolarSystemBody.MARS -> Body.Mars
            SolarSystemBody.JUPITER -> Body.Jupiter
            SolarSystemBody.SATURN -> Body.Saturn
            SolarSystemBody.URANUS -> Body.Uranus
            SolarSystemBody.NEPTUNE -> Body.Neptune
        }

    /** Mean radius, km; for the apparent size of the disc. */
    private val SolarSystemBody.radiusKm: Double
        get() = when (this) {
            SolarSystemBody.SUN -> 695_700.0
            SolarSystemBody.MOON -> 1_737.4
            SolarSystemBody.MERCURY -> 2_439.7
            SolarSystemBody.VENUS -> 6_051.8
            SolarSystemBody.MARS -> 3_389.5
            SolarSystemBody.JUPITER -> 69_911.0
            SolarSystemBody.SATURN -> 58_232.0
            SolarSystemBody.URANUS -> 25_362.0
            SolarSystemBody.NEPTUNE -> 24_622.0
        }

    private companion object {
        const val DEGREES_PER_HOUR = 15.0
        const val KM_PER_AU = 149_597_870.7
    }
}
