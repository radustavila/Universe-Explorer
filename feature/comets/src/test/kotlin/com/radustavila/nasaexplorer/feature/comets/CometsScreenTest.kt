package com.radustavila.nasaexplorer.feature.comets

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.radustavila.nasaexplorer.core.designsystem.theme.NasaTheme
import com.radustavila.nasaexplorer.core.model.CometsTonight
import com.radustavila.nasaexplorer.core.model.DataFreshness
import com.radustavila.nasaexplorer.core.model.GeoCoordinate
import com.radustavila.nasaexplorer.core.testing.data.TestComets
import com.radustavila.nasaexplorer.feature.comets.component.COMETS_PLACE_PROMPT_TEST_TAG
import com.radustavila.nasaexplorer.feature.comets.component.cometCardTestTag
import com.radustavila.nasaexplorer.feature.comets.component.faintCometTestTag
import com.radustavila.nasaexplorer.feature.comets.sky.COMETS_TONIGHT_CARD_TEST_TAG
import com.radustavila.nasaexplorer.feature.comets.sky.CometsTonightCardContent
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CometsScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val synced = DataFreshness(lastUpdatedAt = Instant.parse("2026-01-01T00:00:00Z"))
    private val bucharest = GeoCoordinate(latitude = 44.43, longitude = 26.10)
    private val bright = TestComets.tonight(
        comet = TestComets.comet(designation = "2026 B1", name = "C/2026 B1 (Bright)"),
        magnitude = 7.6,
    )
    private val faint = TestComets.tonight()

    private fun show(uiState: CometsUiState, onCometClick: (String) -> Unit = {}, onSetLocation: () -> Unit = {}) {
        composeTestRule.setContent {
            NasaTheme {
                CometsContent(
                    uiState = uiState,
                    onBackClick = {},
                    onCometClick = onCometClick,
                    onSetLocation = onSetLocation,
                    onRefresh = {},
                )
            }
        }
    }

    @Test
    fun `a bright comet shows its rounded brightness, how to see it and where it is`() {
        val clicks = mutableListOf<String>()
        show(
            CometsUiState(
                CometsTonight(bucharest, visible = listOf(bright), faint = listOf(faint)),
                freshness = synced,
            ),
            onCometClick = { clicks += it },
        )

        composeTestRule.onNodeWithText("C/2026 B1 (Bright)").assertIsDisplayed()
        composeTestRule.onNodeWithText("Predicted magnitude 8 · binoculars").assertIsDisplayed()
        composeTestRule.onNodeWithText("In Draco · 1.70 au from Earth").assertIsDisplayed()
        composeTestRule.onNodeWithText("Best at", substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithTag(cometCardTestTag("2026 B1")).performClick()
        composeTestRule.onNodeWithTag(COMETS_LIST_TEST_TAG)
            .performScrollToNode(hasTestTag(faintCometTestTag("2026 A2")))
        composeTestRule.onNodeWithTag(faintCometTestTag("2026 A2")).performClick()

        assertEquals(listOf("2026 B1", "2026 A2"), clicks)
    }

    @Test
    fun `no comet above the cut-off is an empty state, with the fainter ones still listed`() {
        show(CometsUiState(CometsTonight(bucharest, visible = emptyList(), faint = listOf(faint)), freshness = synced))

        composeTestRule.onNodeWithTag(COMETS_EMPTY_TEST_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithText("No comet is predicted to be brighter than magnitude 12", substring = true)
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("Fainter comets").assertIsDisplayed()
    }

    @Test
    fun `without a place the directions give way to a prompt`() {
        var asked = false
        show(
            CometsUiState(
                CometsTonight(null, visible = listOf(bright.copy(sky = null)), faint = emptyList()),
                freshness = synced,
            ),
            onSetLocation = { asked = true },
        )

        composeTestRule.onNodeWithTag(COMETS_PLACE_PROMPT_TEST_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithText("Set your place").performClick()
        composeTestRule.onNodeWithText("Best at", substring = true).assertDoesNotExist()
        assertEquals(true, asked)
    }

    @Test
    fun `never downloaded and offline asks for one connection`() {
        show(
            CometsUiState(
                comets = CometsTonight(null, emptyList(), emptyList()),
                freshness = DataFreshness(isOffline = true),
            ),
        )

        composeTestRule.onNodeWithText("Comets need one download").assertIsDisplayed()
    }

    @Test
    fun `the sky tonight card lists bright comets or says there is none`() {
        val opened = mutableListOf<String>()
        var state by mutableStateOf(
            CometsUiState(comets = CometsTonight(bucharest, visible = listOf(bright), faint = listOf(faint))),
        )
        composeTestRule.setContent {
            NasaTheme {
                CometsTonightCardContent(state, onOpenComets = { opened += "all" }, onCometClick = { opened += it })
            }
        }

        composeTestRule.onNodeWithText("C/2026 B1 (Bright)").performClick()
        composeTestRule.onNodeWithTag(COMETS_TONIGHT_CARD_TEST_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("All comets").performClick()
        state = CometsUiState(comets = CometsTonight(bucharest, visible = emptyList(), faint = listOf(faint)))

        composeTestRule.onNodeWithText("No comet is bright enough to find tonight.").assertIsDisplayed()
        composeTestRule.onNodeWithText("C/2026 A2 (Bok)").assertDoesNotExist()
        assertEquals(listOf("2026 B1", "all"), opened)
    }
}
