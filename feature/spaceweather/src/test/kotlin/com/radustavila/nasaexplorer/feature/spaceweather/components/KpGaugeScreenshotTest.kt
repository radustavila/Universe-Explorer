package com.radustavila.nasaexplorer.feature.spaceweather.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.radustavila.nasaexplorer.core.screenshottesting.ScreenshotDevices
import com.radustavila.nasaexplorer.core.screenshottesting.captureComponent
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Golden image of the Kp gauge; `./gradlew recordRoborazziDebug` rewrites it. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = ScreenshotDevices.COMPACT)
class KpGaugeScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun kpGauge() {
        // Quiet, a G2 storm, and no reading yet.
        // About as wide as on a phone's Space weather screen.
        composeTestRule.captureComponent("KpGauge", width = 280.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                KpGauge(kp = 2.3)
                KpGauge(kp = 6.3)
                KpGauge(kp = null)
            }
        }
    }
}
