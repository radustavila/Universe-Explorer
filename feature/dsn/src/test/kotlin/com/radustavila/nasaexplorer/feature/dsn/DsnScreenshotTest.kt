package com.radustavila.nasaexplorer.feature.dsn

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.radustavila.nasaexplorer.core.model.DataFreshness
import com.radustavila.nasaexplorer.core.model.DsnRightNow
import com.radustavila.nasaexplorer.core.model.DsnSiteLink
import com.radustavila.nasaexplorer.core.screenshottesting.ScreenshotDevices
import com.radustavila.nasaexplorer.core.screenshottesting.captureComponent
import com.radustavila.nasaexplorer.core.screenshottesting.captureScreen
import com.radustavila.nasaexplorer.core.testing.data.TestDeepSpaceNetwork
import com.radustavila.nasaexplorer.feature.dsn.today.DsnRightNowCardContent
import com.radustavila.nasaexplorer.feature.dsn.today.DsnRightNowUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Golden images of the Deep Space Network screen over fixed data; `./gradlew recordRoborazziDebug` rewrites them. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DsnScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val liveState = DsnUiState(
        isLoaded = true,
        snapshot = TestDeepSpaceNetwork.snapshot(),
        farthest = TestDeepSpaceNetwork.farthest,
        freshness = DataFreshness(lastUpdatedAt = TestDeepSpaceNetwork.OBSERVED_AT),
    )

    @Test
    @Config(qualifiers = ScreenshotDevices.COMPACT)
    fun dsnCompact() {
        composeTestRule.captureScreen("DsnCompact") { DsnContent(liveState, {}, { _, _ -> }, {}) }
    }

    @Test
    @Config(qualifiers = ScreenshotDevices.EXPANDED)
    fun dsnExpanded() {
        composeTestRule.captureScreen("DsnExpanded") { DsnContent(liveState, {}, { _, _ -> }, {}) }
    }

    @Test
    @Config(qualifiers = ScreenshotDevices.COMPACT)
    fun spacecraftDetails() {
        val canberra = TestDeepSpaceNetwork.canberra
        composeTestRule.captureComponent("DsnSpacecraftDetails", width = 380.dp) {
            SpacecraftDetails(DsnSiteLink(canberra, canberra.dishes[1], canberra.dishes[1].links[0]))
        }
    }

    @Test
    @Config(qualifiers = ScreenshotDevices.COMPACT)
    fun rightNowCard() {
        composeTestRule.captureComponent("DsnRightNowCard", width = 380.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                DsnRightNowCardContent(
                    uiState = DsnRightNowUiState(
                        isLoaded = true,
                        hasSnapshot = true,
                        rightNow = DsnRightNow(
                            spacecraft = TestDeepSpaceNetwork.link().spacecraft,
                            siteName = "Canberra",
                            distanceKm = 21_600_000_000.0,
                            observedAt = TestDeepSpaceNetwork.OBSERVED_AT,
                        ),
                    ),
                    onClick = {},
                )
                DsnRightNowCardContent(uiState = DsnRightNowUiState(isLoaded = true), onClick = {})
            }
        }
    }
}
