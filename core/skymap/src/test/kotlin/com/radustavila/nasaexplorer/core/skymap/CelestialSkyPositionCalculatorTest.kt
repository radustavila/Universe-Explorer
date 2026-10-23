package com.radustavila.nasaexplorer.core.skymap

import com.radustavila.nasaexplorer.core.model.EquatorialCoordinate
import com.radustavila.nasaexplorer.core.model.GeoCoordinate
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class CelestialSkyPositionCalculatorTest {

    private val calculator = CelestialSkyPositionCalculator()

    @Test
    fun `the celestial pole stands at the observer's latitude, due north`() {
        val pole = calculator.horizontal(
            EquatorialCoordinate(0.0, 90.0),
            GeoCoordinate(latitude = 44.43, longitude = 26.10),
            Instant.parse("2026-10-23T21:00:00Z"),
        )

        // Precession moves the J2000 pole a fraction of a degree by 2026.
        assertEquals(44.43, pole.altitudeDegrees, 0.3)
        assertEquals(0.0, ((pole.azimuthDegrees + 180.0) % 360.0) - 180.0, 0.5)
    }

    @Test
    fun `matches the map's own rotation`() {
        val observer = GeoCoordinate(latitude = 51.48, longitude = 0.0)
        val at = Instant.parse("2026-01-10T23:00:00Z")
        val orionNebula = EquatorialCoordinate(83.82, -5.39)
        val expected = (CelestialFrames.j2000ToHorizontal(observer, at) * SkyVector.of(orionNebula)).toHorizontal()

        val actual = calculator.horizontal(orionNebula, observer, at)

        assertEquals(expected.altitudeDegrees, actual.altitudeDegrees, 1e-9)
        assertEquals(expected.azimuthDegrees, actual.azimuthDegrees, 1e-9)
    }
}
