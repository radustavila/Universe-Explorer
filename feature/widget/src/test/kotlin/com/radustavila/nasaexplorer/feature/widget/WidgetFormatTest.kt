package com.radustavila.nasaexplorer.feature.widget

import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WidgetFormatTest {

    private val format = testWidgetFormat()
    private val now = Instant.parse("2026-10-19T14:20:00Z")

    @Test
    fun `times on the same day leave the day out`() {
        assertEquals("21:43", format.dayAndTime(Instant.parse("2026-10-19T21:43:00Z"), now))
        assertEquals("Tue 20 Oct 02:07", format.dayAndTime(Instant.parse("2026-10-20T02:07:31Z"), now))
    }

    @Test
    fun `the countdown has minutes under a day and hours beyond`() {
        assertEquals("in 7 m", format.timeUntil(now.plus(Duration.ofMinutes(7)), now))
        assertEquals("in 3 h 20 m", format.timeUntil(now.plus(Duration.ofMinutes(200)), now))
        assertEquals("in 2 d 5 h", format.timeUntil(now.plus(Duration.ofHours(53).plusMinutes(40)), now))
        assertEquals("in 0 m", format.timeUntil(now.minusSeconds(30), now))
    }

    @Test
    fun `ages are whole minutes, hours or days`() {
        assertEquals("45 m old", format.age(now.minus(Duration.ofMinutes(45)), now))
        assertEquals("5 h old", format.age(now.minus(Duration.ofMinutes(5 * 60 + 59)), now))
        assertEquals("2 d old", format.age(now.minus(Duration.ofDays(2)), now))
    }

    @Test
    fun `decimals follow the locale`() {
        assertEquals("4.3", format.decimal(4.33, fractionDigits = 1))
    }
}
