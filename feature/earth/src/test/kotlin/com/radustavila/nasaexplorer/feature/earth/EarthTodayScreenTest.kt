package com.radustavila.nasaexplorer.feature.earth

import android.content.Context
import android.provider.Settings
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTouchHeightIsEqualTo
import androidx.compose.ui.test.assertTouchWidthIsEqualTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.radustavila.nasaexplorer.core.designsystem.component.RETRY_BUTTON_TEST_TAG
import com.radustavila.nasaexplorer.core.model.DataFreshness
import com.radustavila.nasaexplorer.core.model.EpicCollection
import com.radustavila.nasaexplorer.core.model.EpicImage
import com.radustavila.nasaexplorer.core.testing.data.TestEpicImages
import java.time.Instant
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
class EarthTodayScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `renders the day as a rotation with time and centroid`() {
        setContent(EarthTodayUiState(images = TestEpicImages.day(size = 3)))

        composeTestRule.onNodeWithTag(EARTH_PAGER_TEST_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithText("Photo 1 of 3").assertIsDisplayed()
        composeTestRule.onNodeWithText("Facing 0.7° N, 163.7° E").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Play rotation").assertIsDisplayed()
    }

    @Test
    fun `the calendar is disabled until the archive listing is known`() {
        setContent(EarthTodayUiState(images = TestEpicImages.day()))
        composeTestRule.onNodeWithTag(EARTH_CALENDAR_BUTTON_TEST_TAG).assertIsNotEnabled()
    }

    @Test
    fun `the calendar opens the date picker once dates are known`() {
        setContent(
            EarthTodayUiState(images = TestEpicImages.day(), availableDates = setOf(TestEpicImages.LATEST_DATE)),
        )

        composeTestRule.onNodeWithTag(EARTH_CALENDAR_BUTTON_TEST_TAG).assertIsEnabled().performClick()

        composeTestRule.onNodeWithText("Show Earth on").assertIsDisplayed()
    }

    @Test
    fun `the collection toggle switches to the enhanced set`() {
        var selected: EpicCollection? = null
        setContent(EarthTodayUiState(images = TestEpicImages.day()), onCollectionSelected = { selected = it })

        composeTestRule.onNodeWithText("Enhanced").performClick()

        assertEquals(EpicCollection.ENHANCED, selected)
    }

    @Test
    fun `an archive day offers the way back to the latest one`() {
        var picked: Any? = "untouched"
        setContent(
            EarthTodayUiState(images = TestEpicImages.day(), selectedDate = TestEpicImages.LATEST_DATE),
            onDateSelected = { picked = it },
        )

        composeTestRule.onNodeWithContentDescription("Back to the latest day").performClick()

        assertEquals(null, picked)
    }

    @Test
    fun `offline with nothing cached explains why and offers a retry`() {
        var retried = false
        setContent(EarthTodayUiState(isOffline = true), onRetry = { retried = true })

        composeTestRule.onNodeWithText("You are offline").assertIsDisplayed()
        composeTestRule.onNodeWithTag(RETRY_BUTTON_TEST_TAG).performClick()

        assertTrue(retried)
    }

    @Test
    fun `shows an up arrow only when pushed`() {
        var backClicked = false
        setContent(EarthTodayUiState(images = TestEpicImages.day()), onBackClick = { backClicked = true })

        composeTestRule.onNodeWithContentDescription("Back").performClick()

        assertTrue(backClicked)
    }

    @Test
    fun `the heart saves the photo on screen and shows whether it is saved`() {
        val day = TestEpicImages.day(size = 3)
        var toggled: EpicImage? = null
        setContent(
            EarthTodayUiState(images = day, favoriteIds = setOf(day[1].ref.favoriteId)),
            onToggleFavorite = { toggled = it },
        )

        composeTestRule.onNodeWithContentDescription("Add to favorites").performClick()

        assertEquals(day[0], toggled)
    }

