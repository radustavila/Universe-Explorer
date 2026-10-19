package com.radustavila.nasaexplorer.core.astro

import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ISS passes over Bucharest for the fixed 2026-10-19 element set, against Skyfield 1.55
 * (`EarthSatellite.find_events`, 10 degrees; visibility from DE421: Sun 6 degrees down and the
 * station sunlit). The roadmap asks for agreement within a minute.
 */
class Sgp4PassPredictorTest {

    private val predictor = Sgp4PassPredictor()

    private val reference = listOf(
        ReferencePass("2026-10-19T01:19:52Z", "2026-10-19T01:21:49Z", "2026-10-19T01:23:47Z", 14.6, visible = false),
        ReferencePass("2026-10-19T02:54:50Z", "2026-10-19T02:58:13Z", "2026-10-19T03:01:38Z", 84.52, visible = true),
        ReferencePass("2026-10-19T04:32:26Z", "2026-10-19T04:35:24Z", "2026-10-19T04:38:22Z", 26.97, visible = false),
        ReferencePass("2026-10-19T06:10:00Z", "2026-10-19T06:12:55Z", "2026-10-19T06:15:50Z", 25.52, visible = false),
        ReferencePass("2026-10-19T07:46:45Z", "2026-10-19T07:50:09Z", "2026-10-19T07:53:33Z", 70.0, visible = false),
        ReferencePass("2026-10-19T09:24:14Z", "2026-10-19T09:26:39Z", "2026-10-19T09:29:04Z", 18.24, visible = false),
        ReferencePass("2026-10-20T02:07:31Z", "2026-10-20T02:10:51Z", "2026-10-20T02:14:13Z", 61.63, visible = true),
        ReferencePass("2026-10-20T03:44:45Z", "2026-10-20T03:47:52Z", "2026-10-20T03:50:58Z", 31.86, visible = true),
        ReferencePass("2026-10-20T05:22:31Z", "2026-10-20T05:25:21Z", "2026-10-20T05:28:11Z", 23.66, visible = false),
        ReferencePass("2026-10-20T06:59:23Z", "2026-10-20T07:02:43Z", "2026-10-20T07:06:01Z", 48.16, visible = false),
        ReferencePass("2026-10-20T08:36:25Z", "2026-10-20T08:39:25Z", "2026-10-20T08:42:24Z", 28.85, visible = false),
    )

    @Test
    fun `passes match the reference within a minute`() {
        val passes = predictor.passes(
            ReferenceElements.ISS,
            ReferenceElements.BUCHAREST,
            from = Instant.parse("2026-10-19T00:00:00Z"),
            to = Instant.parse("2026-10-21T00:00:00Z"),
            minElevationDegrees = 10.0,
        )

        assertEquals(reference.size, passes.size)
        reference.zip(passes).forEach { (expected, actual) ->
            assertClose("start", expected.start, actual.start)
            assertClose("peak", expected.peak, actual.peak)
            assertClose("end", expected.end, actual.end)
            assertEquals("max elevation of ${expected.start}", expected.maxElevation, actual.maxElevationDegrees, 0.5)
            assertEquals("visibility of ${expected.start}", expected.visible, actual.isVisible)
        }
    }

    @Test
    fun `a pass under way is included, one that ended is not`() {
        val passes = predictor.passes(
            ReferenceElements.ISS,
            ReferenceElements.BUCHAREST,
            from = Instant.parse("2026-10-19T02:58:00Z"),
            to = Instant.parse("2026-10-19T05:00:00Z"),
        )

        assertEquals(2, passes.size)
        assertClose("start", Instant.parse("2026-10-19T02:54:50Z"), passes.first().start)
    }

    @Test
    fun `the sub-satellite point follows the orbit`() {
        val at = Instant.parse("2026-10-19T02:58:13Z")

        val position = checkNotNull(predictor.position(ReferenceElements.ISS, at))

        // At the peak of an 85-degree pass the station is almost overhead.
        assertEquals(ReferenceElements.BUCHAREST.latitude, position.coordinate.latitude, 1.5)
        assertEquals(ReferenceElements.BUCHAREST.longitude, position.coordinate.longitude, 2.0)
        assertEquals(420.0, position.altitudeKm, 15.0)
        assertEquals(27_600.0, position.speedKmPerHour, 200.0)
    }

    @Test
    fun `the ground track has one point per step and stays on the station's latitudes`() {
        val from = Instant.parse("2026-10-19T00:00:00Z")

        val track = predictor.groundTrack(ReferenceElements.ISS, from, from.plus(Duration.ofMinutes(93)))

        assertEquals(94, track.size)
        assertTrue(track.all { kotlin.math.abs(it.coordinate.latitude) <= 52.0 })
        assertTrue(track.maxOf { it.coordinate.latitude } > 51.0)
    }

    private fun assertClose(what: String, expected: Instant, actual: Instant) {
        val error = Duration.between(expected, actual).abs()
        assertTrue("$what $actual is $error from $expected", error <= Duration.ofMinutes(1))
    }

    private data class ReferencePass(
        val start: Instant,
        val peak: Instant,
        val end: Instant,
        val maxElevation: Double,
        val visible: Boolean,
    ) {
        constructor(start: String, peak: String, end: String, maxElevation: Double, visible: Boolean) :
            this(Instant.parse(start), Instant.parse(peak), Instant.parse(end), maxElevation, visible)
    }
}

