package com.radustavila.nasaexplorer.feature.widget.aurora

import com.radustavila.nasaexplorer.core.model.AuroraForecast
import com.radustavila.nasaexplorer.core.model.AuroraVisibility
import com.radustavila.nasaexplorer.core.model.CurrentKp
import com.radustavila.nasaexplorer.core.model.GeoCoordinate
import com.radustavila.nasaexplorer.core.testing.data.TestSpaceWeather
import com.radustavila.nasaexplorer.core.testing.data.TestSpaceWeather.NOW
import com.radustavila.nasaexplorer.feature.widget.R
import com.radustavila.nasaexplorer.feature.widget.testResources
import com.radustavila.nasaexplorer.feature.widget.testWidgetFormat
import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AuroraWidgetStateTest {

    // Slots from 06:00: two observed, the current one (12:00) estimated, then 15:00, 18:00, 21:00.
    private val forecast = TestSpaceWeather.forecast(2.0, 3.0, 4.0, 5.33, 6.0, 3.67)

    private fun state(
        current: CurrentKp? = TestSpaceWeather.currentKp(kp = 4.33),
        place: GeoCoordinate? = null,
        auroraMap: AuroraForecast? = null,
        now: Instant = NOW,
    ) = auroraWidgetState(current, forecast, auroraMap, place, now, testWidgetFormat(), testResources)

    @Test
    fun `shows the current Kp, its meaning, the coming peak and when the reading is from`() {
        val state = checkNotNull(state())

        assertEquals("4.3", state.kp)
        assertEquals(4, state.level)
        assertEquals("Active", state.activity)
        assertEquals("Peak Kp 6 · 18:00", state.peak)
        assertEquals("Kp at 14:20", state.updated)
        assertFalse(state.isStale)
        assertNull(state.local)
        assertEquals("Kp index 4.3, Active. Peak Kp 6 · 18:00. Kp at 14:20", state.contentDescription)
    }

    @Test
    fun `without the 1-minute estimate the latest slot that has begun stands in`() {
        val state = checkNotNull(state(current = null))

        assertEquals("4.0", state.kp)
        assertEquals("Kp at 12:00", state.updated)
    }

    @Test
    fun `an old reading says how old it is`() {
        val state = checkNotNull(state(now = NOW.plus(Duration.ofHours(5))))

        assertTrue(state.isStale)
        assertEquals("Kp at 14:20 · 5 h old", state.updated)
        // At 19:20 the 18:00 slot is under way and still the highest ahead.
        assertEquals("Peak Kp 6 · now", state.peak)
    }

    @Test
    fun `a current aurora map gives the chance at the saved place`() {
        val fairbanks = GeoCoordinate(latitude = 64.84, longitude = -147.72)

        val state = checkNotNull(state(place = fairbanks, auroraMap = TestSpaceWeather.auroraForecast(probability = 40)))

        assertEquals("40% aurora chance here", state.local)
    }

    @Test
    fun `without a current map the Kp table says whether the peak reaches the place`() {
        val oldMap = TestSpaceWeather.auroraForecast(observedAt = NOW.minus(Duration.ofHours(4)))
        val helsinki = GeoCoordinate(latitude = 60.17, longitude = 24.94)
        val minimumKp = checkNotNull(AuroraVisibility.minimumKp(helsinki.latitude, helsinki.longitude))

        val state = checkNotNull(state(place = helsinki, auroraMap = oldMap))

        val expected = if (minimumKp <= 6) R.string.aurora_widget_reaches_you else R.string.aurora_widget_needs_kp
        assertEquals(testResources.getString(expected, minimumKp), state.local)
    }

    @Test
    fun `a place the oval never reaches is told so`() {
        val state = checkNotNull(state(place = GeoCoordinate(latitude = 1.35, longitude = 103.82)))

        assertEquals("The aurora rarely reaches your place", state.local)
    }

    @Test
    fun `nothing cached means no state`() {
        assertNull(auroraWidgetState(null, emptyList(), null, null, NOW, testWidgetFormat(), testResources))
    }

    @Test
    fun `the colour scale runs from green to red`() {
        assertEquals(kpColor(0), kpColor(2))
        assertTrue(kpColor(9) != kpColor(5))
    }
}
