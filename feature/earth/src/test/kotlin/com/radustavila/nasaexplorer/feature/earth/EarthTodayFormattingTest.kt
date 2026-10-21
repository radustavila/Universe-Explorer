package com.radustavila.nasaexplorer.feature.earth

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EarthTodayFormattingTest {

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
