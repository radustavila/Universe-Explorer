package com.radustavila.nasaexplorer.ui

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import com.radustavila.nasaexplorer.core.screenshottesting.ScreenshotDevices
import com.radustavila.nasaexplorer.core.screenshottesting.captureScreen
import com.radustavila.nasaexplorer.navigation.TopLevelDestination
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Golden images of the navigation suite: a bottom bar on a phone, a rail on a tablet.
 * `./gradlew recordRoborazziDebug` rewrites them.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], application = Application::class)
class NasaNavigationSuiteScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    @Config(qualifiers = ScreenshotDevices.COMPACT)
    fun navigationBar() = captureNavigationSuite("NavigationSuiteBar")

    @Test
    @Config(qualifiers = ScreenshotDevices.EXPANDED)
    fun navigationRail() = captureNavigationSuite("NavigationSuiteRail")

    private fun captureNavigationSuite(name: String) {
        composeTestRule.captureScreen(name) {
            NasaNavigationSuite(
                currentTopLevelDestination = TopLevelDestination.entries.first(),
                onTopLevelDestinationClick = {},
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Content", style = MaterialTheme.typography.titleLarge)
                }
            }
        }
    }
}
