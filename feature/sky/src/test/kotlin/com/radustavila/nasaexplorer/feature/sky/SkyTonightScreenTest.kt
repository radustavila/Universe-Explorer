package com.radustavila.nasaexplorer.feature.sky

import androidx.compose.foundation.layout.Box
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.radustavila.nasaexplorer.core.domain.sky.IssPassForecast
import com.radustavila.nasaexplorer.core.domain.sky.IssTrack
import com.radustavila.nasaexplorer.core.map.overlay.MapOverlay
import com.radustavila.nasaexplorer.core.model.DataFreshness
import com.radustavila.nasaexplorer.core.testing.data.TestSky
import com.radustavila.nasaexplorer.feature.sky.component.ISS_POSITION_OVERLAY_ID
import com.radustavila.nasaexplorer.feature.sky.component.ISS_TRACK_OVERLAY_ID
import com.radustavila.nasaexplorer.feature.sky.component.OBSERVER_OVERLAY_ID
import com.radustavila.nasaexplorer.feature.sky.component.SKY_ISS_CARD_TEST_TAG
import com.radustavila.nasaexplorer.feature.sky.component.SKY_ISS_PASS_TEST_TAG
import com.radustavila.nasaexplorer.feature.sky.component.SKY_ISS_REMINDER_SWITCH_TEST_TAG
import com.radustavila.nasaexplorer.feature.sky.component.SKY_LOCATION_PROMPT_TEST_TAG
import com.radustavila.nasaexplorer.feature.sky.component.SKY_MOON_CARD_TEST_TAG
import com.radustavila.nasaexplorer.feature.sky.component.SKY_PEOPLE_CARD_TEST_TAG
import com.radustavila.nasaexplorer.feature.sky.component.SKY_TRACK_CARD_TEST_TAG
import com.radustavila.nasaexplorer.feature.sky.component.SKY_USE_LOCATION_BUTTON_TEST_TAG
import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w400dp-h2000dp")
class SkyTonightScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private var shownOverlays: List<MapOverlay> = emptyList()
    private val visible = TestSky.pass()
    private val invisible = TestSky.pass(start = Instant.parse("2026-10-20T05:22:31Z"), isVisible = false)
    private val located = SkyTonightUiState(
        isLocationLoaded = true,
        location = TestSky.location(),
        sky = TestSky.skyTonight(),
        passes = IssPassForecast(TestSky.issElements(), listOf(visible, invisible), isStale = false),
        track = IssTrack(TestSky.position(), groundTrack = listOf(TestSky.position(), TestSky.position(longitude = -150.0))),
        people = listOf(TestSky.person(), TestSky.person(id = 2, name = "Zhu Yangzhu", agency = "CNSA")),
    )

    @Test
    fun `without a location it explains why and offers both ways to give one`() {
        var useLocationClicks = 0
        setContent(SkyTonightUiState(isLocationLoaded = true), onUseMyLocation = { useLocationClicks++ })

        composeTestRule.onNodeWithTag(SKY_LOCATION_PROMPT_TEST_TAG).assertExists()
        composeTestRule.onNodeWithText(string(R.string.sky_choose_city)).assertExists()
        composeTestRule.onNodeWithTag(SKY_USE_LOCATION_BUTTON_TEST_TAG).performClick()
        assertEquals(1, useLocationClicks)
        // The crew and the station do not depend on where the user is.
        composeTestRule.onNodeWithTag(SKY_TRACK_CARD_TEST_TAG).assertExists()
    }

    @Test
    fun `the moon, planets and visible passes are listed for the place`() {
        setContent(located)

        composeTestRule.onNodeWithText("Bucharest").assertExists()
        composeTestRule.onNodeWithTag(SKY_MOON_CARD_TEST_TAG).assertExists()
        composeTestRule.onNodeWithText(string(R.string.moon_phase_first_quarter)).assertExists()
        composeTestRule.onNodeWithText("58% lit").assertExists()
        composeTestRule.onNodeWithText(string(R.string.planet_saturn)).assertExists()
        composeTestRule.onNodeWithText(string(R.string.sky_planet_not_visible)).assertExists()

        scrollTo(SKY_ISS_CARD_TEST_TAG)
        // Only the visible pass, until "All passes" is chosen.
        composeTestRule.onAllNodesWithTag(SKY_ISS_PASS_TEST_TAG).assertCountEquals(1)
        composeTestRule.onNodeWithText(string(R.string.sky_iss_show_all)).performClick()
        composeTestRule.onAllNodesWithTag(SKY_ISS_PASS_TEST_TAG).assertCountEquals(2)
    }

    @Test
    fun `each card's title is a heading TalkBack can jump to`() {
        setContent(located)

        listOf(R.string.sky_night_title, R.string.sky_moon_title, R.string.sky_planets_title).forEach { title ->
            composeTestRule.onNodeWithText(string(title)).assert(isHeading)
        }
    }

    @Test
    fun `the screen says when its data was last updated`() {
        setContent(located.copy(freshness = DataFreshness(lastUpdatedAt = Instant.now().minusSeconds(5))))

        composeTestRule.onNodeWithText("Updated just now").assertExists()
    }

    @Test
    fun `stale orbit data asks for a refresh instead of listing passes`() {
        setContent(located.copy(passes = IssPassForecast(TestSky.issElements(), emptyList(), isStale = true)))

        scrollTo(SKY_ISS_CARD_TEST_TAG)
        composeTestRule.onAllNodesWithTag(SKY_ISS_PASS_TEST_TAG).assertCountEquals(0)
        composeTestRule.onNode(hasText("too old", substring = true)).assertExists()
    }

    @Test
    fun `the reminder switch reports taps`() {
        var taps = 0
        setContent(located, onRemindersClick = { taps++ })

        scrollTo(SKY_ISS_CARD_TEST_TAG)
        composeTestRule.onNodeWithTag(SKY_ISS_REMINDER_SWITCH_TEST_TAG).performClick()

        assertEquals(1, taps)
    }

    @Test
    fun `people in space are counted and listed with how long they have been up`() {
        setContent(located.copy(sky = located.sky?.copy(computedAt = TestSky.person().launchedAt!!.plus(Duration.ofDays(18)))))

        scrollTo(SKY_PEOPLE_CARD_TEST_TAG)
        composeTestRule.onNodeWithText("2 people are in space right now").assertExists()
        composeTestRule.onNodeWithText("Zhu Yangzhu").assertExists()
        composeTestRule.onAllNodesWithText("Up for 18 days", substring = true).assertCountEquals(2)
    }

    @Test
    fun `the station's live position is described under the track`() {
        setContent(located)

        scrollTo(SKY_TRACK_CARD_TEST_TAG)
        composeTestRule.onNode(hasText("Live", substring = true)).assertExists()
        composeTestRule.onNodeWithText(string(R.string.sky_track_eclipsed), substring = true).assertExists()
        composeTestRule.onNodeWithTag(FAKE_MAP_TAG).assertExists()
        // Track, then the observer, then the station on top.
        assertEquals(
            listOf(ISS_TRACK_OVERLAY_ID, OBSERVER_OVERLAY_ID, ISS_POSITION_OVERLAY_ID),
            shownOverlays.map { it.id },
        )
        assertEquals(1, shownOverlays.first { it.id == ISS_POSITION_OVERLAY_ID }.shapes.size)
    }

    private fun scrollTo(tag: String) {
        composeTestRule.onNodeWithTag(SKY_LIST_TEST_TAG).performScrollToNode(hasTestTag(tag))
    }

    private fun setContent(
        state: SkyTonightUiState,
        onUseMyLocation: () -> Unit = {},
        onRemindersClick: () -> Unit = {},
    ) {
        composeTestRule.setContent {
            SkyTonightContent(
                uiState = state,
                remindersOn = false,
                trackMap = { overlays, _, modifier ->
                    shownOverlays = overlays
                    Box(modifier.testTag(FAKE_MAP_TAG))
                },
                onBackClick = {},
                onUseMyLocation = onUseMyLocation,
                onChooseLocation = {},
                onRemindersClick = onRemindersClick,
                onRefresh = {},
            )
        }
    }

    private companion object {
        const val FAKE_MAP_TAG = "fake-map"
        val isHeading = SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)
    }

    private fun string(id: Int): String =
        androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>().getString(id)
}
