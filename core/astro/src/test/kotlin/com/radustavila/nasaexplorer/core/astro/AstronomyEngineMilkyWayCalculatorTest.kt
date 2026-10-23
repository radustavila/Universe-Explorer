package com.radustavila.nasaexplorer.core.astro

import com.radustavila.nasaexplorer.core.model.GeoCoordinate
import com.radustavila.nasaexplorer.core.model.MilkyWayNight
import com.radustavila.nasaexplorer.core.model.MilkyWayOutlook
import com.radustavila.nasaexplorer.core.model.MilkyWayUnavailable
import com.radustavila.nasaexplorer.core.model.NightDarkness
import java.time.Duration
import java.time.Instant
import java.time.Month
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The planner against the real sky. Twilight times are timeanddate.com's tables (read 2026-10-23),
 * for its coordinates of each city, converted from local time to UTC; they are printed to the
 * minute, and the calculator must agree within [TOLERANCE].
 */
class AstronomyEngineMilkyWayCalculatorTest {

    private val calculator = AstronomyEngineMilkyWayCalculator(AstronomyEngineDarkSkyCalculator())

    /** timeanddate's Madrid, 40°25′N 3°42′W; CEST is UTC+2. */
    private val madrid = GeoCoordinate(40.4167, -3.7)

    /** timeanddate's Sydney, 33°52′S 151°13′E; AEST is UTC+10. */
    private val sydney = GeoCoordinate(-33.8667, 151.2167)

    private val tromso = GeoCoordinate(69.65, 18.96)
    private val london = GeoCoordinate(51.51, -0.13)

    @Test
    fun `Madrid on 10 July 2026 matches the published twilight times`() {
        val night = calculator.nights(madrid, Instant.parse("2026-07-10T12:00:00Z"), count = 1).single()

        // 21:46, 22:18, 22:59, 23:47 on the 10th; 04:53, 05:40, 06:21, 06:54 on the 11th (CEST).
        with(night.twilight) {
            assertNear("2026-07-10T19:46:00Z", sunset)
            assertNear("2026-07-10T20:18:00Z", civilDusk)
            assertNear("2026-07-10T20:59:00Z", nauticalDusk)
            assertNear("2026-07-10T21:47:00Z", astronomicalDusk)
            assertNear("2026-07-11T02:53:00Z", astronomicalDawn)
            assertNear("2026-07-11T03:40:00Z", nauticalDawn)
            assertNear("2026-07-11T04:21:00Z", civilDawn)
            assertNear("2026-07-11T04:54:00Z", sunrise)
        }
    }

    @Test
    fun `Sydney on 16 May 2026 matches the published twilight times`() {
        val night = calculator.nights(sydney, Instant.parse("2026-05-16T00:00:00Z"), count = 1).single()

        // 17:02, 17:28, 17:58, 18:28 on the 16th; 05:14, 05:44, 06:14, 06:41 on the 17th (AEST).
        with(night.twilight) {
            assertNear("2026-05-16T07:02:00Z", sunset)
            assertNear("2026-05-16T07:28:00Z", civilDusk)
            assertNear("2026-05-16T07:58:00Z", nauticalDusk)
            assertNear("2026-05-16T08:28:00Z", astronomicalDusk)
            assertNear("2026-05-16T19:14:00Z", astronomicalDawn)
            assertNear("2026-05-16T19:44:00Z", nauticalDawn)
            assertNear("2026-05-16T20:14:00Z", civilDawn)
            assertNear("2026-05-16T20:41:00Z", sunrise)
        }
    }

    @Test
    fun `golden and blue hours sit on either side of sunset and sunrise`() {
        val night = calculator.nights(madrid, Instant.parse("2026-07-10T12:00:00Z"), count = 1).single()

        val evening = night.evening
        val morning = night.morning
        assertTrue(evening.goldenHour!!.start.isBefore(night.twilight.sunset))
        assertTrue(evening.goldenHour!!.end.isAfter(night.twilight.sunset))
        assertEquals(evening.goldenHour!!.end, evening.blueHour!!.start)
        assertEquals(night.twilight.civilDusk, evening.blueHour!!.end)
        assertEquals(night.twilight.civilDawn, morning.blueHour!!.start)
        assertEquals(morning.blueHour!!.end, morning.goldenHour!!.start)
        assertTrue(morning.goldenHour!!.end.isAfter(night.twilight.sunrise))
        // In July the Sun takes about 50 minutes from 6° up to 4° down at Madrid's latitude.
        val golden = evening.goldenHour!!.duration
        assertTrue(golden.toString(), golden > Duration.ofMinutes(40) && golden < Duration.ofMinutes(70))
    }

