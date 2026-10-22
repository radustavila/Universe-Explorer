package com.radustavila.nasaexplorer.core.skymap

import com.radustavila.nasaexplorer.core.model.EquatorialCoordinate
import com.radustavila.nasaexplorer.core.model.GeoCoordinate
import com.radustavila.nasaexplorer.core.model.HorizontalCoordinate
import com.radustavila.nasaexplorer.core.testing.data.TestSkyMap
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SkyChartTest {

    private val bucharest = GeoCoordinate(latitude = 44.43, longitude = 26.10)
    private val at = Instant.parse("2026-10-22T22:00:00Z")
    private val chart = SkyChart(TestSkyMap.catalog())

    @Test
    fun `stars are turned with the J2000 to horizontal matrix`() {
        val sky = chart.at(bucharest, at)
        val matrix = CelestialFrames.j2000ToHorizontal(bucharest, at)

        chart.catalog.stars.forEachIndexed { i, star ->
            val expected = (matrix * SkyVector.of(star.position)).toHorizontal()
            val actual = sky.star(i).toHorizontal()
            val refraction = CelestialFrames.refraction(expected.altitudeDegrees)
            assertEquals(star.name, expected.altitudeDegrees + refraction, actual.altitudeDegrees, 1e-3)
            if (expected.altitudeDegrees < 89.0) {
                assertEquals(star.name, expected.azimuthDegrees, actual.azimuthDegrees, 1e-3)
            }
        }
    }

    @Test
    fun `figures become segments between their stars`() {
        val sky = chart.at(bucharest, at)

        // Orion's one closed line of five points has four segments.
        assertEquals(4, chart.segmentCount)
        assertTrue(chart.segmentFigures.all { it == 0 })
        assertEquals(4 * 6, sky.segments.size)
        val betelgeuse = sky.direction(TestSkyMap.BETELGEUSE.position)
        assertEquals(betelgeuse.x, sky.segments[0].toDouble(), 1e-6)
    }

    @Test
    fun `refraction raises low objects and leaves high ones`() {
        val low = SkyChart.apparent(HorizontalCoordinate(altitudeDegrees = 0.0, azimuthDegrees = 90.0)).toHorizontal()
        val high = SkyChart.apparent(HorizontalCoordinate(altitudeDegrees = 70.0, azimuthDegrees = 90.0)).toHorizontal()

        assertEquals(0.48, low.altitudeDegrees, 0.02)
        assertEquals(90.0, low.azimuthDegrees, 1e-6)
        assertEquals(70.0, high.altitudeDegrees, 1e-6)
    }

    @Test
    fun `any J2000 direction can be placed`() {
        val pole = chart.at(bucharest, at).direction(EquatorialCoordinate(0.0, 90.0)).toHorizontal()

        // The celestial pole, precessed to 2026, sits a few arcminutes off true north at the latitude.
        assertEquals(44.43 + CelestialFrames.refraction(44.43), pole.altitudeDegrees, 0.2)
    }

    @Test
    fun `counts the stars down to a magnitude`() {
        // Sirius -1.44, Rigel 0.18, Betelgeuse 0.45, Bellatrix 1.64, Polaris 1.97, Saiph 2.07.
        assertEquals(0, chart.starsBrighterThan(-2f))
        assertEquals(3, chart.starsBrighterThan(0.45f))
        assertEquals(6, chart.starsBrighterThan(6.5f))
    }
}
