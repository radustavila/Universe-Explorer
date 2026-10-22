package com.radustavila.nasaexplorer.feature.settings

import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import com.radustavila.nasaexplorer.core.screenshottesting.ScreenshotDevices
import com.radustavila.nasaexplorer.core.screenshottesting.captureScreen
import com.radustavila.nasaexplorer.core.testing.rule.MainDispatcherRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Golden images of Settings over fakes; `./gradlew recordRoborazziDebug` rewrites them. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SettingsScreenshotTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    @Config(qualifiers = ScreenshotDevices.COMPACT)
    fun settingsCompact() = captureSettings("SettingsCompact")

    @Test
    @Config(qualifiers = ScreenshotDevices.EXPANDED)
    fun settingsExpanded() = captureSettings("SettingsExpanded")

    private fun captureSettings(name: String) {
        val viewModel = SettingsFixture().createViewModel()
        composeTestRule.captureScreen(name) {
            SettingsScreen(
                onBackClick = {},
                locationSection = { modifier -> Text("Location slot", modifier) },
                viewModel = viewModel,
            )
        }
    }
}
