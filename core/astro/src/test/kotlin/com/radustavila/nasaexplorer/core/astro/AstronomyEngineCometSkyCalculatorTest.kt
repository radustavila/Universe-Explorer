package com.radustavila.nasaexplorer.core.astro

import com.radustavila.nasaexplorer.core.model.Comet
import com.radustavila.nasaexplorer.core.model.CometEphemerisPoint
import com.radustavila.nasaexplorer.core.model.CometVisibility
import com.radustavila.nasaexplorer.core.model.EquatorialCoordinate
import java.time.Duration
import java.time.Instant
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Against JPL Horizons for Bucharest (2026-10-23): its geocentric daily rows are the input, and its
 * refracted topocentric answers the reference.
 */
class AstronomyEngineCometSkyCalculatorTest {

    private val calculator = AstronomyEngineCometSkyCalculator()

    /** C/2026 A2 (Bok), dec +60: never sets from Bucharest. */
    private val bok = comet(
        "2026 A2",
        "2026-10-22T00:00:00Z" to row(291.53490, 60.37661, 14.174, 2.078869441552, 1.70599144548927),
        "2026-10-23T00:00:00Z" to row(292.54598, 59.85362, 14.152, 2.074508644170, 1.69719869832713),
        "2026-10-24T00:00:00Z" to row(293.55269, 59.31902, 14.130, 2.070207401569, 1.68866070201638),
        "2026-10-25T00:00:00Z" to row(294.55462, 58.77287, 14.109, 2.065966229095, 1.68038646078167),
    )

    /** C/2026 O1 (PANSTARRS), dec -31: low in the south. */
    private val panstarrs = comet(
        "2026 O1",
        "2026-10-22T00:00:00Z" to row(334.89559, -30.62889, 17.591, 3.673206902807, 3.13169002407864),
        "2026-10-23T00:00:00Z" to row(334.57775, -30.74263, 17.575, 3.663434069306, 3.13875556367632),
        "2026-10-24T00:00:00Z" to row(334.26545, -30.85259, 17.559, 3.653657257850, 3.14600379410193),
        "2026-10-25T00:00:00Z" to row(333.95880, -30.95882, 17.542, 3.643876495173, 3.15342580348050),
    )

    @Test
    fun `position and brightness are interpolated between daily rows`() {
        val tonight = calculator.tonight(bok, observer = null, now = Instant.parse("2026-10-23T12:00:00Z"))!!

        assertEquals(14.141, tonight.magnitude!!, 1e-3)
        assertEquals(CometVisibility.TOO_FAINT, tonight.visibility)
        assertEquals(293.049, tonight.position.rightAscensionDegrees, 0.01)
        assertEquals(59.587, tonight.position.declinationDegrees, 0.01)
        assertEquals("Draco", tonight.constellation)
        assertEquals(14.109, tonight.peakMagnitude!!, 0.0)
        assertEquals(Instant.parse("2026-10-25T00:00:00Z"), tonight.peakTime)
        assertNull(tonight.sky)
    }

    @Test
    fun `altitude and azimuth match horizons within a few tenths of a degree`() {
        // Horizons, refracted: 18:00 318.179213 64.232222 · 22:00 321.842305 34.908469 · 2:00 346.983122 15.867128.
        listOf(
            "2026-10-23T18:00:00Z" to (318.179213 to 64.232222),
            "2026-10-23T22:00:00Z" to (321.842305 to 34.908469),
            "2026-10-24T02:00:00Z" to (346.983122 to 15.867128),
        ).forEach { (at, expected) ->
            val now = calculator.tonight(bok, ReferenceElements.BUCHAREST, Instant.parse(at))!!.sky!!.now
            assertEquals(at, expected.second, now.altitudeDegrees, 0.3)
            assertEquals(at, expected.first, now.azimuthDegrees, 0.5)
        }
    }

    @Test
    fun `sky map position matches horizons airless altitude and azimuth`() {
        // Horizons, airless, topocentric for Bucharest (az, el): the sky map adds refraction itself.
        listOf(
            "2026-10-23T00:00:00Z" to (333.327848321 to 23.637085528),
            "2026-10-23T06:00:00Z" to (17.618633575 to 18.059033811),
            "2026-10-23T12:00:00Z" to (45.034099800 to 54.129624804),
            "2026-10-23T18:00:00Z" to (318.179213388 to 64.224035148),
            "2026-10-24T00:00:00Z" to (332.945616275 to 23.236579483),
        ).forEach { (at, expected) ->
            val position = calculator.skyPosition(bok, ReferenceElements.BUCHAREST, Instant.parse(at))!!
            assertEquals(at, expected.second, position.horizontal.altitudeDegrees, 0.02)
            assertEquals(at, expected.first, position.horizontal.azimuthDegrees, 0.05)
        }
    }

