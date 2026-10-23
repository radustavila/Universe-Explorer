package com.radustavila.nasaexplorer.feature.milkyway

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.radustavila.nasaexplorer.core.designsystem.theme.NasaTheme
import com.radustavila.nasaexplorer.core.designsystem.units.LocalUnitPreferences
import com.radustavila.nasaexplorer.core.model.TimeFormat
import com.radustavila.nasaexplorer.core.model.UnitPreferences
import com.radustavila.nasaexplorer.feature.milkyway.sky.MILKY_WAY_TONIGHT_CARD_TEST_TAG
import com.radustavila.nasaexplorer.feature.milkyway.sky.MilkyWayTonightCardContent
import java.time.ZoneOffset
import java.util.TimeZone
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MilkyWayScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val defaultZone = TimeZone.getDefault()

    @Before
    fun useUtc() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    @After
    fun restoreZone() {
        TimeZone.setDefault(defaultZone)
    }

    @Composable
    private fun Themed(content: @Composable () -> Unit) {
        NasaTheme {
            CompositionLocalProvider(LocalUnitPreferences provides TWENTY_FOUR_HOURS, content = content)
        }
    }

    private fun show(uiState: MilkyWayUiState, onSetLocation: () -> Unit = {}, onShowOnSkyMap: () -> Unit = {}) {
        composeTestRule.setContent {
            Themed {
                MilkyWayContent(
                    uiState = uiState,
                    onBackClick = {},
                    onSetLocation = onSetLocation,
                    onShowOnSkyMap = onShowOnSkyMap,
                    zone = ZoneOffset.UTC,
                )
            }
        }
    }

    @Test
    fun `tonight's window leads, with where the core is, and opens the sky map`() {
        var skyMapOpened = 0
        show(MilkyWayFixture.planner, onShowOnSkyMap = { skyMapOpened++ })

        composeTestRule.onNodeWithText("Tonight").assertIsDisplayed()
        composeTestRule.onNodeWithText("21:47–01:16").assertIsDisplayed()
        composeTestRule.onNodeWithText("Core 20° high in the south at 23:46").assertIsDisplayed()
        composeTestRule.onNodeWithText("Core season here: March to October", substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithTag(MILKY_WAY_SKY_MAP_TEST_TAG).performClick()
        assertEquals(1, skyMapOpened)
    }

    @Test
    fun `without a window tonight the next good night leads`() {
        show(MilkyWayFixture.notTonight)

        composeTestRule.onNodeWithText("Next good night: ", substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("21:46–01:11").assertIsDisplayed()
    }

    @Test
    fun `a night opens to its twilight and golden hour times`() {
        show(MilkyWayFixture.planner)

        composeTestRule.onNodeWithTag(MILKY_WAY_LIST_TEST_TAG).performScrollToNode(hasTestTag(MILKY_WAY_NIGHT_TEST_TAG))
        composeTestRule.onAllNodesWithTag(MILKY_WAY_NIGHT_TEST_TAG).onFirst().performClick()
        composeTestRule.onNodeWithTag(MILKY_WAY_LIST_TEST_TAG).performScrollToNode(hasText("Astronomical dusk"))
        composeTestRule.onNodeWithText("Astronomical dusk").assertIsDisplayed()
        composeTestRule.onNodeWithText("21:47").assertExists()
        composeTestRule.onNodeWithText("Moon 17% lit, below the horizon all night").assertExists()
    }

    @Test
    fun `polar summer explains that the core never rises and offers no sky map`() {
        show(MilkyWayFixture.polarSummer)

        composeTestRule.onNodeWithText("The core stays too low here").assertIsDisplayed()
        composeTestRule.onNodeWithText(
            "From your latitude the centre of the Milky Way never rises.",
        ).assertIsDisplayed()
        composeTestRule.onAllNodesWithTag(MILKY_WAY_SKY_MAP_TEST_TAG).assertCountEquals(0)
    }

    @Test
    fun `a summer that never gets dark says so`() {
        show(MilkyWayFixture.tooLight)

        composeTestRule.onNodeWithText("Too light for the Milky Way").assertIsDisplayed()
    }

    @Test
    fun `without a place the planner asks for one`() {
        var asked = 0
        show(MilkyWayFixture.noPlace, onSetLocation = { asked++ })

        composeTestRule.onNodeWithTag(MILKY_WAY_PLACE_PROMPT_TEST_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithText("Set your place").performClick()
        assertEquals(1, asked)
    }

    @Test
    fun `Sky tonight's card shows tonight's window, the next night's, or nothing`() {
        var state by mutableStateOf(MilkyWayFixture.planner)
        composeTestRule.setContent { Themed { MilkyWayTonightCardContent(state, onOpen = {}) } }

        composeTestRule.onNodeWithText("21:47–01:16, core 20° high in the south").assertIsDisplayed()

        state = MilkyWayFixture.notTonight
        composeTestRule.onNodeWithText("Not tonight. Next: ", substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("21:46–01:11", substring = true).assertIsDisplayed()

        state = MilkyWayFixture.polarSummer
        composeTestRule.onAllNodes(hasTestTag(MILKY_WAY_TONIGHT_CARD_TEST_TAG)).assertCountEquals(0)
    }

    private companion object {
        val TWENTY_FOUR_HOURS = UnitPreferences(timeFormat = TimeFormat.TWENTY_FOUR_HOUR)
    }
}
