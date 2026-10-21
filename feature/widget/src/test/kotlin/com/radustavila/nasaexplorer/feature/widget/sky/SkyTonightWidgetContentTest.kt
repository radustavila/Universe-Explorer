package com.radustavila.nasaexplorer.feature.widget.sky

import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.unit.hasContentDescriptionEqualTo
import androidx.glance.testing.unit.hasText
import com.radustavila.nasaexplorer.feature.widget.WidgetSizes
import com.radustavila.nasaexplorer.feature.widget.testContext
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SkyTonightWidgetContentTest {

    private val state = SkyTonightWidgetState(
        phaseAngleDegrees = 99.0,
        southernHemisphere = false,
        phase = "First quarter",
        illumination = "58% lit",
        nextFullMoon = "Full moon Mon 26 Oct",
        placeName = "Bucharest",
        iss = "ISS Tue 20 Oct 02:07 · up to 62°",
        hasIssPass = true,
        contentDescription = "First quarter. 58% lit",
    )
    private val moon = drawMoon(state.phaseAngleDegrees, sizePx = 40)

    @Test
    fun `the small widget shows the phase and the pass`() = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(WidgetSizes.Small)
        setContext(testContext)
        provideComposable { SkyTonightWidgetContent(state, title = "Tonight", moon = moon, openIntent = null) }

        onNode(hasText("Tonight")).assertExists()
        onNode(hasText("First quarter")).assertExists()
        onNode(hasText("58% lit")).assertExists()
        onNode(hasText("ISS Tue 20 Oct 02:07 · up to 62°")).assertExists()
        onNode(hasText("Full moon Mon 26 Oct")).assertDoesNotExist()
        onNode(hasContentDescriptionEqualTo("First quarter. 58% lit")).assertExists()
    }

    @Test
    fun `the large widget names the place and the next full moon`() = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(WidgetSizes.Large)
        setContext(testContext)
        provideComposable { SkyTonightWidgetContent(state, title = "Tonight", moon = moon, openIntent = null) }

        onNode(hasText("Tonight · Bucharest")).assertExists()
        onNode(hasText("Full moon Mon 26 Oct")).assertExists()
    }
}