    @Test
    fun `opened for one photo it starts on that photo, saved and holding still`() {
        val day = TestEpicImages.day(size = 3)
        setContent(
            EarthTodayUiState(images = day, focusedImageId = day[2].identifier, favoriteIds = setOf(day[2].ref.favoriteId)),
            autoPlayOverride = true,
        )

        composeTestRule.onNodeWithText("Photo 3 of 3").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Play rotation").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Remove from favorites").assertIsDisplayed()
    }

    @Test
    fun `with system animations removed the rotation starts paused`() {
        Settings.Global.putFloat(
            ApplicationProvider.getApplicationContext<Context>().contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            0f,
        )
        // Should the rotation start anyway, a frozen clock keeps it from spinning the test forever.
        composeTestRule.mainClock.autoAdvance = false
        setContent(EarthTodayUiState(images = TestEpicImages.day(size = 3)), autoPlayOverride = true)
        composeTestRule.mainClock.advanceTimeByFrame()

        composeTestRule.onNodeWithContentDescription("Play rotation").assertIsDisplayed()
        composeTestRule.onNodeWithText("Photo 1 of 3").assertIsDisplayed()
    }

    @Test
    fun `the play button and the heart are 48 dp to touch`() {
        setContent(EarthTodayUiState(images = TestEpicImages.day(size = 3)))

        composeTestRule.onNodeWithTag(EARTH_PLAY_BUTTON_TEST_TAG).assertTouchHeightIsEqualTo(48.dp)
            .assertTouchWidthIsEqualTo(48.dp)
        composeTestRule.onNodeWithContentDescription("Add to favorites").assertTouchHeightIsEqualTo(48.dp)
            .assertTouchWidthIsEqualTo(48.dp)
    }

    @Test
    fun `pulling down the photos asks for a refresh`() {
        var refreshes = 0
        setContent(EarthTodayUiState(images = TestEpicImages.day(size = 3)), onRefresh = { refreshes++ })

        composeTestRule.onNodeWithTag(EARTH_PULL_TO_REFRESH_TEST_TAG).performTouchInput {
            swipeDown(startY = top + 10f, endY = bottom, durationMillis = 500)
        }
        composeTestRule.waitForIdle()

        assertEquals(1, refreshes)
    }

    @Test
    fun `without photos there is nothing to pull`() {
        var refreshes = 0
        setContent(EarthTodayUiState(isOffline = true), onRefresh = { refreshes++ })

        composeTestRule.onNodeWithTag(EARTH_PULL_TO_REFRESH_TEST_TAG).performTouchInput {
            swipeDown(startY = top + 10f, endY = bottom, durationMillis = 500)
        }
        composeTestRule.waitForIdle()

        assertEquals(0, refreshes)
    }

    @Test
    fun `the photos say when EPIC was last updated`() {
        setContent(
            EarthTodayUiState(
                images = TestEpicImages.day(size = 3),
                freshness = DataFreshness(lastUpdatedAt = Instant.now().minusSeconds(5)),
            ),
        )

        composeTestRule.onNodeWithText("Updated just now").assertIsDisplayed()
    }

    @Test
    fun `the footer sits below the rotation, and below an empty state too`() {
        setContent(EarthTodayUiState(isOffline = true), footer = { Text("Satellite map") })

        composeTestRule.onNodeWithText("Satellite map").performScrollTo().assertIsDisplayed()
    }

    private fun setContent(
        uiState: EarthTodayUiState,
        onBackClick: (() -> Unit)? = null,
        onCollectionSelected: (EpicCollection) -> Unit = {},
        onDateSelected: (LocalDate?) -> Unit = {},
        onRefresh: () -> Unit = {},
        onRetry: () -> Unit = {},
        onToggleFavorite: (EpicImage) -> Unit = {},
        footer: (@Composable () -> Unit)? = null,
        // Only safe where something else stops the rotation (a focused photo holds still).
        autoPlayOverride: Boolean = false,
    ) {
        composeTestRule.setContent {
            EarthTodayContent(
                uiState = uiState,
                onBackClick = onBackClick,
                onCollectionSelected = onCollectionSelected,
                onDateSelected = onDateSelected,
                onRefresh = onRefresh,
                onRetry = onRetry,
                onErrorDismissed = {},
                onToggleFavorite = onToggleFavorite,
                footer = footer,
                autoPlay = autoPlayOverride,
            )
        }
    }
}
