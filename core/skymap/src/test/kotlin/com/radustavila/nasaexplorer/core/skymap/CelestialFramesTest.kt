package com.radustavila.nasaexplorer.core.skymap

import com.radustavila.nasaexplorer.core.model.EquatorialCoordinate
import com.radustavila.nasaexplorer.core.model.GeoCoordinate
import com.radustavila.nasaexplorer.core.model.HorizontalCoordinate
import io.github.cosinekitty.astronomy.Aberration
import io.github.cosinekitty.astronomy.Body
import io.github.cosinekitty.astronomy.EquatorEpoch
import io.github.cosinekitty.astronomy.Observer
import io.github.cosinekitty.astronomy.Refraction
import io.github.cosinekitty.astronomy.Time
import io.github.cosinekitty.astronomy.defineStar
import io.github.cosinekitty.astronomy.equator
import io.github.cosinekitty.astronomy.horizon
import java.time.Duration
import java.time.Instant
import kotlin.math.abs
import kotlin.math.cos
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The transforms against published worked examples: Meeus, Astronomical Algorithms (2nd ed.),
 * examples 12.a, 12.b, 13.b and 21.b, and Duffett-Smith, Practical Astronomy with your Calculator
 * (3rd ed.), section 25; then whole stars against Astronomy Engine.
 */
class CelestialFramesTest {

    @Test
    fun `mean sidereal time matches Meeus examples 12a and 12b`() {
        // 13h 10m 46.3668s and 8h 34m 57.0896s.
        assertEquals(197.693195, CelestialFrames.greenwichMeanSiderealTime(Instant.parse("1987-04-10T00:00:00Z")), 1e-5)
        assertEquals(128.737873, CelestialFrames.greenwichMeanSiderealTime(Instant.parse("1987-04-10T19:21:00Z")), 1e-5)
    }

    @Test
    fun `precession matches Meeus example 21b`() {
        // Theta Persei, J2000 position already carried to the date by its proper motion.
        val j2000 = SkyVector.of(EquatorialCoordinate(41.054063, 49.227750))
        val at = instantOfJulianDate(2462088.69)

        val ofDate = (CelestialFrames.precession(at) * j2000).toEquatorial()

        assertEquals(41.547214, ofDate.rightAscensionDegrees, 2e-5)
        assertEquals(49.348483, ofDate.declinationDegrees, 2e-5)
    }

    @Test
    fun `hour angle to horizontal matches Duffett-Smith section 25`() {
        // H = 5h 51m 44s, dec = +23 13' 10", latitude 52 N: altitude 19 20' 03.64", azimuth 283 16' 15.7".
        val horizontal = CelestialFrames.horizontal(
            hourAngle = (5 + 51 / 60.0 + 44 / 3600.0) * 15.0,
            declination = 23 + 13 / 60.0 + 10 / 3600.0,
            latitude = 52.0,
        )

        assertEquals(19.334344, horizontal.altitudeDegrees, 1e-4)
        assertEquals(283.271028, horizontal.azimuthDegrees, 1e-4)
    }

    @Test
    fun `apparent place to horizontal matches Meeus example 13b`() {
        // Venus from the US Naval Observatory, 1987 April 10 at 19:21 UT: altitude 15.1249,
        // azimuth 68.0337 measured from the south, so 248.0337 from the north. Meeus uses apparent
        // sidereal time; the mean time used here differs by 0.24 s, a thousandth of a degree.
        val washington = GeoCoordinate(
            latitude = 38 + 55 / 60.0 + 17 / 3600.0,
            longitude = -(77 + 3 / 60.0 + 56 / 3600.0),
        )
        val venus = SkyVector.of(EquatorialCoordinate(347.3193375, -6.7198917))

        val horizontal = (CelestialFrames.ofDateToHorizontal(washington, Instant.parse("1987-04-10T19:21:00Z")) * venus)
            .toHorizontal()

        assertEquals(15.1249, horizontal.altitudeDegrees, 0.005)
        assertEquals(248.0337, horizontal.azimuthDegrees, 0.005)
    }

