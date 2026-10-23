package com.radustavila.nasaexplorer.feature.moonmap

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.radustavila.nasaexplorer.core.model.MoonTilesPack
import com.radustavila.nasaexplorer.core.screenshottesting.ScreenshotDevices
import com.radustavila.nasaexplorer.core.screenshottesting.captureComponent
import com.radustavila.nasaexplorer.core.screenshottesting.captureScreen
import com.radustavila.nasaexplorer.core.testing.data.TestMoonMap
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Golden images of the Moon map's controls over a stand-in map; `./gradlew recordRoborazziDebug` rewrites them. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MoonMapScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    @Config(qualifiers = ScreenshotDevices.COMPACT)
    fun moonMapCompact() {
        composeTestRule.captureScreen("MoonMapCompact") { Screen(MoonMapFixture.state()) }
    }

    @Test
    @Config(qualifiers = ScreenshotDevices.EXPANDED)
    fun moonMapExpandedDownloading() {
        composeTestRule.captureScreen("MoonMapExpandedDownloading") {
            Screen(MoonMapFixture.state(tiles = MoonTilesPack.Downloading(20_000_000L, 59_768_832L)))
        }
    }

    @Test
    @Config(qualifiers = ScreenshotDevices.COMPACT)
    fun moonMapSiteDetails() {
        composeTestRule.captureComponent("MoonMapSiteDetails", width = 380.dp) {
            SiteDetails(
                site = TestMoonMap.apollo11,
                illumination = MoonMapFixture.state().illumination,
                onOpenPage = {},
            )
        }
    }

    @androidx.compose.runtime.Composable
    private fun Screen(state: MoonMapUiState) {
        MoonMapScreen(
            uiState = state,
            onBackClick = {},
            onProgrammeToggled = {},
            onSiteSelected = {},
            onSiteDismissed = {},
            onDownloadTiles = {},
            onRemoveTiles = {},
            onOpenPage = {},
            map = MoonMapFixture.map,
        )
    }
}
