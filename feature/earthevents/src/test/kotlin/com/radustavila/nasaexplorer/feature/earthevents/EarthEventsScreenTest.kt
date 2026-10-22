package com.radustavila.nasaexplorer.feature.earthevents

import androidx.compose.foundation.layout.Box
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.radustavila.nasaexplorer.core.designsystem.component.OFFLINE_BANNER_TEST_TAG
import com.radustavila.nasaexplorer.core.map.TileLoadStatus
import com.radustavila.nasaexplorer.core.map.gibs.GibsImagery
import com.radustavila.nasaexplorer.core.map.overlay.MapOverlay
import com.radustavila.nasaexplorer.core.model.EarthEventCategory
import com.radustavila.nasaexplorer.core.testing.data.TestEarthEvents
import java.time.Duration
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w400dp-h800dp")
class EarthEventsScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val today = LocalDate.of(2026, 10, 19)
    private val storm = TestEarthEvents.storm()
    private val fire = TestEarthEvents.wildfire()
    private val oldFire = TestEarthEvents.wildfire(
        id = "EONET_old",
        at = TestEarthEvents.NOW.minus(Duration.ofDays(20)),
    )
    private val loaded = EarthEventsUiState(
        events = listOf(storm, fire),
        today = today,
        isLoaded = true,
    )

    private var shownImagery: GibsImagery? = null
    private var shownOverlays: List<MapOverlay> = emptyList()
    private var mapEventClick: (String) -> Unit = {}

    private val toggled = mutableListOf<EarthEventCategory>()
    private val selected = mutableListOf<String>()
    private val openedUrls = mutableListOf<String>()
    private var showImageryClicks = 0
    private var hideImageryClicks = 0
    private var historyToggles = 0

    private fun setContent(
        uiState: EarthEventsUiState,
        tileStatus: TileLoadStatus = TileLoadStatus.Loaded,
        isLoadingMapImagery: Boolean = false,
    ) {
        composeTestRule.setContent {
            EarthEventsScreen(
                uiState = uiState,
                tileStatus = tileStatus,
                isLoadingMapImagery = isLoadingMapImagery,
                onWildfireHistoryToggled = { historyToggles++ },
                onBackClick = {},
                onRefresh = {},
                onCategoryToggled = { toggled += it },
                onEventSelected = { selected += it },
                onEventDismissed = {},
                onShowImagery = { showImageryClicks++ },
                onHideImagery = { hideImageryClicks++ },
                onOpenSource = { openedUrls += it },
                onMessageShown = {},
                map = { imagery, overlays, onEventClick, _, modifier ->
                    shownImagery = imagery
                    shownOverlays = overlays
                    mapEventClick = onEventClick
                    Box(modifier.testTag(FAKE_MAP_TAG))
                },
            )
        }
    }

    @Test
    fun `the map shows the events over yesterday's imagery`() {
        setContent(loaded)

        composeTestRule.onNodeWithTag(FAKE_MAP_TAG).assertIsDisplayed()
        assertEquals(loaded.imagery, shownImagery)
        assertTrue(shownOverlays.any { overlay -> overlay.shapes.any { it.id == storm.id } })
    }

    @Test
    fun `the filter lists each category with its count and toggles it`() {
        setContent(loaded.copy(hiddenCategories = setOf(EarthEventCategory.WILDFIRES)))

        composeTestRule.onNodeWithText("Severe storms · 1").assertIsSelected()
        composeTestRule.onNodeWithText("Wildfires · 1").assertIsNotSelected().performClick()

        assertEquals(listOf(EarthEventCategory.WILDFIRES), toggled)
    }

    @Test
    fun `tapping a marker selects its event`() {
        setContent(loaded)

        composeTestRule.runOnIdle { mapEventClick(storm.id) }

        assertEquals(listOf(storm.id), selected)
    }

    @Test
    fun `the event sheet shows title, category, magnitude, track and source`() {
        setContent(loaded.copy(selectedEventId = storm.id))

        composeTestRule.onNodeWithTag(EARTH_EVENT_SHEET_TEST_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithText("Tropical Cyclone Wati").assertIsDisplayed()
        composeTestRule.onNodeWithText("Severe storms").assertIsDisplayed()
        // 45 kts, in the units of Robolectric's default en-US region.
        composeTestRule.onNodeWithText("Magnitude: 52 mph", substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("Last report today").assertIsDisplayed()
        composeTestRule.onNodeWithText("Last update:", substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("Track: 3 positions since", substring = true).assertIsDisplayed()

        composeTestRule.onNodeWithText("Source: JTWC").performClick()
        assertEquals(listOf("https://www.metoc.navy.mil/jtwc/products/sh0127.tcw"), openedUrls)
    }

    @Test
    fun `show imagery asks for the event's imagery`() {
        setContent(loaded.copy(selectedEventId = fire.id))

        composeTestRule.onNodeWithTag(EARTH_EVENT_SHOW_IMAGERY_TEST_TAG).performClick()

        assertEquals(1, showImageryClicks)
    }

    @Test
    fun `while an event's imagery shows, a bar names it and leads back to the latest`() {
        val imagery = EventImagery(fire.id, TestEarthEvents.TRUE_COLOR_LAYER, LocalDate.of(2026, 10, 14))
        setContent(loaded.copy(eventImagery = imagery))

        composeTestRule.onNodeWithTag(EARTH_EVENTS_IMAGERY_BAR_TEST_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithText("VIIRS SNPP Corrected Reflectance True Color", substring = true).assertIsDisplayed()
        assertEquals(imagery.toGibsImagery(), shownImagery)

        composeTestRule.onNodeWithText("Latest imagery").performClick()
        assertEquals(1, hideImageryClicks)
    }

    @Test
    fun `offline with nothing cached says how to get events`() {
        setContent(EarthEventsUiState(today = today, isLoaded = true, isOffline = true))

        composeTestRule.onNodeWithTag(OFFLINE_BANNER_TEST_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithTag(EARTH_EVENTS_EMPTY_TEST_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithText("No events saved yet", substring = true).assertIsDisplayed()
    }

    @Test
    fun `offline with a cache shows the events and the banner`() {
        setContent(loaded.copy(isOffline = true))

        composeTestRule.onNodeWithTag(OFFLINE_BANNER_TEST_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithTag(EARTH_EVENTS_EMPTY_TEST_TAG).assertDoesNotExist()
        assertTrue(shownOverlays.flatMap { it.shapes }.isNotEmpty())
    }

    @Test
    fun `missing imagery is explained`() {
        setContent(loaded, tileStatus = TileLoadStatus.ImageryUnavailable)

        composeTestRule.onNodeWithTag(EARTH_EVENTS_TILE_BANNER_TEST_TAG).assertIsDisplayed()
    }

    @Test
    fun `older wildfires stay off the map until the history toggle shows them`() {
        setContent(loaded.copy(events = listOf(storm, fire, oldFire)))

        composeTestRule.onNodeWithText("1 wildfire last reported 2–6 weeks ago is hidden").assertIsDisplayed()
        composeTestRule.onNodeWithText("Wildfires · 1").assertIsDisplayed()
        assertTrue(shownOverlays.none { overlay -> overlay.shapes.any { it.id == oldFire.id } })

        composeTestRule.onNodeWithTag(EARTH_EVENTS_HISTORY_TOGGLE_TEST_TAG).performClick()
        assertEquals(1, historyToggles)
    }

    @Test
    fun `with the history shown, older wildfires are drawn and counted`() {
        setContent(loaded.copy(events = listOf(storm, fire, oldFire), showWildfireHistory = true))

        composeTestRule.onNodeWithText("Showing 1 wildfire last reported 2–6 weeks ago, faded").assertIsDisplayed()
        composeTestRule.onNodeWithText("Hide").assertIsDisplayed()
        composeTestRule.onNodeWithText("Wildfires · 2").assertIsDisplayed()
        assertTrue(shownOverlays.any { overlay -> overlay.shapes.any { it.id == oldFire.id } })
    }

    @Test
    fun `without older wildfires there is no history toggle`() {
        setContent(loaded)

        composeTestRule.onNodeWithTag(EARTH_EVENTS_HISTORY_TOGGLE_TEST_TAG).assertDoesNotExist()
    }

    @Test
    fun `the sheet says how many days ago the event was last reported`() {
        setContent(loaded.copy(events = listOf(oldFire), showWildfireHistory = true, selectedEventId = oldFire.id))

        composeTestRule.onNodeWithText("Last report 20 days ago").assertIsDisplayed()
    }

    @Test
    fun `a progress bar tops the map while new imagery loads`() {
        setContent(loaded, isLoadingMapImagery = true)
        composeTestRule.onNodeWithTag(EARTH_EVENTS_MAP_LOADING_TEST_TAG).assertIsDisplayed()
    }

    @Test
    fun `daily true colour comes with a note on its gaps`() {
        setContent(loaded)

        composeTestRule.onNodeWithText("Daily mosaic", substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithTag(EARTH_EVENTS_MAP_LOADING_TEST_TAG).assertDoesNotExist()
    }

    private companion object {
        const val FAKE_MAP_TAG = "fake-map"
    }
}
