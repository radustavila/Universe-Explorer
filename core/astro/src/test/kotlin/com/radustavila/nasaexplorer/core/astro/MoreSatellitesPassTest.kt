package com.radustavila.nasaexplorer.core.astro

import com.radustavila.nasaexplorer.core.model.GeoCoordinate
import com.radustavila.nasaexplorer.core.model.OrbitalElements
import com.radustavila.nasaexplorer.core.model.SatellitePass
import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Hubble and Tiangong passes for the fixed 2026-10-21 CelesTrak elements, against Heavens-Above's
 * visible-pass tables read on 2026-10-22 (its own elements were a day newer). The roadmap asks for
 * agreement within a minute; Heavens-Above lists the rise at 10 degrees, and the highest point only
 * when the satellite is still sunlit there, so only those peaks are compared.
 */
class MoreSatellitesPassTest {

    private val predictor = Sgp4PassPredictor()
    private val from = Instant.parse("2026-10-23T00:00:00Z")
    private val to = Instant.parse("2026-10-28T00:00:00Z")

    @Test
    fun `Hubble's visible passes over Cairo match Heavens-Above within a minute`() {
        val expected = listOf(
            HeavensAbovePass("2026-10-24T16:53:37Z"),
            HeavensAbovePass("2026-10-25T16:20:31Z", peak = "2026-10-25T16:23:18Z", maxElevation = 20.0),
            HeavensAbovePass(
                "2026-10-26T15:47:25Z",
                peak = "2026-10-26T15:50:10Z",
                maxElevation = 19.0,
                end = "2026-10-26T15:52:54Z",
            ),
            HeavensAbovePass("2026-10-26T17:25:51Z"),
            HeavensAbovePass("2026-10-27T16:52:40Z"),
        )

        assertMatches(expected, visiblePasses(ReferenceElements.HUBBLE, ReferenceElements.CAIRO))
    }

    @Test
    fun `Tiangong's visible passes over Bucharest match Heavens-Above within a minute`() {
        val expected = listOf(
            HeavensAbovePass("2026-10-23T16:46:47Z"),
            HeavensAbovePass("2026-10-24T17:22:30Z"),
            HeavensAbovePass("2026-10-25T16:23:07Z", peak = "2026-10-25T16:25:38Z", maxElevation = 22.0),
            HeavensAbovePass("2026-10-26T16:59:18Z"),
            HeavensAbovePass("2026-10-27T15:59:36Z", peak = "2026-10-27T16:02:27Z", maxElevation = 30.0),
            HeavensAbovePass("2026-10-27T17:35:55Z"),
        )

        assertMatches(expected, visiblePasses(ReferenceElements.TIANGONG, ReferenceElements.BUCHAREST))
    }

    @Test
    fun `Hubble never climbs 10 degrees above Bucharest`() {
        assertTrue(predictor.passes(ReferenceElements.HUBBLE, ReferenceElements.BUCHAREST, from, to).isEmpty())
    }

    @Test
    fun `the many-satellite search finds the same visible passes as one search per satellite`() {
        val satellites = with(ReferenceElements) { listOf(ISS, HUBBLE, TIANGONG, VANGUARD_1) }
        val start = Instant.parse("2026-10-21T00:00:00Z")
        val end = start.plus(Duration.ofDays(4))

        for (observer in listOf(ReferenceElements.BUCHAREST, ReferenceElements.CAIRO, ReferenceElements.LONDON)) {
            val batched = predictor.visiblePasses(satellites, observer, start, end)
            satellites.forEach { satellite ->
                val oneByOne = predictor.passes(satellite, observer, start, end).filter(SatellitePass::isVisible)
                val found = batched.getValue(satellite.noradId)
                assertEquals("${satellite.name} over $observer", oneByOne.size, found.size)
                oneByOne.zip(found).forEach { (expected, actual) ->
                    assertTrue(Duration.between(expected.start, actual.start).abs() <= Duration.ofSeconds(1))
                    assertTrue(Duration.between(expected.end, actual.end).abs() <= Duration.ofSeconds(1))
                }
            }
        }
    }

    private fun visiblePasses(elements: OrbitalElements, observer: GeoCoordinate): List<SatellitePass> =
        predictor.visiblePasses(listOf(elements), observer, from, to).getValue(elements.noradId)

    private fun assertMatches(expected: List<HeavensAbovePass>, actual: List<SatellitePass>) {
        assertEquals(expected.size, actual.size)
        expected.zip(actual).forEach { (reference, pass) ->
            assertClose("start", reference.start, pass.start)
            reference.peak?.let { assertClose("peak", it, pass.peak) }
            reference.end?.let { assertClose("end", it, pass.end) }
            // Heavens-Above rounds to whole degrees.
            reference.maxElevation?.let {
                assertEquals("max elevation of ${reference.start}", it, pass.maxElevationDegrees, 1.0)
            }
            assertTrue(pass.isVisible)
        }
    }

    private fun assertClose(what: String, expected: Instant, actual: Instant) {
        val error = Duration.between(expected, actual).abs()
        assertTrue("$what $actual is $error from $expected", error <= Duration.ofMinutes(1))
    }

    private data class HeavensAbovePass(
        val start: Instant,
        val peak: Instant?,
        val maxElevation: Double?,
        val end: Instant?,
    ) {
        constructor(start: String, peak: String? = null, maxElevation: Double? = null, end: String? = null) :
            this(Instant.parse(start), peak?.let(Instant::parse), maxElevation, end?.let(Instant::parse))
    }
}
