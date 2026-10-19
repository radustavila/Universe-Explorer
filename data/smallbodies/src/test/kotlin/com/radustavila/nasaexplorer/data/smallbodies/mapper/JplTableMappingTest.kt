package com.radustavila.nasaexplorer.data.smallbodies.mapper

import com.radustavila.nasaexplorer.core.testing.http.Fixtures
import com.radustavila.nasaexplorer.data.smallbodies.SmallBodiesFixtures
import com.radustavila.nasaexplorer.data.smallbodies.remote.dto.JplTableDto
import com.radustavila.nasaexplorer.data.smallbodies.testJson
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** CAD and fireball answers are arrays of arrays; every value is found by its column's name. */
class JplTableMappingTest {

    private fun table(path: String): JplTableDto = testJson.decodeFromString(Fixtures.read(path))

    @Test
    fun `close approaches are read by field name from the recorded CAD answer`() {
        val approaches = table(SmallBodiesFixtures.CAD).toCloseApproachEntities()

        assertEquals(30, approaches.size)
        val first = approaches.first()
        assertEquals("2026 UK", first.designation)
        assertEquals("2026 UK", first.name)
        assertEquals(Instant.parse("2026-10-19T04:38:00Z").toEpochMilli(), first.time)
        assertEquals(0.0194736855784829, first.distanceAu, 0.0)
        assertEquals(15.1034118016831, first.relativeVelocityKmPerSecond, 0.0)
        assertEquals(25.014, first.absoluteMagnitude!!, 0.0)
        assertNull(first.diameterKm)
        assertEquals("524522 Zoozve (2002 VE68)", approaches.first { it.designation == "524522" }.name)
    }

    @Test
    fun `the same columns in another order map to the same values`() {
        val reordered = testJson.decodeFromString<JplTableDto>(
            """
            {"fields":["fullname","v_rel","cd","h","dist","des"],
             "data":[["       (2026 UK)","15.1034118016831","2026-Oct-19 04:38","25.014","0.0194736855784829","2026 UK"]]}
            """.trimIndent(),
        )

        assertEquals(
            table(SmallBodiesFixtures.CAD).toCloseApproachEntities().first(),
            reordered.toCloseApproachEntities().single(),
        )
    }

    @Test
    fun `rows missing a required column are dropped, an empty answer maps to nothing`() {
        val partial = testJson.decodeFromString<JplTableDto>(
            """{"fields":["des","cd","dist"],"data":[["2026 UK","2026-Oct-19 04:38","0.019"]]}""",
        )
        val empty = testJson.decodeFromString<JplTableDto>("""{"signature":{"version":"1.5"},"count":0}""")

        assertTrue(partial.toCloseApproachEntities().isEmpty())
        assertTrue(empty.toCloseApproachEntities().isEmpty())
    }

    @Test
    fun `fireballs get signed coordinates and joules`() {
        val fireballs = table(SmallBodiesFixtures.FIREBALLS).toFireballEntities()

        assertEquals(20, fireballs.size)
        val first = fireballs.first()
        assertEquals(Instant.parse("2026-10-04T03:14:45Z").toEpochMilli(), first.time)
        assertEquals(5.4e10, first.radiatedEnergyJoules, 1.0)
        assertEquals(0.18, first.impactEnergyKilotons, 0.0)
        assertEquals(-41.8, first.latitude!!, 0.0)
        assertEquals(-173.1, first.longitude!!, 0.0)
        assertNull(fireballs[1].velocityKmPerSecond)
        assertEquals(54.4, fireballs[3].latitude!!, 0.0)
    }
}
