package com.radustavila.nasaexplorer.data.comets.remote

import com.radustavila.nasaexplorer.core.testing.http.Fixtures
import com.radustavila.nasaexplorer.data.comets.remote.dto.HorizonsResponseDto
import java.time.Instant
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HorizonsCometParserTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun result(path: String) = json.decodeFromString<HorizonsResponseDto>(Fixtures.read(path)).result!!

    @Test
    fun `the recorded table is 121 daily rows with positions, magnitudes and distances`() {
        val rows = HorizonsCometParser.parse(result("comets/horizons_2026_A2.json"))!!

        assertEquals(121, rows.size)
        assertEquals(Instant.parse("2026-08-24T00:00:00Z").toEpochMilli(), rows.first().epochMillis)
        assertEquals(Instant.parse("2026-12-22T00:00:00Z").toEpochMilli(), rows.last().epochMillis)
        // 2026-Oct-23 00:00, , ,   292.54598,   59.85362,   14.152,   n.a.,   2.074508644170, -7.4987477,
        //     1.69719869832713,-15.0053143,
        val today = rows.single { it.epochMillis == Instant.parse("2026-10-23T00:00:00Z").toEpochMilli() }
        assertEquals(292.54598, today.rightAscensionDegrees, 0.0)
        assertEquals(59.85362, today.declinationDegrees, 0.0)
        assertEquals(14.152, today.totalMagnitude!!, 0.0)
        assertEquals(2.074508644170, today.sunDistanceAu, 0.0)
        assertEquals(1.69719869832713, today.earthDistanceAu, 0.0)
    }

    @Test
    fun `columns are found by their header`() {
        val table = """
            | Date__(UT)__HR:MN, , , R.A._(ICRF), DEC_(ICRF),    T-mag,  N-mag,     r,  rdot,  delta, deldot, S-O-T,
            |${'$'}${'$'}SOE
            | 2026-Oct-23 00:00, , ,   10.5,   -20.25,   n.a.,   n.a.,   1.5, 1.0,  0.5, 1.0, 120.0,
            | 2026-Oct-24 00:00, *, ,  broken,   -20.25,   n.a.,   n.a.,   1.5, 1.0,  0.5, 1.0, 120.0,
            |${'$'}${'$'}EOE
        """.trimMargin()

        val row = HorizonsCometParser.parse(table)!!.single()

        assertEquals(10.5, row.rightAscensionDegrees, 0.0)
        assertNull(row.totalMagnitude)
        assertEquals(0.5, row.earthDistanceAu, 0.0)
    }

    @Test
    fun `an unknown designation has no table`() {
        assertNull(HorizonsCometParser.parse(result("comets/horizons_not_found.json")))
        assertNull(HorizonsCometParser.parse("\$\$SOE\n 2026-Oct-23 00:00, 1, 2\n\$\$EOE"))
    }
}
