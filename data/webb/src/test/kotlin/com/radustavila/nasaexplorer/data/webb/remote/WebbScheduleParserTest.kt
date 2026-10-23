package com.radustavila.nasaexplorer.data.webb.remote

import com.radustavila.nasaexplorer.core.testing.http.Fixtures
import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** STScI's weekly files as recorded on 2026-10-23, in both naming schemes, plus drifted and broken text. */
class WebbScheduleParserTest {

    private fun parse(file: String) = WebbScheduleParser.parse(Fixtures.read("webb/$file"))

    private fun List<ScheduledVisitDto>.visit(id: String) = single { it.visitId == id }

    @Test
    fun `this week's file reads every prime visit with its parallels`() {
        val visits = parse("20261019_report_20261022.txt")

        assertEquals(44, visits.size)
        val first = visits.first()
        assertEquals("7782:16:1", first.visitId)
        assertEquals(Instant.parse("2026-10-19T00:57:55Z"), first.start)
        assertEquals(Duration.ofHours(4).plusMinutes(11).plusSeconds(14), first.duration)
        assertEquals("PRIME TARGETED FIXED", first.visitType)
        assertEquals("NIRSpec MultiObject Spectroscopy", first.instrument)
        assertEquals("o16", first.targetName)
        assertNull(first.category)
        assertNull(first.keywords)
        // A coordinated parallel continues the row above.
        assertEquals(listOf("NIRCam Imaging"), first.parallels)
        // An attached parallel (its own visit id, no start) belongs to the prime above it.
        val nova = visits.visit("11318:3:1")
        assertEquals("V339-Del", nova.targetName)
        assertEquals("Star", nova.category)
        assertEquals("Novae", nova.keywords)
        assertEquals(listOf("NIRSpec Dark"), nova.parallels)
        // Two attached darks of one instrument count once.
        assertEquals(listOf("NIRCam Dark"), visits.visit("9016:1:2").parallels)
        assertTrue(visits.none { it.visitId == "12551:58:1" })
    }

    @Test
    fun `engineering visits without an instrument or target still read`() {
        val unload = parse("20261019_report_20261022.txt").visit("1000:1:43")

        assertEquals("FULL BIASED MOMENTUM UNLOAD", unload.visitType)
        assertEquals(Instant.parse("2026-10-21T07:22:32Z"), unload.start)
        assertEquals(Duration.ofHours(1).plusMinutes(12).plusSeconds(10), unload.duration)
        assertEquals("", unload.instrument)
        assertNull(unload.targetName)
    }

    @Test
    fun `a file in the old package-id naming scheme reads the same`() {
        val visits = parse("2219105f02_report_20220710.txt")

        assertEquals(70, visits.size)
        val wasp = visits.visit("1366:4:1")
        assertEquals(Instant.parse("2022-07-10T15:04:42Z"), wasp.start)
        assertEquals("NIRSpec Bright Object Time Series", wasp.instrument)
        assertEquals("WASP-39", wasp.targetName)
        assertEquals("Exoplanet Systems", wasp.keywords)
        assertEquals(
            "Astrometric, External flat field, Photometric",
            visits.visit("1476:1:1").keywords,
        )
    }

    @Test
    fun `a week with many attached parallels and long keyword lists`() {
        val visits = parse("20261012_report_20261008.txt")

        assertEquals(64, visits.size)
        assertEquals(listOf("MIRI Anneal"), visits.visit("6793:1:1").parallels)
        assertEquals(
            "Galaxy mergers, High-redshift clusters, Interacting galaxies",
            visits.visit("12610:4:1").keywords,
        )
        assertEquals("Clusters of Galaxies", visits.visit("12610:4:1").category)
        assertEquals(visits.sortedBy { it.start }, visits)
    }

    @Test
    fun `further target rows keep the visit's first target`() {
        val visits = parse("2227607f01_report_20221003.txt")

        assertEquals(46, visits.size)
        val galaxy = visits.visit("1567:3:1")
        assertEquals("Z6.1-6.2", galaxy.targetName)
        assertEquals("High-redshift galaxies", galaxy.keywords)
        assertEquals(listOf("NIRCam Imaging"), visits.visit("1180:29:1").parallels)
        assertTrue(visits.none { it.targetName == "BG-Z6.1-6.2" || it.targetName == "TA" })
    }

    @Test
    fun `values a few characters off their column still land in it`() {
        val text = Fixtures.read("webb/20261019_report_20261022.txt").lines().joinToString("\n") { line ->
            // Shift every data row three characters right of the header.
            if (line.firstOrNull()?.isDigit() == true || line.startsWith(" ")) "   $line" else line
        }

        val visits = WebbScheduleParser.parse(text)

        assertEquals(44, visits.size)
        assertEquals(parse("20261019_report_20261022.txt"), visits)
    }

    @Test
    fun `a start time glued to a long visit type and reordered columns read`() {
        val text = """
            VISIT ID       VISIT TYPE                    SCHEDULED START TIME  DURATION     TARGET NAME   SCIENCE INSTRUMENT AND MODE
            1000:1:43      FULL BIASED MOMENTUM UNLOADING 2026-10-21T07:22:32Z 00/01:12:10
            1366:4:1       PRIME TARGETED FIXED          2026-10-21T08:40:00Z  00/08:33:57  WASP-39       NIRSpec Bright Object Time Series
        """.trimIndent()

        val visits = WebbScheduleParser.parse(text)

        assertEquals(listOf("1000:1:43", "1366:4:1"), visits.map { it.visitId })
        assertEquals("FULL BIASED MOMENTUM UNLOADING", visits[0].visitType)
        assertEquals(Duration.ofMinutes(72).plusSeconds(10), visits[0].duration)
        assertEquals("WASP-39", visits[1].targetName)
        assertEquals("NIRSpec Bright Object Time Series", visits[1].instrument)
        assertNull(visits[1].category)
    }

    @Test
    fun `rows that do not read are skipped and text without a header is empty`() {
        val text = """
            VISIT ID       PCS MODE    VISIT TYPE                     SCHEDULED START TIME  DURATION     SCIENCE INSTRUMENT AND MODE
            1366:4:1       FINEGUIDE   PRIME TARGETED FIXED           2026-13-40T99:00:00Z  00/08:33:57  NIRSpec Bright Object Time Series
            1366:4:2       FINEGUIDE   PRIME TARGETED FIXED           2026-10-21T08:40:00Z  soon         NIRSpec Bright Object Time Series
            1366:4:3       FINEGUIDE   PRIME TARGETED FIXED           2026-10-21T18:40:00Z  00/01:00:00  NIRSpec Bright Object Time Series
        """.trimIndent()

        assertEquals(listOf("1366:4:3"), WebbScheduleParser.parse(text).map { it.visitId })
        assertTrue(WebbScheduleParser.parse("<html><body>Page not found</body></html>").isEmpty())
        assertTrue(WebbScheduleParser.parse("").isEmpty())
    }
}
