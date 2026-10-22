package com.radustavila.nasaexplorer.feature.spaceweather.components

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.radustavila.nasaexplorer.core.model.SunChannel
import com.radustavila.nasaexplorer.core.screenshottesting.ScreenshotDevices
import com.radustavila.nasaexplorer.core.screenshottesting.captureComponent
import com.radustavila.nasaexplorer.core.testing.fake.FakeSunImageRepository
import com.radustavila.nasaexplorer.feature.spaceweather.SunUiState
import java.time.Instant
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Golden image of the "Sun now" card offline; `./gradlew recordRoborazziDebug` rewrites it. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = ScreenshotDevices.COMPACT)
class SunCardScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun sunCard() {
        // Offline, so the label shows the picture's clock time rather than a relative age.
        val picture = FakeSunImageRepository.image(SunChannel.CHROMOSPHERE, Instant.parse("2026-10-22T16:04:06Z"))
        composeTestRule.captureComponent("SunCard", width = 380.dp) {
            SunCard(
                sun = SunUiState(channel = SunChannel.CHROMOSPHERE, image = picture),
                isOffline = true,
                onChannelSelected = {},
                onOpen = {},
            )
        }
    }
}
