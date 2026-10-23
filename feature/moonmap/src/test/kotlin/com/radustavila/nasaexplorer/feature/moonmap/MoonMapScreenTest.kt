package com.radustavila.nasaexplorer.feature.moonmap

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.radustavila.nasaexplorer.core.designsystem.theme.NasaTheme
import com.radustavila.nasaexplorer.core.model.LunarProgramme
import com.radustavila.nasaexplorer.core.model.MoonTilesFailure
import com.radustavila.nasaexplorer.core.model.MoonTilesPack
import com.radustavila.nasaexplorer.core.testing.data.TestMoonMap
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val UNAVAILABLE = "Detailed close-ups come with the Play Store version of the app."
private const val POLE_NOTE = "The map stops at 85° N and S; the poles do not fit its projection."

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w400dp-h800dp")
class MoonMapScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private var toggled = mutableListOf<LunarProgramme>()
    private var downloads = 0
    private var removals = 0
    private var opened = mutableListOf<String>()

    private fun setContent(state: MoonMapUiState) {
        composeTestRule.setContent {
            NasaTheme {
                MoonMapScreen(
                    uiState = state,
                    onBackClick = {},
                    onProgrammeToggled = { toggled += it },
                    onSiteSelected = {},
                    onSiteDismissed = {},
                    onDownloadTiles = { downloads++ },
                    onRemoveTiles = { removals++ },
                    onOpenPage = { opened += it },
                    map = MoonMapFixture.map,
                )
            }
        }
    }

    @Test
    fun `chips show each programme selected and toggle it`() {
        setContent(MoonMapFixture.state())

        composeTestRule.onNodeWithTag(FAKE_MAP_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithText("Apollo").assertIsSelected().performClick()
        composeTestRule.onNodeWithText("Luna").assertIsSelected()
        assertEquals(listOf(LunarProgramme.APOLLO), toggled)
    }

    @Test
    fun `a hidden programme's chip is not selected`() {
        setContent(MoonMapFixture.state().copy(shownProgrammes = setOf(LunarProgramme.LUNA)))

        composeTestRule.onNodeWithText("Apollo").assertIsNotSelected()
    }

    @Test
    fun `the tile offer names the size and downloads on tap`() {
        setContent(MoonMapFixture.state())

        composeTestRule.onNodeWithText("Download (60 MB)").performClick()
        assertEquals(1, downloads)
        composeTestRule.onNodeWithText(POLE_NOTE).assertIsDisplayed()
    }

    @Test
    fun `progress, installed and failed packs read clearly`() {
        var tiles: MoonTilesPack by mutableStateOf(MoonTilesPack.Downloading(30_000_000L, 60_000_000L))
        composeTestRule.setContent {
            NasaTheme {
                MoonMapScreen(
                    uiState = MoonMapFixture.state(tiles = tiles),
                    onBackClick = {},
                    onProgrammeToggled = {},
                    onSiteSelected = {},
                    onSiteDismissed = {},
                    onDownloadTiles = { downloads++ },
                    onRemoveTiles = { removals++ },
                    onOpenPage = {},
                    map = MoonMapFixture.map,
                )
            }
        }

        composeTestRule.onNodeWithText("Downloading detailed tiles… 50%").assertIsDisplayed()
        tiles = MoonTilesPack.Installed("/data/moon_tiles", 59_768_832L)
        composeTestRule.onNodeWithText("Detailed tiles on this device (60 MB)").assertIsDisplayed()
        composeTestRule.onNodeWithText("Remove").performClick()
        assertEquals(1, removals)
        tiles = MoonTilesPack.Failed(MoonTilesFailure.NO_CONNECTION)
        composeTestRule.onNodeWithText("Try again").performClick()
        assertEquals(1, downloads)
        tiles = MoonTilesPack.Unavailable
        composeTestRule.onNodeWithText(UNAVAILABLE).assertIsDisplayed()
    }

    @Test
    fun `a selected site opens its sheet with crew, daylight and its record`() {
        setContent(MoonMapFixture.state(selected = TestMoonMap.apollo11))

        composeTestRule.onNodeWithTag(MOON_MAP_SITE_SHEET_TEST_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithText("Apollo · Crewed landing").assertIsDisplayed()
        composeTestRule.onNodeWithText("Crew on the surface: Neil Armstrong, Buzz Aldrin").assertIsDisplayed()
        composeTestRule.onNodeWithText("In daylight now").assertIsDisplayed()
        composeTestRule.onNodeWithText("NSSDCA record").performClick()
        assertEquals(listOf("https://nssdc.gsfc.nasa.gov/nmc/spacecraft/display.action?id=1969-059C"), opened)
    }

    @Test
    fun `a far side site at first quarter is in lunar night`() {
        setContent(MoonMapFixture.state(selected = TestMoonMap.luna9))

        composeTestRule.onNodeWithText("In lunar night now").assertIsDisplayed()
        composeTestRule.onNodeWithText("Site: Oceanus Procellarum").assertIsDisplayed()
    }
}
