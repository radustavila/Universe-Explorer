package com.radustavila.nasaexplorer.feature.feed

import androidx.compose.material3.ExperimentalMaterial3Api
import com.radustavila.nasaexplorer.core.model.Apod
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalMaterial3Api::class)
class PublishedDatesTest {

    private val today = LocalDate.of(2026, 9, 10)
    private val selectableDates = PublishedDates(today)

    @Test
    fun `today is selectable`() {
        assertTrue(selectableDates.isSelectableDate(today.toUtcMillis()))
    }

    @Test
    fun `future days are not selectable`() {
        assertFalse(selectableDates.isSelectableDate(today.plusDays(1).toUtcMillis()))
    }

    @Test
    fun `the first published day is selectable but the day before is not`() {
        val first = Apod.FIRST_PUBLICATION_DATE

        assertTrue(selectableDates.isSelectableDate(first.toUtcMillis()))
        assertFalse(selectableDates.isSelectableDate(first.minusDays(1).toUtcMillis()))
    }

    @Test
    fun `only years the archive covers are selectable`() {
        assertFalse(selectableDates.isSelectableYear(Apod.FIRST_PUBLICATION_DATE.year - 1))
        assertTrue(selectableDates.isSelectableYear(2005))
        assertTrue(selectableDates.isSelectableYear(today.year))
        assertFalse(selectableDates.isSelectableYear(today.year + 1))
    }

    @Test
    fun `the picker's milliseconds round-trip to the same UTC date`() {
        assertEquals(today, today.toUtcMillis().toUtcLocalDate())
    }
}