    @Test
    fun `a July night in Madrid has a window with the core low in the south`() {
        val night = calculator.nights(madrid, Instant.parse("2026-07-10T12:00:00Z"), count = 1).single()

        val window = checkNotNull(night.window)
        // Dark from 23:47, when the core is already near its highest (about 20.6°).
        assertNear(night.twilight.astronomicalDusk!!, window.start)
        assertTrue(window.duration > Duration.ofHours(1))
        assertTrue(window.coreAtBest.altitudeDegrees in 18.0..21.5)
        assertTrue(window.coreAtBest.azimuthDegrees in 160.0..200.0)
        assertNotNull(night.coreTransit)
        assertTrue(night.coreUp.isNotEmpty())
    }

    @Test
    fun `at new Moon in Sydney the window runs from the core clearing 10 degrees until dawn`() {
        // New Moon on 16 May 2026 at about 20:01 UTC.
        val night = calculator.nights(sydney, Instant.parse("2026-05-16T00:00:00Z"), count = 1).single()

        assertTrue(night.moonIlluminatedFraction < 0.05)
        val window = checkNotNull(night.window)
        assertNear(night.twilight.astronomicalDawn!!, window.end)
        assertTrue(window.start.isAfter(night.twilight.astronomicalDusk))
        // The core nearly overhead from the south.
        assertTrue(window.coreAtBest.altitudeDegrees > 70.0)
        val rise = checkNotNull(night.coreRise)
        assertTrue(rise.isBefore(window.start))
    }

    @Test
    fun `a bright Moon that is up closes the window`() {
        // Full Moon on 29 July 2026: up all night, 99 % lit.
        val night = calculator.nights(madrid, Instant.parse("2026-07-29T12:00:00Z"), count = 1).single()

        assertTrue(night.moonIlluminatedFraction > 0.9)
        assertTrue(night.moonUp.isNotEmpty())
        assertNull(night.window)
    }

    @Test
    fun `polar summer in Tromso has no darkness and no window`() {
        val nights = calculator.nights(tromso, Instant.parse("2026-06-21T12:00:00Z"), count = 5)

        assertEquals(5, nights.size)
        nights.forEach { night ->
            assertEquals(NightDarkness.NONE, night.darkness)
            assertNull(night.twilight.sunset)
            assertNull(night.window)
            assertTrue(night.coreUp.isEmpty())
        }
        nights.zipWithNext { night, next -> assertFalse(next.start.isBefore(night.end)) }
        assertEquals(MilkyWayUnavailable.CORE_TOO_LOW, outlook(tromso, nights).unavailable)
    }

    @Test
    fun `June at 50 degrees north is too light even though the core clears 10 degrees`() {
        val observer = GeoCoordinate(50.0, 5.0)
        val nights = calculator.nights(observer, Instant.parse("2026-06-15T12:00:00Z"), count = 5)

        assertTrue(nights.none { it.darkness == NightDarkness.ASTRONOMICAL })
        assertEquals(MilkyWayUnavailable.NO_DARKNESS, outlook(observer, nights).unavailable)
    }

    @Test
    fun `in December the core is up only by day`() {
        val nights = calculator.nights(madrid, Instant.parse("2026-12-10T12:00:00Z"), count = 5)

        assertTrue(nights.all { it.window == null })
        assertEquals(MilkyWayUnavailable.CORE_UP_BY_DAY, outlook(madrid, nights).unavailable)
    }

    @Test
    fun `the season is spring to autumn in Madrid, mid-year in Sydney and never in London or Tromso`() {
        val madridSeason = calculator.season(madrid, 2026)
        assertTrue(madridSeason.containsAll(listOf(Month.APRIL, Month.JUNE, Month.JULY, Month.AUGUST)))
        assertFalse(Month.DECEMBER in madridSeason)

        val sydneySeason = calculator.season(sydney, 2026)
        assertTrue(sydneySeason.containsAll(listOf(Month.APRIL, Month.JUNE, Month.AUGUST)))
        assertFalse(Month.DECEMBER in sydneySeason)

        assertTrue(calculator.season(london, 2026).isEmpty())
        assertTrue(calculator.season(tromso, 2026).isEmpty())
    }

    @Test
    fun `thirty nights follow one another`() {
        val nights = calculator.nights(sydney, Instant.parse("2026-05-01T00:00:00Z"), count = 30)

        assertEquals(30, nights.size)
        nights.zipWithNext { night, next ->
            val gap = Duration.between(night.start, next.start)
            assertTrue(gap.toString(), gap > Duration.ofHours(23) && gap < Duration.ofHours(25))
        }
        // Around the new Moon of 16 May there is a window every night.
        assertTrue(nights.count { it.window != null } >= 7)
    }

    private fun outlook(observer: GeoCoordinate, nights: List<MilkyWayNight>) =
        MilkyWayOutlook(observer = observer, nights = nights, season = emptySet())

    private fun assertNear(expected: String, actual: Instant?) = assertNear(Instant.parse(expected), actual)

    private fun assertNear(expected: Instant, actual: Instant?) {
        assertNotNull("expected about $expected", actual)
        val difference = Duration.between(expected, actual).abs()
        assertTrue("$actual is $difference from $expected", difference <= TOLERANCE)
    }

    private companion object {
        val TOLERANCE: Duration = Duration.ofMinutes(2)
    }
}
