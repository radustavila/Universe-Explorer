package com.radustavila.nasaexplorer.feature.earth

import com.radustavila.nasaexplorer.core.model.GeoPoint
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EarthTodayFormattingTest {

    @Test
    fun `coordinates read as compass bearings`() {
        assertEquals("0.7° N, 163.7° E", GeoPoint(0.65918, 163.696289).toCoordinateLabel())
        assertEquals("12.3° S, 45.0° W", GeoPoint(-12.34, -45.0).toCoordinateLabel())
    }

    @Test
    fun `only published days and their years are selectable`() {
        val published = LocalDate.of(2026, 10, 16)
        val gap = LocalDate.of(2026, 10, 9)
        val dates = AvailableEpicDates(setOf(published, LocalDate.of(2015, 6, 13)))

        assertTrue(dates.isSelectableDate(published.toUtcMillis()))
        assertFalse(dates.isSelectableDate(gap.toUtcMillis()))
        assertTrue(dates.isSelectableYear(2015))
        assertFalse(dates.isSelectableYear(2020))
        assertEquals(2015..2026, dates.yearRange)
    }
}
