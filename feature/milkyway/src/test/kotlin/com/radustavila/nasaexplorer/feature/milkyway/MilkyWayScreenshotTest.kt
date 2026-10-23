package com.radustavila.nasaexplorer.feature.milkyway

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.radustavila.nasaexplorer.core.screenshottesting.ScreenshotDevices
import com.radustavila.nasaexplorer.core.screenshottesting.captureComponent
import com.radustavila.nasaexplorer.core.screenshottesting.captureScreen
import com.radustavila.nasaexplorer.feature.milkyway.sky.MilkyWayTonightCardContent
import java.time.ZoneOffset
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Golden images of the Milky Way planner over fixed nights; `./gradlew recordRoborazziDebug` rewrites them. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MilkyWayScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    @Config(qualifiers = ScreenshotDevices.COMPACT)
    fun milkyWayCompact() {
        composeTestRule.captureScreen("MilkyWayCompact") { Planner(MilkyWayFixture.planner) }
    }

    @Test
    @Config(qualifiers = ScreenshotDevices.EXPANDED)
    fun milkyWayExpanded() {
        composeTestRule.captureScreen("MilkyWayExpanded") { Planner(MilkyWayFixture.planner) }
    }

    @Test
    @Config(qualifiers = ScreenshotDevices.COMPACT)
    fun milkyWayPolarSummer() {
        composeTestRule.captureScreen("MilkyWayPolarSummer") { Planner(MilkyWayFixture.polarSummer) }
    }

    @Test
    @Config(qualifiers = ScreenshotDevices.COMPACT)
    fun milkyWayTonightCard() {
        composeTestRule.captureComponent("MilkyWayTonightCard", width = 380.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                MilkyWayTonightCardContent(MilkyWayFixture.planner, onOpen = {})
                MilkyWayTonightCardContent(MilkyWayFixture.notTonight, onOpen = {})
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun Planner(state: MilkyWayUiState) {
        MilkyWayContent(state, onBackClick = {}, onSetLocation = {}, onShowOnSkyMap = {}, zone = ZoneOffset.UTC)
    }
}
