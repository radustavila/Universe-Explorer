package com.radustavila.nasaexplorer.data.climate.remote

import com.radustavila.nasaexplorer.core.model.ClimatePoint
import com.radustavila.nasaexplorer.core.testing.http.Fixtures
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Each parser over the files as recorded on 23 October 2026, plus the odd cases they must survive. */
class ClimateFileParserTest {

    @Test
    fun `co2 reads the monthly means from May 1974 on`() {
        val points = ClimateFileParser.co2(Fixtures.read("climate/co2_mm_mlo.csv"))

        assertEquals(ClimatePoint(LocalDate.of(1974, 5, 1), 333.19), points.first())
        assertEquals(ClimatePoint(LocalDate.of(2026, 9, 1), 426.07), points.last())
        assertTrue(points.none { it.date.isBefore(ClimateFileParser.CO2_FIRST_MONTH) })
        assertEquals(points.sortedBy { it.date }, points)
    }

    @Test
    fun `co2 skips fill values and finds its columns by name`() {
        val text = """
            # comment, with a comma
            year,month,decimal date,new column,average,deseasonalized
            1980,1,1980.0417,x,337.80,338.10
            1980,2,1980.1250,x,-99.99,338.40
            1980,3,1980.2083,x,,338.50
            1980,4,1980.2917,x,340.00,338.80
        """.trimIndent()

        assertEquals(
            listOf(ClimatePoint(LocalDate.of(1980, 1, 1), 337.80), ClimatePoint(LocalDate.of(1980, 4, 1), 340.00)),
            ClimateFileParser.co2(text),
        )
    }

    @Test
    fun `gistemp reads every month and skips the missing ones`() {
        val points = ClimateFileParser.gistemp(Fixtures.read("climate/GLB.Ts+dSST.csv"))

        assertEquals(ClimatePoint(LocalDate.of(1880, 1, 1), -0.19), points.first())
        assertEquals(ClimatePoint(LocalDate.of(2026, 9, 1), 1.38), points.last())
        // 1880–2025 whole, nine months of 2026.
        assertEquals(146 * 12 + 9, points.size)
    }

    @Test
    fun `gistemp survives a changed title and reordered summary columns`() {
        val text = """
            Land-Ocean: Global Means (new title)
            Year,J-D,Jan,Feb,Mar,Apr,May,Jun,Jul,Aug,Sep,Oct,Nov,Dec
            2025,1.18,1.37,1.25,***,1.24,1.07,1.06,1.02,1.17,1.24,1.18,1.21,1.05
        """.trimIndent()

        val points = ClimateFileParser.gistemp(text)

        assertEquals(11, points.size)
        assertEquals(ClimatePoint(LocalDate.of(2025, 1, 1), 1.37), points.first())
        assertTrue(points.none { it.date.monthValue == 3 })
    }

    @Test
    fun `sea level takes the newest mission where they overlap`() {
        val points = ClimateFileParser.seaLevel(Fixtures.read("climate/slr_sla_gbl_free_ref_90.csv"))

        assertEquals(LocalDate.of(1992, 12, 17), points.first().date)
        assertEquals(-19.33, points.first().value, 0.0)
        assertEquals(ClimatePoint(LocalDate.of(2025, 2, 16), 83.60), points.last())
        assertEquals(points.sortedBy { it.date }, points)

        val text = """
            #title = mean sea level anomaly
            year,TOPEX/Poseidon,Jason-1
            2002.1000,10.00,12.00
            2002.2000,-99.99,13.00
            2002.3000,14.00,-99.99
            2002.4000,,
        """.trimIndent()
        assertEquals(listOf(12.0, 13.0, 14.0), ClimateFileParser.seaLevel(text).map { it.value })
    }

    @Test
    fun `sea ice reads the padded NSIDC columns and skips -9999`() {
        val september = ClimateFileParser.seaIce(Fixtures.read("climate/N_09_extent_v4.0.csv"))
        assertEquals(ClimatePoint(LocalDate.of(1979, 9, 1), 7.05), september.first())
        assertEquals(ClimatePoint(LocalDate.of(2026, 9, 1), 4.81), september.last())
        assertEquals(48, september.size)

        val january = ClimateFileParser.seaIce(Fixtures.read("climate/N_01_extent_v4.0.csv"))
        assertTrue(january.none { it.date.year == 1988 })
    }

    @Test
    fun `a file without its header or with an unknown layout gives nothing`() {
        assertEquals(emptyList<ClimatePoint>(), ClimateFileParser.co2("<html>Service unavailable</html>"))
        assertEquals(emptyList<ClimatePoint>(), ClimateFileParser.seaIce("year,month,area\n2026,9,3.12"))
        assertEquals(emptyList<ClimatePoint>(), ClimateFileParser.gistemp("Year,Jan,Feb\n2026,1.0,1.1"))
    }
}