    @Test
    fun `Polaris stands at the observer's latitude all night`() {
        val polaris = SkyVector.of(EquatorialCoordinate(37.9546, 89.2641))
        for (latitude in listOf(10.0, 44.43, 60.0)) {
            val observer = GeoCoordinate(latitude, 26.10)
            for (hour in 0 until 24 step 3) {
                val at = Instant.parse("2026-10-22T00:00:00Z").plus(Duration.ofHours(hour.toLong()))
                val horizontal = (CelestialFrames.j2000ToHorizontal(observer, at) * polaris).toHorizontal()
                // Polaris circles the pole at about 0.66 degrees today.
                assertEquals("latitude $latitude at $at", latitude, horizontal.altitudeDegrees, 0.7)
                val offNorth = minOf(horizontal.azimuthDegrees, 360.0 - horizontal.azimuthDegrees)
                assertTrue("azimuth ${horizontal.azimuthDegrees}", offNorth * cos(Math.toRadians(latitude)) < 0.7)
            }
        }
    }

    @Test
    fun `bright stars match Astronomy Engine within a hundredth of a degree`() {
        val bucharest = GeoCoordinate(latitude = 44.43, longitude = 26.10)
        val stars = mapOf(
            "Sirius" to EquatorialCoordinate(101.2872, -16.7161),
            "Vega" to EquatorialCoordinate(279.2346, 38.7837),
            "Betelgeuse" to EquatorialCoordinate(88.7929, 7.4071),
            "Capella" to EquatorialCoordinate(79.1723, 45.9980),
        )
        for (iso in listOf("2026-10-22T19:00:00Z", "2026-10-23T03:30:00Z", "2031-06-01T22:00:00Z")) {
            val at = Instant.parse(iso)
            val matrix = CelestialFrames.j2000ToHorizontal(bucharest, at)
            stars.forEach { (name, position) ->
                val ours = (matrix * SkyVector.of(position)).toHorizontal()
                val reference = astronomyEngine(position, bucharest, at)
                assertEquals("$name altitude at $iso", reference.altitudeDegrees, ours.altitudeDegrees, 0.01)
                // Azimuth differences shrink towards the zenith; compare them as arcs on the sky.
                val azimuthArc = azimuthDifference(reference, ours) * cos(Math.toRadians(ours.altitudeDegrees))
                assertEquals("$name azimuth at $iso", 0.0, azimuthArc, 0.01)
            }
        }
    }

    @Test
    fun `refraction lifts the horizon by about half a degree and fades below it`() {
        assertEquals(0.48, CelestialFrames.refraction(0.0), 0.02)
        assertEquals(0.028, CelestialFrames.refraction(30.0), 0.003)
        assertEquals(0.0, CelestialFrames.refraction(90.0), 0.001)
        assertEquals(0.0, CelestialFrames.refraction(-6.0), 0.0)
        assertTrue(CelestialFrames.refraction(-3.0) in 0.0..CelestialFrames.refraction(-1.0))
    }

    private fun astronomyEngine(
        position: EquatorialCoordinate,
        observer: GeoCoordinate,
        at: Instant,
    ): HorizontalCoordinate {
        defineStar(Body.Star1, position.rightAscensionDegrees / 15.0, position.declinationDegrees, 1000.0)
        val site = Observer(observer.latitude, observer.longitude, 0.0)
        val time = Time.fromMillisecondsSince1970(at.toEpochMilli())
        val ofDate = equator(Body.Star1, time, site, EquatorEpoch.OfDate, Aberration.None)
        val topocentric = horizon(time, site, ofDate.ra, ofDate.dec, Refraction.None)
        return HorizontalCoordinate(topocentric.altitude, topocentric.azimuth)
    }

    private fun azimuthDifference(a: HorizontalCoordinate, b: HorizontalCoordinate): Double {
        val difference = abs(a.azimuthDegrees - b.azimuthDegrees) % 360.0
        return minOf(difference, 360.0 - difference)
    }

    private fun instantOfJulianDate(julianDate: Double): Instant =
        Instant.ofEpochMilli(((julianDate - 2440587.5) * 86_400_000.0).toLong())
}
