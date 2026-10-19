package com.radustavila.nasaexplorer.feature.earth

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.radustavila.nasaexplorer.core.designsystem.component.RETRY_BUTTON_TEST_TAG
import com.radustavila.nasaexplorer.core.model.EpicCollection
import com.radustavila.nasaexplorer.core.testing.data.TestEpicImages
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

    private fun setContent(
        uiState: EarthTodayUiState,
        onBackClick: (() -> Unit)? = null,
        onCollectionSelected: (EpicCollection) -> Unit = {},
        onDateSelected: (LocalDate?) -> Unit = {},
        onRetry: () -> Unit = {},
    ) {
        composeTestRule.setContent {
            EarthTodayContent(
                uiState = uiState,
                onBackClick = onBackClick,
                onCollectionSelected = onCollectionSelected,
                onDateSelected = onDateSelected,
                onRetry = onRetry,
                onErrorDismissed = {},
                autoPlay = false,
            )
        }
    }
}