    @Test
    fun `sky map position carries the comet, its J2000 direction and interpolated magnitude`() {
        val position = calculator.skyPosition(bok, ReferenceElements.BUCHAREST, Instant.parse("2026-10-23T12:00:00Z"))!!

        assertEquals("2026 A2", position.designation)
        assertEquals(293.049, position.equatorial.rightAscensionDegrees, 0.01)
        assertEquals(59.587, position.equatorial.declinationDegrees, 0.01)
        assertEquals(14.141, position.magnitude!!, 1e-3)
        assertNull(calculator.skyPosition(bok, ReferenceElements.BUCHAREST, Instant.parse("2026-10-25T00:00:01Z")))
    }

    @Test
    fun `a circumpolar comet never rises or sets and is best when the night begins`() {
        val sky = calculator.tonight(bok, ReferenceElements.BUCHAREST, Instant.parse("2026-10-23T12:00:00Z"))!!.sky!!

        assertTrue(sky.isAlwaysUp)
        assertNull(sky.rise)
        assertNull(sky.set)
        // It culminates at 75 degrees around 16:00 UT, just before the Sun is 12 degrees down (about 16:30).
        val best = sky.bestTime!!
        assertWithin(Instant.parse("2026-10-23T16:30:00Z"), best, Duration.ofMinutes(20))
        assertTrue(sky.best!!.altitudeDegrees in 72.0..75.5)
    }

    @Test
    fun `during the night the best time is the highest point still ahead`() {
        val now = Instant.parse("2026-10-23T18:00:00Z")

        val sky = calculator.tonight(bok, ReferenceElements.BUCHAREST, now)!!.sky!!

        assertEquals(now, sky.bestTime)
        assertEquals(64.23, sky.best!!.altitudeDegrees, 0.3)
    }

    @Test
    fun `rise and set match horizons within three minutes, and a low comet is not visible`() {
        // Horizons: rises 14:45, transits 18:26 at 14.9 degrees, sets 22:08 (UT).
        val noon = Instant.parse("2026-10-23T12:00:00Z")
        val tonight = calculator.tonight(panstarrs, ReferenceElements.BUCHAREST, noon)!!
        val sky = tonight.sky!!

        assertWithin(Instant.parse("2026-10-23T14:45:00Z"), sky.rise!!, Duration.ofMinutes(3))
        assertWithin(Instant.parse("2026-10-23T22:08:00Z"), sky.set!!, Duration.ofMinutes(3))
        assertFalse(sky.isAlwaysUp)
        assertFalse(sky.isVisibleTonight)
        // Astronomy Engine's spelling of Piscis Austrinus.
        assertEquals("Pisces Austrinus", tonight.constellation)
    }

    @Test
    fun `nothing outside the ephemeris`() {
        assertNull(calculator.tonight(bok, ReferenceElements.BUCHAREST, Instant.parse("2026-10-21T23:00:00Z")))
        assertNull(calculator.tonight(bok, ReferenceElements.BUCHAREST, Instant.parse("2026-10-25T00:00:01Z")))
        assertNull(calculator.tonight(bok.copy(ephemeris = emptyList()), null, Instant.parse("2026-10-23T00:00:00Z")))
    }

    @Test
    fun `right ascension is interpolated across 0 degrees`() {
        val crossing = comet(
            "test",
            "2026-10-23T00:00:00Z" to row(359.0, 10.0, null, 1.0, 1.0),
            "2026-10-24T00:00:00Z" to row(1.0, 10.0, 12.0, 1.0, 1.0),
        )

        val tonight = calculator.tonight(crossing, null, Instant.parse("2026-10-23T12:00:00Z"))!!

        assertTrue(tonight.position.rightAscensionDegrees.let { it < 0.01 || it > 359.99 })
        // One side without a brightness model: the other side's value.
        assertEquals(12.0, tonight.magnitude!!, 0.0)
        assertEquals(CometVisibility.TELESCOPE, tonight.visibility)
    }

    private fun assertWithin(expected: Instant, actual: Instant, tolerance: Duration) {
        assertTrue("$actual vs $expected", abs(Duration.between(expected, actual).toMillis()) <= tolerance.toMillis())
    }

    private data class Row(val ra: Double, val dec: Double, val magnitude: Double?, val r: Double, val delta: Double)

    private fun row(ra: Double, dec: Double, magnitude: Double?, r: Double, delta: Double) =
        Row(ra, dec, magnitude, r, delta)

    private fun comet(designation: String, vararg rows: Pair<String, Row>) = Comet(
        designation = designation,
        name = designation,
        absoluteMagnitude = null,
        slopeParameter = null,
        perihelionDistanceAu = 1.0,
        eccentricity = 1.0,
        perihelionTime = null,
        ephemeris = rows.map { (time, row) ->
            CometEphemerisPoint(
                time = Instant.parse(time),
                position = EquatorialCoordinate(row.ra, row.dec),
                magnitude = row.magnitude,
                sunDistanceAu = row.r,
                earthDistanceAu = row.delta,
            )
        },
    )
}
