package com.radustavila.nasaexplorer.data.dsn.remote

import com.radustavila.nasaexplorer.core.testing.http.Fixtures
import com.radustavila.nasaexplorer.data.dsn.remote.dto.HorizonsResponseDto
import java.time.Instant
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Horizons observer tables recorded on 2026-10-22 for 2026-10-22 00:00 UTC. */
class HorizonsEphemerisParserTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun result(fixture: String): String =
        json.decodeFromString<HorizonsResponseDto>(Fixtures.read("dsn/$fixture")).result!!

    @Test
    fun `voyager 1 is 172 point 65 AU from Earth on the recorded date`() {
        val range = HorizonsEphemerisParser.parseRange(-31, result("horizons_31.json"))!!

        assertEquals("Voyager 1", range.name)
        assertEquals(Instant.parse("2026-10-22T00:00:00Z").toEpochMilli(), range.epochMillis)
        // Horizons' value, within the brief's 0.01 AU.
        assertEquals(172.65, range.deltaAu, 0.01)
        assertEquals(34.8813474, range.rangeRateKmPerSecond!!, 1e-9)
    }

    @Test
    fun `names drop the spacecraft tag and id`() {
        assertEquals("Voyager 2", HorizonsEphemerisParser.parseRange(-32, result("horizons_32.json"))!!.name)
        assertEquals("New Horizons", HorizonsEphemerisParser.parseRange(-98, result("horizons_98.json"))!!.name)
        assertEquals(
            "James Webb Space Telescope",
            HorizonsEphemerisParser.parseRange(-170, result("horizons_170.json"))!!.name,
        )
    }

    @Test
    fun `an approaching spacecraft has a negative range rate`() {
        val juno = HorizonsEphemerisParser.parseRange(-61, result("horizons_61.json"))!!

        assertEquals(5.63873634984826, juno.deltaAu, 1e-12)
        assertEquals(-25.9331973, juno.rangeRateKmPerSecond!!, 1e-9)
    }

    @Test
    fun `text without an ephemeris is not a range`() {
        assertNull(HorizonsEphemerisParser.parseRange(-31, "No such record, positive values only"))
        assertNull(
            HorizonsEphemerisParser.parseRange(
                -31,
                "Target body name: Voyager 1 (spacecraft) (-31)\n\$\$SOE\n 2026-Oct-22 00:00  n.a.  n.a.\n\$\$EOE",
            ),
        )
    }
}
