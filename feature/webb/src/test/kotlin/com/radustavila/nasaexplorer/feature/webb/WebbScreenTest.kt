package com.radustavila.nasaexplorer.feature.webb

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.radustavila.nasaexplorer.core.designsystem.theme.NasaTheme
import com.radustavila.nasaexplorer.core.domain.usecase.statusAt
import com.radustavila.nasaexplorer.core.model.DataFreshness
import com.radustavila.nasaexplorer.core.model.WebbSchedule
import com.radustavila.nasaexplorer.core.testing.data.TestWebb
import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WebbScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val week = TestWebb.week()
    private val synced = DataFreshness(lastUpdatedAt = week.fetchedAt)

    private fun state(now: Instant, schedule: WebbSchedule = week) = WebbUiState(
        isLoaded = true,
        now = schedule.statusAt(now),
        visits = schedule.visits,
        freshness = synced,
    )

    private fun showCard(uiState: WebbUiState, onOpen: () -> Unit = {}, onTargetClick: (String) -> Unit = {}) {
        composeTestRule.setContent { NasaTheme { WebbNowCardContent(uiState, onOpen, onTargetClick) } }
    }

    @Test
    fun `the card shows the visit scheduled now and the next three`() {
        showCard(state(TestWebb.NOW))

        composeTestRule.onNodeWithText("Scheduled now").assertIsDisplayed()
        composeTestRule.onNodeWithText("WASP-39").assertIsDisplayed()
        composeTestRule.onNodeWithText("NIRSpec Bright Object Time Series").assertIsDisplayed()
        composeTestRule.onNodeWithText("Star · Exoplanet Systems").assertIsDisplayed()
        composeTestRule.onNodeWithText("Up next").assertIsDisplayed()
        composeTestRule.onNode(hasText("Jupiter · NIRCam Imaging", substring = true)).assertIsDisplayed()
        composeTestRule.onNode(hasText("Calibration · MIRI Dark", substring = true)).assertIsDisplayed()
        composeTestRule.onNode(hasText("NGC-3324", substring = true)).assertIsDisplayed()
    }

    @Test
    fun `a calibration visit is shown as calibration`() {
        showCard(state(Instant.parse("2026-10-23T06:40:00Z")))

        composeTestRule.onNodeWithText("Calibration").assertIsDisplayed()
        composeTestRule.onNodeWithText("WFSC NIRCam Fine Phasing").assertIsDisplayed()
    }

    @Test
    fun `gaps say slewing or when the next visit is`() {
        showCard(state(Instant.parse("2026-10-23T07:00:00Z")))
        composeTestRule.onNodeWithText("Slewing to the next target").assertIsDisplayed()
        composeTestRule.onNode(hasText("Next at", substring = true) and hasText("WASP-39", substring = true))
            .assertIsDisplayed()
    }

    @Test
    fun `a long pause says no visit is scheduled and when the next one is`() {
        showCard(state(Instant.parse("2026-10-23T16:00:00Z")))

        composeTestRule.onNodeWithText("No visit scheduled right now").assertIsDisplayed()
        composeTestRule.onNode(hasText("Next at", substring = true) and hasText("Jupiter", substring = true))
            .assertIsDisplayed()
    }

    @Test
    fun `a week older than eight days shows the stale note`() {
        showCard(state(TestWebb.FINE_PHASING.start.plus(Duration.ofDays(9))))

        composeTestRule.onNodeWithText("Every visit in this schedule is over").assertIsDisplayed()
        composeTestRule.onNode(hasText("A newer week hasn't downloaded yet", substring = true)).assertIsDisplayed()
    }

    @Test
    fun `tapping the target searches the gallery for it, the card opens the week`() {
        var query: String? = null
        var opened = false
        showCard(state(TestWebb.NOW), onOpen = { opened = true }, onTargetClick = { query = it })

        composeTestRule.onNodeWithText("WASP-39").performClick()
        assertEquals("WASP 39", query)
        composeTestRule.onNodeWithText("Webb right now").performClick()
        assertEquals(true, opened)
    }

    @Test
    fun `before the first download the card says so`() {
        showCard(WebbUiState(isLoaded = true))

        composeTestRule.onNodeWithText("Webb's weekly schedule hasn't downloaded yet.").assertIsDisplayed()
    }

    @Test
    fun `the week's timeline lists every visit with its parallels and the credit`() {
        var query: String? = null
        composeTestRule.setContent {
            NasaTheme {
                WebbContent(state(TestWebb.NOW), onBackClick = {}, onTargetClick = { query = it }, onRefresh = {})
            }
        }

        val timeline = composeTestRule.onNodeWithTag(WEBB_TIMELINE_TEST_TAG)
        timeline.performScrollToNode(hasText("In parallel: MIRI Imaging"))
        composeTestRule.onNodeWithText("In parallel: MIRI Imaging").assertIsDisplayed()
        composeTestRule.onNodeWithText("Jupiter").performClick()
        assertEquals("Jupiter", query)
        timeline.performScrollToNode(hasText("Gas and dust between the stars · Star formation"))
        composeTestRule.onNodeWithText("Gas and dust between the stars · Star formation").assertIsDisplayed()
        timeline.performScrollToNode(hasText("Space Telescope Science Institute", substring = true))
    }

    @Test
    fun `the timeline before the first download offers to try again`() {
        composeTestRule.setContent {
            NasaTheme {
                WebbContent(WebbUiState(isLoaded = true), onBackClick = {}, onTargetClick = {}, onRefresh = {})
            }
        }

        composeTestRule.onNodeWithText("No schedule yet").assertIsDisplayed()
    }
}
