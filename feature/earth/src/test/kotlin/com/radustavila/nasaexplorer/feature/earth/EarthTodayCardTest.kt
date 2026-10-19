package com.radustavila.nasaexplorer.feature.earth

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.radustavila.nasaexplorer.core.testing.data.TestEpicImages
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class EarthTodayCardTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `summarises the newest day and opens the Earth screen`() {
        var clicked = false
        composeTestRule.setContent {
            EarthTodayCardContent(
                uiState = EarthTodayCardUiState(images = TestEpicImages.day(size = 13)),
                onClick = { clicked = true },
            )
        }

        composeTestRule.onNodeWithText("Earth today").assertIsDisplayed()
        composeTestRule.onNodeWithText("13 photos from DSCOVR", substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithTag(EARTH_TODAY_CARD_TEST_TAG).performClick()

        assertTrue(clicked)
    }

    @Test
    fun `shows progress while the first sync runs`() {
        composeTestRule.setContent {
            EarthTodayCardContent(uiState = EarthTodayCardUiState(isLoading = true), onClick = {})
        }

        composeTestRule.onNodeWithText("Downloading today's photos of Earth…").assertIsDisplayed()
    }

    @Test
    fun `takes no space when there is nothing to show`() {
        composeTestRule.setContent {
            EarthTodayCardContent(uiState = EarthTodayCardUiState(), onClick = {})
        }

        composeTestRule.onNodeWithTag(EARTH_TODAY_CARD_TEST_TAG).assertDoesNotExist()
    }
}
