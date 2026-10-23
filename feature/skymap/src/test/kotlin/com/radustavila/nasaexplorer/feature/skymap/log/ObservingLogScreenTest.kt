package com.radustavila.nasaexplorer.feature.skymap.log

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.radustavila.nasaexplorer.core.designsystem.theme.NasaTheme
import com.radustavila.nasaexplorer.core.model.DeepSkyCatalogue
import com.radustavila.nasaexplorer.core.model.DeepSkyTonight
import com.radustavila.nasaexplorer.core.model.DeepSkyVisibility
import com.radustavila.nasaexplorer.core.testing.data.TestDeepSky
import com.radustavila.nasaexplorer.feature.skymap.deepsky.MARK_SEEN_DIALOG_TEST_TAG
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ObservingLogScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val catalogues = mutableListOf<DeepSkyCatalogue>()
    private val notSeen = mutableListOf<Boolean>()
    private val upTonight = mutableListOf<Boolean>()
    private val shownOnMap = mutableListOf<String>()
    private val marked = mutableListOf<String>()

    private val tonight = DeepSkyTonight(
        darkStart = Instant.parse("2026-10-22T17:30:00Z"),
        darkEnd = Instant.parse("2026-10-23T03:30:00Z"),
        visible = listOf(TestDeepSky.M31, TestDeepSky.M13).associate {
            it.id to DeepSkyVisibility(it.id, Instant.parse("2026-10-22T21:00:00Z"), 64.0, 175.0)
        },
    )

    private val state = ObservingLogUiState(
        objects = TestDeepSky.objects(),
        constellationNames = mapOf("And" to "Andromeda", "Ori" to "Orion", "Her" to "Hercules"),
        log = mapOf(TestDeepSky.M42.id to TestDeepSky.entry(objectId = TestDeepSky.M42.id)),
        tonight = TonightSky.Known(tonight),
    )

    private fun show(uiState: ObservingLogUiState) {
        composeTestRule.setContent {
            NasaTheme {
                ObservingLogContent(
                    uiState = uiState,
                    onBackClick = {},
                    onShowOnSkyMap = { shownOnMap += it },
                    onCatalogueChange = { catalogues += it },
                    onNotSeenOnlyChange = { notSeen += it },
                    onUpTonightOnlyChange = { upTonight += it },
                    onMarkSeen = { id, _, _, _ -> marked += id },
                    onRemoveSeen = {},
                    today = LocalDate.of(2026, 10, 22),
                )
            }
        }
    }

    @Test
    fun `the Messier checklist shows progress, numbers in order and what is seen`() {
        show(state)

        composeTestRule.onNodeWithText("Seen: 1 / 3").assertIsDisplayed()
        composeTestRule.onNodeWithText("M13").assertIsDisplayed()
        composeTestRule.onNodeWithText("Galaxy in Andromeda").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Seen").assertExists()

        composeTestRule.onNodeWithText("Caldwell").performClick()
        composeTestRule.onNodeWithText("Not yet seen").performClick()
        composeTestRule.onNodeWithText("Up tonight").performClick()
        assertEquals(listOf(DeepSkyCatalogue.CALDWELL), catalogues)
        assertEquals(listOf(true), notSeen)
        assertEquals(listOf(true), upTonight)
    }

    @Test
    fun `filters keep what is not seen and up tonight`() {
        show(state.copy(notSeenOnly = true, upTonightOnly = true))

        val rows = composeTestRule.onAllNodesWithTag(OBSERVING_LOG_ROW_TEST_TAG).fetchSemanticsNodes()
        assertEquals(2, rows.size)
        composeTestRule.onNodeWithText("M31").assertIsDisplayed()
        composeTestRule.onNodeWithText("M13").assertIsDisplayed()
    }

    @Test
    fun `a row opens to its details, the map and the log dialog`() {
        show(state)

        composeTestRule.onNodeWithText("Andromeda Galaxy").performClick()
        composeTestRule.onNodeWithText("Best in October · Naked eye").assertIsDisplayed()
        composeTestRule.onNodeWithText("Show on the sky map").performScrollTo().performClick()
        composeTestRule.onNodeWithText("Mark as seen").performScrollTo().performClick()
        composeTestRule.onNodeWithTag(MARK_SEEN_DIALOG_TEST_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithText("Save").performClick()

        assertEquals(listOf(TestDeepSky.M31.id), shownOnMap)
        assertEquals(listOf(TestDeepSky.M31.id), marked)
    }

    @Test
    fun `without a place the up-tonight filter says why it is empty`() {
        show(state.copy(tonight = TonightSky.NoPlace, upTonightOnly = true))

        composeTestRule.onNodeWithText("Choose your place in Sky tonight to see what is up tonight.")
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("Nothing in this list matches the filters.").assertIsDisplayed()
    }

    @Test
    fun `the tonight card lists unseen Messier objects up tonight, brightest first`() {
        val opened = mutableListOf<String>()
        composeTestRule.setContent {
            NasaTheme { DeepSkyTonightCardContent(state, onOpenLog = {}, onObjectClick = { opened += it }) }
        }

        composeTestRule.onNodeWithText("Messier objects seen: 1 / 110").assertIsDisplayed()
        composeTestRule.onNodeWithText("M13 · Hercules Globular Cluster").performClick()
        composeTestRule.onNodeWithText("M31 · Andromeda Galaxy").assertIsDisplayed()
        assertEquals(listOf(TestDeepSky.M13.id), opened)
        assertEquals(listOf(TestDeepSky.M31, TestDeepSky.M13), state.tonightsBest())
    }

    @Test
    fun `the tonight card hides without a dark night`() {
        composeTestRule.setContent {
            NasaTheme {
                DeepSkyTonightCardContent(state.copy(tonight = TonightSky.NotDark), onOpenLog = {}, onObjectClick = {})
            }
        }

        assertTrue(composeTestRule.onAllNodesWithTag(DEEP_SKY_TONIGHT_CARD_TEST_TAG).fetchSemanticsNodes().isEmpty())
    }
}
