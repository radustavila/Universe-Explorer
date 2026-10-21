package com.radustavila.nasaexplorer.feature.widget.sky

import com.radustavila.nasaexplorer.core.domain.sky.IssPassForecast
import com.radustavila.nasaexplorer.core.model.GeoCoordinate
import com.radustavila.nasaexplorer.core.model.SatellitePass
import com.radustavila.nasaexplorer.core.testing.data.TestSky
import com.radustavila.nasaexplorer.feature.widget.testResources
import com.radustavila.nasaexplorer.feature.widget.testWidgetFormat
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SkyTonightWidgetStateTest {

    private val now = Instant.parse("2026-10-19T20:00:00Z")
    private val elements = TestSky.issElements()

    private fun forecast(vararg passes: SatellitePass) =
        IssPassForecast(elements = elements, passes = passes.toList(), isStale = false)

    @Test
    fun `shows the Moon and the next visible pass over the saved place`() {
        val hidden = TestSky.pass(start = Instant.parse("2026-10-19T22:10:00Z"), isVisible = false)
        val visible = TestSky.pass()

        val state = skyTonightWidgetState(
            TestSky.moon(),
            TestSky.location(),
            forecast(hidden, visible),
            now,
            testWidgetFormat(),
            testResources,
        )

        assertEquals("First quarter", state.phase)
        assertEquals("58% lit", state.illumination)
        assertEquals("Full moon Mon 26 Oct", state.nextFullMoon)
        assertEquals("Bucharest", state.placeName)
        assertEquals("ISS Tue 02:07 · up to 62°", state.iss)
        assertTrue(state.hasIssPass)
        assertFalse(state.southernHemisphere)
        assertEquals(
            "First quarter. 58% lit. Full moon Mon 26 Oct. ISS Tue 02:07 · up to 62°",
            state.contentDescription,
        )
    }

    @Test
    fun `without a place the Moon still shows and the pass asks for one`() {
        val state = skyTonightWidgetState(TestSky.moon(), null, null, now, testWidgetFormat(), testResources)

        assertEquals("First quarter", state.phase)
        assertNull(state.placeName)
        assertEquals("Set your place in Sky tonight to see ISS passes", state.iss)
        assertFalse(state.hasIssPass)
    }

    @Test
    fun `explains why there is no pass to show`() {
        fun issFor(forecast: IssPassForecast) =
            skyTonightWidgetState(TestSky.moon(), TestSky.location(), forecast, now, testWidgetFormat(), testResources).iss

        assertEquals(
            "Open Sky tonight online to predict ISS passes",
            issFor(IssPassForecast(elements = null, passes = emptyList(), isStale = false)),
        )
        assertEquals(
            "ISS orbit data is out of date. Open Sky tonight online",
            issFor(IssPassForecast(elements = elements, passes = emptyList(), isStale = true)),
        )
        assertEquals("No visible ISS pass in the next 5 days", issFor(forecast(TestSky.pass(isVisible = false))))
    }

    @Test
    fun `a southern place draws the Moon the other way up`() {
        val sydney = TestSky.location(coordinate = GeoCoordinate(-33.87, 151.21), name = "Sydney")

        val state = skyTonightWidgetState(TestSky.moon(), sydney, forecast(), now, testWidgetFormat(), testResources)

        assertTrue(state.southernHemisphere)
    }
}
