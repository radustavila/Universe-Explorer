package com.radustavila.nasaexplorer.data.comets.remote

import com.radustavila.nasaexplorer.core.testing.http.Fixtures
import com.radustavila.nasaexplorer.data.comets.remote.dto.SbdbQueryDto
import java.time.Instant
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SbdbCometQueryTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `the filter asks for bright comets with a perihelion from 200 days ago to 350 days ahead`() {
        // 2026-10-23 00:00 UTC is JD 2461336.5.
        val constraints = SbdbCometQuery.constraints(Instant.parse("2026-10-23T00:00:00Z"))

        assertEquals("""{"AND":["M1|LT|11","q|LT|2.5","tp|RG|2461136.5|2461686.5"]}""", constraints)
        assertEquals(listOf("full_name", "pdes", "M1", "K1", "q", "e", "tp"), SbdbCometQuery.FIELDS)
    }

    @Test
    fun `the recorded answer becomes five candidates with trimmed names`() {
        val table = json.decodeFromString<SbdbQueryDto>(Fixtures.read("comets/sbdb_comets.json"))

        val candidates = SbdbCometQuery.candidates(table)

        assertEquals(
            listOf("2018 VN2", "2025 V2", "2026 A2", "2026 AZ17", "2026 O1"),
            candidates.map { it.designation },
        )
        val bok = candidates.single { it.designation == "2026 A2" }
        assertEquals("C/2026 A2 (Bok)", bok.fullName)
        assertEquals(9.2, bok.absoluteMagnitude!!, 0.0)
        assertEquals(12.0, bok.slopeParameter!!, 0.0)
        assertEquals(1.938, bok.perihelionDistanceAu, 0.0)
        assertEquals(0.9976, bok.eccentricity, 0.0)
        // JD 2461396.85 is 2026-12-22 08:24 TDB.
        val perihelion = SbdbCometQuery.julianDateToInstant(bok.perihelionJulianDate!!)
        assertEquals(Instant.parse("2026-12-22T08:24:00Z"), perihelion)
    }

    @Test
    fun `an empty answer has no candidates, a row without an orbit is dropped`() {
        val empty = json.decodeFromString<SbdbQueryDto>(Fixtures.read("comets/sbdb_empty.json"))
        val partial = json.decodeFromString<SbdbQueryDto>(
            """{"fields":["full_name","pdes","M1","K1","q","e","tp"],"data":[
                ["  C/2099 A1 (Test)","2099 A1",null,null,"1.0","0.99",null],
                ["  C/2099 B1 (Test)","2099 B1","8.0","10.",null,"0.99","2488000.5"]]}""",
        )

        assertTrue(SbdbCometQuery.candidates(empty).isEmpty())
        val kept = SbdbCometQuery.candidates(partial).single()
        assertEquals("2099 A1", kept.designation)
        assertNull(kept.absoluteMagnitude)
        assertNull(kept.perihelionJulianDate)
    }
}
