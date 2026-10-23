package com.radustavila.nasaexplorer.core.astro

import com.radustavila.nasaexplorer.core.domain.moon.MoonTerminator
import com.radustavila.nasaexplorer.core.model.SelenographicPoint
import java.time.Instant
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The terminator checked against the Moon's phase at three fixed instants (F25 "done when"). Seen
 * from Earth, the subsolar longitude is about 180° − phase angle: far side at new Moon, the near
 * side's centre at full Moon, the eastern limb lit at first quarter. Libration and the Earth–Sun
 * parallax keep it within ~8° of that.
 */
class AstronomyEngineMoonIlluminationCalculatorTest {

    private val calculator = AstronomyEngineMoonIlluminationCalculator()
    private val apollo11 = SelenographicPoint(0.67416, 23.47314)
    private val nearSideCentre = SelenographicPoint(0.0, 0.0)

    @Test
    fun `full Moon puts the Sun over the near side`() {
        // Full Moon 2024-04-23 23:49 UTC.
        val sun = calculator.illumination(Instant.parse("2024-04-23T23:49:00Z")).subsolarPoint
        assertLongitude(0.0, sun.longitude)
        assertFalse(MoonTerminator.isDark(nearSideCentre, sun))
        assertFalse(MoonTerminator.isDark(apollo11, sun))
    }

    @Test
    fun `new Moon leaves the whole near side dark`() {
        // New Moon (total solar eclipse) 2024-04-08 18:21 UTC.
        val sun = calculator.illumination(Instant.parse("2024-04-08T18:21:00Z")).subsolarPoint
        assertLongitude(180.0, sun.longitude)
        assertTrue(MoonTerminator.isDark(nearSideCentre, sun))
        assertTrue(MoonTerminator.isDark(apollo11, sun))
    }

    @Test
    fun `first quarter lights the eastern half, Tranquillitatis included`() {
        // First quarter 2024-04-15 19:13 UTC.
        val sun = calculator.illumination(Instant.parse("2024-04-15T19:13:00Z")).subsolarPoint
        assertLongitude(90.0, sun.longitude)
        assertFalse(MoonTerminator.isDark(apollo11, sun))
        assertTrue(MoonTerminator.isDark(SelenographicPoint(0.0, -45.0), sun)) // Oceanus Procellarum
    }

    @Test
    fun `subsolar latitude stays within the Moon's small axial tilt`() {
        val start = Instant.parse("2025-01-01T00:00:00Z")
        repeat(60) { day ->
            val sun = calculator.illumination(start.plusSeconds(day * 86_400L * 6)).subsolarPoint
            assertTrue("latitude ${sun.latitude}", abs(sun.latitude) < 1.7)
        }
    }

    private fun assertLongitude(expected: Double, actual: Double) {
        val difference = abs(((actual - expected + 540.0) % 360.0) - 180.0)
        assertEquals("subsolar longitude $actual, expected about $expected", 0.0, difference, TOLERANCE_DEGREES)
    }

    private companion object {
        const val TOLERANCE_DEGREES = 10.0
    }
}
