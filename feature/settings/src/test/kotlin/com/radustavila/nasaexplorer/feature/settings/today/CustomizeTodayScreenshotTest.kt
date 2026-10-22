package com.radustavila.nasaexplorer.feature.settings.today

import androidx.compose.ui.test.junit4.createComposeRule
import com.radustavila.nasaexplorer.core.model.TodayCardId
import com.radustavila.nasaexplorer.core.screenshottesting.ScreenshotDevices
import com.radustavila.nasaexplorer.core.screenshottesting.captureScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Golden images of Customize Today; `./gradlew recordRoborazziDebug` rewrites them. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CustomizeTodayScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val shown = listOf(TodayCardId.ISS_NOW, TodayCardId.EARTH_TODAY, TodayCardId.NEXT_LAUNCHES)

    @Test
    @Config(qualifiers = ScreenshotDevices.COMPACT)
    fun customizeTodayCompact() = capture("CustomizeTodayCompact")

    @Test
    @Config(qualifiers = ScreenshotDevices.EXPANDED)
    fun customizeTodayExpanded() = capture("CustomizeTodayExpanded")

    private fun capture(name: String) {
        composeTestRule.captureScreen(name) {
            CustomizeTodayContent(
                uiState = CustomizeTodayUiState(
                    isLoaded = true,
                    shown = shown,
                    hidden = TodayCardId.entries - shown.toSet(),
                ),
                onBackClick = {},
                onShownChange = { _, _ -> },
                onReorder = {},
                onMove = { _, _ -> },
                onReset = {},
            )
        }
    }
}
