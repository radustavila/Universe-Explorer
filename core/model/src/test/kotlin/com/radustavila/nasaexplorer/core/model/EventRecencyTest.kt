package com.radustavila.nasaexplorer.core.model

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EventRecencyTest {

    private val today = LocalDate.of(2026, 10, 22)

    private fun event(category: EarthEventCategory, lastReport: Instant) = EarthEvent(
        id = "EONET_1",
        title = "Event",
        category = category,
        sources = emptyList(),
        geometry = listOf(
            EarthEventGeometry(Instant.parse("2026-01-01T00:00:00Z"), EarthEventShape.Point(GeoCoordinate(0.0, 0.0))),
            EarthEventGeometry(lastReport, EarthEventShape.Point(GeoCoordinate(1.0, 1.0))),
        ),
    )

    private fun wildfireReportedDaysAgo(days: Long) =
        event(EarthEventCategory.WILDFIRES, today.minusDays(days).atTime(23, 59).toInstant(ZoneOffset.UTC))

    @Test
    fun `age counts UTC days since the latest report, not since the event opened`() {
        assertEquals(0L, wildfireReportedDaysAgo(0).reportAgeDays(today))
        assertEquals(9L, wildfireReportedDaysAgo(9).reportAgeDays(today))
    }

    @Test
    fun `a report dated after today counts as today`() {
        assertEquals(0L, wildfireReportedDaysAgo(-1).reportAgeDays(today))
    }

    @Test
    fun `wildfire tiers change between 7 and 8, 14 and 15, 42 and 43 days`() {
        assertEquals(EventRecency.Current, wildfireReportedDaysAgo(0).recency(today))
        assertEquals(EventRecency.Current, wildfireReportedDaysAgo(7).recency(today))
        assertEquals(EventRecency.Latest, wildfireReportedDaysAgo(8).recency(today))
        assertEquals(EventRecency.Latest, wildfireReportedDaysAgo(14).recency(today))
        assertEquals(EventRecency.History, wildfireReportedDaysAgo(15).recency(today))
        assertEquals(EventRecency.History, wildfireReportedDaysAgo(42).recency(today))
        assertEquals(EventRecency.Expired, wildfireReportedDaysAgo(43).recency(today))
    }

    @Test
    fun `only current and latest are shown by default`() {
        assertTrue(EventRecency.Current.isShownByDefault)
        assertTrue(EventRecency.Latest.isShownByDefault)
        assertFalse(EventRecency.History.isShownByDefault)
        assertFalse(EventRecency.Expired.isShownByDefault)
    }

    @Test
    fun `other categories do not age`() {
        val volcano = event(EarthEventCategory.VOLCANOES, today.minusDays(60).atStartOfDay().toInstant(ZoneOffset.UTC))

        assertEquals(EventRecency.Current, volcano.recency(today))
    }
}
