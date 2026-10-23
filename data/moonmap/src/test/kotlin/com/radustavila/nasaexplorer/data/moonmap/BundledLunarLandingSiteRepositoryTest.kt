package com.radustavila.nasaexplorer.data.moonmap

import com.radustavila.nasaexplorer.core.model.LunarProgramme
import com.radustavila.nasaexplorer.data.moonmap.sites.BundledLunarLandingSiteRepository
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.tan
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BundledLunarLandingSiteRepositoryTest {

    @Test
    fun `every bundled site parses, ids are unique and the table is oldest first`() = runTest {
        val sites = BundledLunarLandingSiteRepository(StandardTestDispatcher(testScheduler)).sites()

        assertEquals(26, sites.size)
        assertEquals(sites.size, sites.map { it.id }.toSet().size)
        assertEquals(sites.sortedBy { it.landedOn }, sites)
        assertEquals(LunarProgramme.entries.toSet(), sites.map { it.programme }.toSet())
        assertTrue(sites.filter { it.programme == LunarProgramme.APOLLO }.all { it.crew.size == 2 })
    }

    @Test
    fun `every site is on the Mercator map`() = runTest {
        val sites = BundledLunarLandingSiteRepository(StandardTestDispatcher(testScheduler)).sites()

        assertTrue(sites.all { abs(it.position.latitude) < MERCATOR_LIMIT })
    }

    @Test
    fun `the Apollo 11 marker lands on the LM within one zoom 6 pixel`() = runTest {
        val apollo11 = BundledLunarLandingSiteRepository(StandardTestDispatcher(testScheduler)).sites()
            .single { it.id == "apollo-11" }

        // NSSDCA, LRO-derived position of the Apollo 11 Lunar Module (Wagner et al. 2017).
        val (lmX, lmY) = pixelAtZoom6(latitude = 0.67416, longitude = 23.47314)
        val (x, y) = pixelAtZoom6(apollo11.position.latitude, apollo11.position.longitude)
        assertTrue("x off by ${abs(x - lmX)} px", abs(x - lmX) < 1.0)
        assertTrue("y off by ${abs(y - lmY)} px", abs(y - lmY) < 1.0)
        // One zoom 6 pixel is about 670 m of lunar equator: the tile grid the build script cuts.
        assertEquals(666.0, MOON_CIRCUMFERENCE_M / WORLD_PIXELS_ZOOM_6, 1.0)
    }

    @Test
    fun `rows with unknown programmes or positions off the Moon are dropped`() {
        val sites = BundledLunarLandingSiteRepository.parseSites(
            """
            {"sites": [
              {"id": "a", "mission": "A", "programme": "APOLLO", "kind": "CREWED", "landed": "1969-07-20",
               "latitude": 0.0, "longitude": 0.0, "region": "R"},
              {"id": "b", "mission": "B", "programme": "ARTEMIS", "kind": "CREWED", "landed": "2027-01-01",
               "latitude": 0.0, "longitude": 0.0, "region": "R"},
              {"id": "c", "mission": "C", "programme": "LUNA", "kind": "LANDER", "landed": "1966-02-03",
               "latitude": 95.0, "longitude": 0.0, "region": "R"}
            ]}
            """.trimIndent(),
        )

        assertEquals(listOf("a"), sites.map { it.id })
    }

    /** Web Mercator pixel coordinates at zoom 6 (256 px tiles), the deepest zoom of the tiles. */
    private fun pixelAtZoom6(latitude: Double, longitude: Double): Pair<Double, Double> {
        val phi = Math.toRadians(latitude)
        val x = (longitude + 180.0) / 360.0 * WORLD_PIXELS_ZOOM_6
        val y = (1.0 - ln(tan(phi) + 1.0 / cos(phi)) / PI) / 2.0 * WORLD_PIXELS_ZOOM_6
        return x to y
    }

    private companion object {
        const val WORLD_PIXELS_ZOOM_6 = 256.0 * 64
        const val MERCATOR_LIMIT = 85.05112878
        const val MOON_CIRCUMFERENCE_M = 2 * PI * 1_737_400.0
    }
}
