package com.radustavila.nasaexplorer.feature.spaceweather.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.radustavila.nasaexplorer.core.screenshottesting.ScreenshotDevices
import com.radustavila.nasaexplorer.core.screenshottesting.captureComponent
import com.radustavila.nasaexplorer.core.testing.data.TestSpaceWeather
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Golden images of the Today card; `./gradlew recordRoborazziDebug` rewrites them. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = ScreenshotDevices.COMPACT)
class SpaceWeatherTodayCardScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun spaceWeatherTodayCard() {
        composeTestRule.captureComponent("SpaceWeatherTodayCard", width = 380.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                SpaceWeatherTodayCardContent(
                    uiState = SpaceWeatherTodayUiState(
                        isLoaded = true,
                        kp = 4.33,
                        peak = TestSpaceWeather.reading(TestSpaceWeather.CURRENT_SLOT.plusSeconds(10_800), 5.67),
                    ),
                    onClick = {},
                )
                SpaceWeatherTodayCardContent(uiState = SpaceWeatherTodayUiState(isLoaded = true), onClick = {})
            }
        }
    }
}
