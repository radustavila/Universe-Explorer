package com.radustavila.nasaexplorer.core.astro

import com.radustavila.nasaexplorer.core.model.EquatorialCoordinate
import com.radustavila.nasaexplorer.core.model.SolarSystemBody
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AstronomyEngineSkyMapEphemerisTest {

    private val ephemeris = AstronomyEngineSkyMapEphemeris()

    @Test
    fun `the Sun crosses the meridian at the equinox at 90 degrees minus the latitude`() {
        // Solar noon in Bucharest on the March 2026 equinox: 10:15 UT plus the equation of time.
        val sun = ephemeris.solarSystem(ReferenceElements.BUCHAREST, Instant.parse("2026-03-20T10:23:00Z"))
            .single { it.body == SolarSystemBody.SUN }

        assertEquals(90.0 - ReferenceElements.BUCHAREST.latitude, sun.horizontal.altitudeDegrees, 0.4)
        assertEquals(180.0, sun.horizontal.azimuthDegrees, 1.5)
        assertEquals(0.536, sun.angularDiameterDegrees, 0.01)
        assertEquals(-26.7, sun.magnitude, 0.2)
    }

    @Test
    fun `every body is there with a sensible size and phase`() {
        val bodies = ephemeris.solarSystem(ReferenceElements.BUCHAREST, Instant.parse("2026-10-22T20:00:00Z"))

        assertEquals(SolarSystemBody.entries.toList(), bodies.map { it.body })
        val moon = bodies.single { it.body == SolarSystemBody.MOON }
        assertTrue(moon.angularDiameterDegrees in 0.49..0.57)
        assertTrue(moon.illuminatedFraction in 0.0..1.0)
        bodies.filter { it.body != SolarSystemBody.SUN && it.body != SolarSystemBody.MOON }.forEach {
            assertTrue("${it.body} ${it.angularDiameterDegrees}", it.angularDiameterDegrees < 0.02)
        }
    }

    @Test
    fun `the Sun is in Virgo in late October`() {
        val sun = ephemeris.solarSystem(ReferenceElements.BUCHAREST, Instant.parse("2026-10-22T12:00:00Z"))
            .single { it.body == SolarSystemBody.SUN }

        assertEquals("Vir", ephemeris.constellationAt(sun.equatorial))
    }

    @Test
    fun `stars are placed in their constellations`() {
        assertEquals("Ori", ephemeris.constellationAt(EquatorialCoordinate(88.7929, 7.4071)))
        assertEquals("UMi", ephemeris.constellationAt(EquatorialCoordinate(37.9546, 89.2641)))
        assertEquals("CMa", ephemeris.constellationAt(EquatorialCoordinate(101.2872, -16.7161)))
    }

    @Test
    fun `the ISS peaks where Skyfield puts it`() {
        // Peaks of two passes over Bucharest from Sgp4PassPredictorTest's reference.
        val high = issAt("2026-10-19T02:58:13Z")
        val low = issAt("2026-10-19T04:35:24Z")

        assertEquals(84.52, checkNotNull(high).altitudeDegrees, 0.5)
        assertEquals(26.97, checkNotNull(low).altitudeDegrees, 0.5)
    }

    private fun issAt(iso: String) =
        ephemeris.satellite(ReferenceElements.ISS, ReferenceElements.BUCHAREST, Instant.parse(iso))
}
