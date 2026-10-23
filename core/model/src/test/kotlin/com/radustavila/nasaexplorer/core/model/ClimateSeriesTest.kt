package com.radustavila.nasaexplorer.core.model

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ClimateSeriesTest {

    private fun monthly(vararg values: Pair<String, Double>) = ClimateSeries(
        ClimateIndicator.CO2,
        values.map { (date, value) -> ClimatePoint(LocalDate.parse(date), value) },
    )

    @Test
    fun `the change compares the latest value with the same month of 1980`() {
        val series = monthly(
            "1979-09-01" to 335.0,
            "1980-03-01" to 340.0,
            "1980-09-01" to 336.8,
            "2026-03-01" to 430.0,
            "2026-09-01" to 426.07,
        )

        val change = series.change()

        assertEquals(1980, change?.sinceYear)
        assertEquals(426.07 - 336.8, change!!.delta, 1e-9)
    }

    @Test
    fun `a record starting later compares with its first year that has the same time of year`() {
        val series = ClimateSeries(
            ClimateIndicator.SEA_LEVEL,
            listOf(
                ClimatePoint(LocalDate.of(1992, 12, 17), -19.3),
                ClimatePoint(LocalDate.of(1993, 2, 10), -24.0),
                ClimatePoint(LocalDate.of(1993, 6, 1), -20.0),
                ClimatePoint(LocalDate.of(2025, 2, 16), 83.6),
            ),
        )

        assertEquals(ClimateChange(sinceYear = 1993, delta = 83.6 + 24.0), series.change())
    }

    @Test
    fun `no value near the same time of year, no change`() {
        assertNull(monthly("1980-01-01" to 1.0, "2026-09-01" to 2.0).change())
        assertNull(monthly("2026-09-01" to 2.0).change())
        assertNull(ClimateSeries(ClimateIndicator.CO2, emptyList()).change())
    }

    @Test
    fun `keys are stable and found again`() {
        assertEquals(
            listOf("co2", "global_temperature", "sea_level", "arctic_sea_ice"),
            ClimateIndicator.entries.map { it.key },
        )
        assertEquals(ClimateIndicator.SEA_LEVEL, ClimateIndicator.fromKey("sea_level"))
        assertNull(ClimateIndicator.fromKey("ozone"))
    }
}
