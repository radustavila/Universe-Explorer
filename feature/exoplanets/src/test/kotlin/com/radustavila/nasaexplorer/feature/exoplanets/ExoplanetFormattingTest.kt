package com.radustavila.nasaexplorer.feature.exoplanets

import java.time.YearMonth
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExoplanetFormattingTest {

    @Test
    fun `quantities keep decimals proportional to their size`() {
        assertEquals("0.089", formatQuantity(0.0892, Locale.US))
        assertEquals("0.74", formatQuantity(0.743, Locale.US))
        assertEquals("1.15", formatQuantity(1.15, Locale.US))
        assertEquals("13.5", formatQuantity(13.5, Locale.US))
        assertEquals("4,000", formatQuantity(4000.0, Locale.US))
        assertEquals("0", formatQuantity(0.0, Locale.US))
    }

    @Test
    fun `light-years show a decimal only nearby`() {
        assertEquals("4.2", formatLightYears(4.2439, Locale.US))
        assertEquals("1,326", formatLightYears(1325.8, Locale.US))
    }

    @Test
    fun `temperatures are whole degrees`() {
        assertEquals("394", formatWhole(394.0, Locale.US))
        assertEquals("-55", formatWhole(-55.15, Locale.US))
        assertEquals("1,200", formatWhole(1199.6, Locale.US))
    }

    @Test
    fun `formatting follows the locale`() {
        assertEquals("1,15", formatQuantity(1.15, Locale.GERMANY))
        assertEquals("6.451", formatCount(6451, Locale.GERMANY))
        assertEquals("Oct 2026", formatYearMonth(YearMonth.of(2026, 10), Locale.US))
    }

    @Test
    fun `planet dots grow with radius and stay inside their slot`() {
        val earth = dotFraction(1.0)
        val neptune = dotFraction(3.9)
        val jupiter = dotFraction(11.2)
        val huge = dotFraction(87.0)

        assertEquals(0.25f, earth, 0.001f)
        assertTrue(earth < neptune && neptune < jupiter)
        assertEquals(1f, huge, 0.0f)
        assertTrue(dotFraction(0.01) > 0f)
    }
}
