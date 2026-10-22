package com.radustavila.nasaexplorer.core.astro

import com.radustavila.nasaexplorer.core.domain.stargazing.StargazingScorer
import com.radustavila.nasaexplorer.core.model.CloudForecast
import com.radustavila.nasaexplorer.core.model.CloudForecastStep
import com.radustavila.nasaexplorer.core.model.GeoCoordinate
import com.radustavila.nasaexplorer.core.model.MoonDuringWindow
import com.radustavila.nasaexplorer.core.model.NightDarkness
import com.radustavila.nasaexplorer.core.model.StargazingRating
import io.github.cosinekitty.astronomy.Time
import io.github.cosinekitty.astronomy.searchMoonPhase
import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The calculator against the real sky, and the score on top of it: a clear night at new Moon, at
 * full Moon, and an overcast one, with a fixed clock.
 */
class AstronomyEngineDarkSkyCalculatorTest {

    private val calculator = AstronomyEngineDarkSkyCalculator()
    private val bucharest = GeoCoordinate(44.43, 26.10)

    @Test
    fun `an October night in Bucharest is astronomically dark from early evening to before dawn`() {
        val now = Instant.parse("2026-10-22T12:00:00Z")

        val night = calculator.nights(bucharest, now, count = 1).single()

        assertEquals(NightDarkness.ASTRONOMICAL, night.darkness)
        // Sunset about 15:25 UTC (18:25 local), astronomical dusk about 16:55, dawn about 03:10.
        assertNear(Instant.parse("2026-10-22T15:25:00Z"), night.sunset!!, Duration.ofMinutes(10))
        assertNear(Instant.parse("2026-10-22T16:55:00Z"), night.darkStart!!, Duration.ofMinutes(15))
        assertNear(Instant.parse("2026-10-23T03:10:00Z"), night.darkEnd!!, Duration.ofMinutes(15))
        assertTrue(night.sunrise!!.isAfter(night.darkEnd))
        assertEquals(night.darkStart, night.moonSamples.first().time)
        assertTrue(night.moonSamples.last().time.isBefore(night.darkEnd))
    }

    @Test
    fun `five nights follow one another a day apart`() {
        val nights = calculator.nights(bucharest, Instant.parse("2026-10-22T12:00:00Z"), count = 5)

        assertEquals(5, nights.size)
        nights.zipWithNext { night, next ->
            val gap = Duration.between(night.darkStart, next.darkStart)
            assertTrue(gap.toString(), gap > Duration.ofHours(23) && gap < Duration.ofHours(25))
        }
    }

    @Test
    fun `during the night the night under way is tonight, after its dark part the coming one`() {
        val evening = calculator.nights(bucharest, Instant.parse("2026-10-22T12:00:00Z"), 1).single()

        val underWay = calculator.nights(bucharest, Instant.parse("2026-10-23T01:00:00Z"), 1).single()
        val beforeSunrise = calculator.nights(bucharest, Instant.parse("2026-10-23T04:00:00Z"), 1).single()

        assertNear(evening.sunset!!, underWay.sunset!!, Duration.ofSeconds(1))
        assertNear(evening.sunset!!.plus(Duration.ofDays(1)), beforeSunrise.sunset!!, Duration.ofMinutes(5))
    }

    @Test
    fun `summer nights are only nautically dark in Berlin and not dark at all in Tromso`() {
        val midsummer = Instant.parse("2026-06-21T12:00:00Z")

        val berlin = calculator.nights(GeoCoordinate(52.52, 13.40), midsummer, 1).single()
        val tromso = calculator.nights(GeoCoordinate(69.65, 18.96), midsummer, 1).single()

        assertEquals(NightDarkness.NAUTICAL, berlin.darkness)
        assertEquals(NightDarkness.NONE, tromso.darkness)
        assertNull(tromso.sunset)
        assertNull(tromso.darkStart)
        assertTrue(tromso.moonSamples.isEmpty())
    }

    @Test
    fun `the polar night in Tromso is dark around midday's twilight`() {
        val night = calculator.nights(GeoCoordinate(69.65, 18.96), Instant.parse("2026-12-21T12:00:00Z"), 1).single()

        assertNull(night.sunset)
        assertEquals(NightDarkness.ASTRONOMICAL, night.darkness)
    }

    @Test
    fun `a clear night at new Moon scores excellent with the Moon down`() {
        val newMoon = moonPhase(NEW_MOON)
        val night = calculator.nights(bucharest, newMoon.minus(Duration.ofHours(6)), 1).single()

        val scored = StargazingScorer.score(night, clearForecast(night.darkStart!!), now = night.sunset!!)

        assertTrue(night.moonIlluminatedFraction < 0.05)
        assertEquals(100, scored.score)
        assertEquals(StargazingRating.EXCELLENT, scored.rating)
        assertEquals(MoonDuringWindow.DOWN, scored.bestWindow?.moon)
    }

    @Test
    fun `a clear night at full Moon scores only fair with the Moon up`() {
        val fullMoon = moonPhase(FULL_MOON)
        val night = calculator.nights(bucharest, fullMoon.minus(Duration.ofHours(6)), 1).single()

        val scored = StargazingScorer.score(night, clearForecast(night.darkStart!!), now = night.sunset!!)

        assertTrue(night.moonIlluminatedFraction > 0.95)
        assertEquals(StargazingRating.FAIR, scored.rating)
        assertTrue(scored.score!! in 50..55)
        assertEquals(MoonDuringWindow.UP, scored.bestWindow?.moon)
    }

    @Test
    fun `an overcast night at new Moon scores poorly`() {
        val newMoon = moonPhase(NEW_MOON)
        val night = calculator.nights(bucharest, newMoon.minus(Duration.ofHours(6)), 1).single()

        val scored = StargazingScorer.score(night, clearForecast(night.darkStart!!, cover = 100.0), now = night.sunset!!)

        assertEquals(0, scored.score)
        assertEquals(StargazingRating.POOR, scored.rating)
        assertNull(scored.bestWindow)
    }

    /** The first new or full Moon after 22 October 2026, so the test date is fixed. */
    private fun moonPhase(longitude: Double): Instant {
        val start = Time.fromMillisecondsSince1970(Instant.parse("2026-10-22T00:00:00Z").toEpochMilli())
        return Instant.ofEpochMilli(searchMoonPhase(longitude, start, 32.0)!!.toMillisecondsSince1970())
    }

    private fun clearForecast(from: Instant, cover: Double = 0.0) = CloudForecast(
        coordinate = bucharest,
        steps = (-2L..20L).map { hour ->
            CloudForecastStep(time = from.plus(Duration.ofHours(hour)), cloudCover = cover, lowCloudCover = cover)
        },
        fetchedAt = from,
    )

    private fun assertNear(expected: Instant, actual: Instant, tolerance: Duration) {
        val off = Duration.between(expected, actual).abs()
        assertTrue("$actual is $off from $expected", off <= tolerance)
    }

    private companion object {
        const val NEW_MOON = 0.0
        const val FULL_MOON = 180.0
    }
}
