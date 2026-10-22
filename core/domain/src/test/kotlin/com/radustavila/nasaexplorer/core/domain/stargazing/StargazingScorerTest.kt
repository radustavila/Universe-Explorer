package com.radustavila.nasaexplorer.core.domain.stargazing

import com.radustavila.nasaexplorer.core.model.MoonDuringWindow
import com.radustavila.nasaexplorer.core.model.NightDarkness
import com.radustavila.nasaexplorer.core.model.StargazingRating
import com.radustavila.nasaexplorer.core.testing.fake.TestStargazing
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StargazingScorerTest {

    // The night is dark from 20:00 to 04:00 UTC; the forecast starts at noon, hourly for two days.
    private val noon = Instant.parse("2026-09-10T12:00:00Z")
    private val darkStart = Instant.parse("2026-09-10T20:00:00Z")
    private val darkEnd = Instant.parse("2026-09-11T04:00:00Z")

    @Test
    fun `a clear moonless night scores full marks and is clear all night`() {
        val night = TestStargazing.darkNight(darkStart = darkStart, moonAltitude = -30.0, moonIlluminated = 0.02)

        val scored = StargazingScorer.score(night, TestStargazing.cloudForecast(from = noon) { 0.0 }, now = noon)

        assertEquals(100, scored.score)
        assertEquals(StargazingRating.EXCELLENT, scored.rating)
        val window = checkNotNull(scored.bestWindow)
        assertEquals(darkStart, window.start)
        assertEquals(darkEnd, window.end)
        assertEquals(MoonDuringWindow.DOWN, window.moon)
        assertEquals(0.0, scored.meanCloudCover!!, 0.0)
    }

    @Test
    fun `an overcast night scores poorly and has no window`() {
        val night = TestStargazing.darkNight(darkStart = darkStart)

        val scored = StargazingScorer.score(night, TestStargazing.cloudForecast(from = noon) { 100.0 }, now = noon)

        assertEquals(0, scored.score)
        assertEquals(StargazingRating.POOR, scored.rating)
        assertNull(scored.bestWindow)
        assertEquals(100.0, scored.meanCloudCover!!, 0.0)
    }

    @Test
    fun `a full Moon up all night halves a clear night and the window says so`() {
        val night = TestStargazing.darkNight(darkStart = darkStart, moonAltitude = 40.0, moonIlluminated = 1.0)

        val scored = StargazingScorer.score(night, TestStargazing.cloudForecast(from = noon) { 0.0 }, now = noon)

        assertEquals(50, scored.score)
        assertEquals(StargazingRating.FAIR, scored.rating)
        assertEquals(MoonDuringWindow.UP, scored.bestWindow?.moon)
    }

    @Test
    fun `a night that clears after midnight scores its clear hours and shows them as the window`() {
        val night = TestStargazing.darkNight(darkStart = darkStart)
        // Overcast until 01:00 UTC (hour 13 of the forecast), clear from then on.
        val forecast = TestStargazing.cloudForecast(from = noon) { hour -> if (hour < 13) 100.0 else 0.0 }

        val scored = StargazingScorer.score(night, forecast, now = noon)

        assertEquals(100, scored.score)
        assertEquals(Instant.parse("2026-09-11T01:00:00Z"), scored.bestWindow?.start)
        assertEquals(darkEnd, scored.bestWindow?.end)
    }

    @Test
    fun `low cloud costs more than the same cover of high cloud`() {
        val night = TestStargazing.darkNight(darkStart = darkStart)
        val high = TestStargazing.cloudForecast(from = noon, lowCloud = { 0.0 }) { 40.0 }
        val low = TestStargazing.cloudForecast(from = noon, lowCloud = { 40.0 }) { 40.0 }

        val highScore = StargazingScorer.score(night, high, now = noon).score!!
        val lowScore = StargazingScorer.score(night, low, now = noon).score!!

        assertEquals(70, highScore)
        assertEquals(60, lowScore)
    }

    @Test
    fun `fog spoils an otherwise clear night`() {
        val night = TestStargazing.darkNight(darkStart = darkStart)

        val foggy = TestStargazing.cloudForecast(from = noon, fog = { 80.0 }) { 0.0 }
        val scored = StargazingScorer.score(night, foggy, noon)

        assertEquals(20, scored.score)
        assertNull(scored.bestWindow)
    }

    @Test
    fun `the Moon rising partway through is reported, and only its hours lose points`() {
        // Down for the first five hours, up for the last three.
        val altitudes = List(8) { hour -> if (hour < 5) -10.0 else 10.0 }
        val night = TestStargazing.darkNight(darkStart = darkStart, moonIlluminated = 0.8, moonAltitudes = altitudes)

        val scored = StargazingScorer.score(night, TestStargazing.cloudForecast(from = noon) { 0.0 }, now = noon)

        assertEquals(100, scored.score)
        assertEquals(MoonDuringWindow.RISES, scored.bestWindow?.moon)
    }

    @Test
    fun `nautical darkness caps the score`() {
        val night = TestStargazing.darkNight(darkStart = darkStart, darkness = NightDarkness.NAUTICAL)

        val scored = StargazingScorer.score(night, TestStargazing.cloudForecast(from = noon) { 0.0 }, now = noon)

        assertEquals(80, scored.score)
    }

    @Test
    fun `a night that never gets dark has no score`() {
        val night = TestStargazing.darkNight(darkStart = darkStart).copy(
            darkness = NightDarkness.NONE,
            darkStart = null,
            darkEnd = null,
            moonSamples = emptyList(),
        )

        val scored = StargazingScorer.score(night, TestStargazing.cloudForecast(from = noon) { 0.0 }, now = noon)

        assertNull(scored.score)
        assertNull(scored.bestWindow)
    }

    @Test
    fun `a night beyond the forecast, or without one, has no score`() {
        val night = TestStargazing.darkNight(darkStart = darkStart)
        // Six hours of forecast: it ends at 18:00, before the night starts.
        val short = TestStargazing.cloudForecast(from = noon, hours = 6) { 0.0 }

        assertNull(StargazingScorer.score(night, short, now = noon).score)
        assertNull(StargazingScorer.score(night, forecast = null, now = noon).score)
    }

    @Test
    fun `6-hourly steps later in the forecast still cover each dark hour`() {
        val night = TestStargazing.darkNight(darkStart = darkStart)
        val sixHourly = TestStargazing.cloudForecast(from = noon, hours = 48) { 0.0 }.let { forecast ->
            forecast.copy(steps = forecast.steps.filterIndexed { index, _ -> index % 6 == 0 })
        }

        assertEquals(100, StargazingScorer.score(night, sixHourly, now = noon).score)
    }

    @Test
    fun `hours already gone drop out of a night under way`() {
        val night = TestStargazing.darkNight(darkStart = darkStart)
        // Clear until 02:00 UTC, then overcast; it is now 02:30.
        val forecast = TestStargazing.cloudForecast(from = noon) { hour -> if (hour < 14) 0.0 else 100.0 }
        val now = Instant.parse("2026-09-11T02:30:00Z")

        val scored = StargazingScorer.score(night, forecast, now = now)

        assertEquals(0, scored.score)
        assertNull(scored.bestWindow)
        assertTrue(StargazingScorer.score(night, forecast, now = noon).score!! > 0)
    }
}
