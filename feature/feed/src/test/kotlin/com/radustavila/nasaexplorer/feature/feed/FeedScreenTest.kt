package com.radustavila.nasaexplorer.feature.feed

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.radustavila.nasaexplorer.core.designsystem.component.FAVORITE_BUTTON_TEST_TAG
import com.radustavila.nasaexplorer.core.designsystem.component.LOADING_INDICATOR_TEST_TAG
import com.radustavila.nasaexplorer.core.designsystem.component.OFFLINE_BANNER_TEST_TAG
import com.radustavila.nasaexplorer.core.designsystem.component.RETRY_BUTTON_TEST_TAG
import com.radustavila.nasaexplorer.core.designsystem.error.UiError
import com.radustavila.nasaexplorer.core.testing.data.TestApods
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import com.radustavila.nasaexplorer.core.designsystem.R as DesignSystemR

/**
 * Compose tests run on Robolectric so the important user-visible flows are verified on every CI run
 * without an emulator.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w400dp-h800dp")
class FeedScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `renders cached entries`() {
        composeTestRule.setContent {
            FeedContent(
                uiState = FeedUiState(items = TestApods.feed(size = 3)),
                onApodClick = {},
                onToggleFavorite = {},
                onRefresh = {},
                onRetry = {},
                onLoadMore = {},
                onErrorDismissed = {},
            )
        }

        composeTestRule.onNodeWithTag(FEED_LIST_TEST_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithText("Apod #0").assertIsDisplayed()
    }

    @Test
    fun `an empty cache while loading shows the loading indicator`() {
        composeTestRule.setContent {
            FeedContent(
                uiState = FeedUiState(isLoading = true),
                onApodClick = {},
                onToggleFavorite = {},
                onRefresh = {},
                onRetry = {},
                onLoadMore = {},
                onErrorDismissed = {},
            )
        }

        composeTestRule.onNodeWithTag(LOADING_INDICATOR_TEST_TAG).assertIsDisplayed()
    }

    @Test
    fun `offline with cached entries shows the stale-content banner`() {
        composeTestRule.setContent {
            FeedContent(
                uiState = FeedUiState(items = TestApods.feed(size = 1), isOffline = true),
                onApodClick = {},
                onToggleFavorite = {},
                onRefresh = {},
                onRetry = {},
                onLoadMore = {},
                onErrorDismissed = {},
            )
        }

        composeTestRule.onNodeWithTag(OFFLINE_BANNER_TEST_TAG).assertIsDisplayed()
    }

    @Test
    fun `offline with an empty cache offers a retry`() {
        var retries = 0
        composeTestRule.setContent {
            FeedContent(
                uiState = FeedUiState(
                    isOffline = true,
                    error = UiError(DesignSystemR.string.error_no_connection),
                ),
                onApodClick = {},
                onToggleFavorite = {},
                onRefresh = {},
                onRetry = { retries++ },
                onLoadMore = {},
                onErrorDismissed = {},
            )
        }

        composeTestRule.onNodeWithTag(RETRY_BUTTON_TEST_TAG).performClick()

        assertEquals(1, retries)
    }

    @Test
    fun `tapping an entry opens its details`() {
        var openedDate: LocalDate? = null
        val apod = TestApods.image()
        composeTestRule.setContent {
            FeedContent(
                uiState = FeedUiState(items = listOf(apod)),
                onApodClick = { date -> openedDate = date },
                onToggleFavorite = {},
                onRefresh = {},
                onRetry = {},
                onLoadMore = {},
                onErrorDismissed = {},
            )
        }

        composeTestRule.onNodeWithText(apod.title).performClick()

        assertEquals(apod.date, openedDate)
    }

    @Test
    fun `tapping the heart toggles the favorite`() {
        val apod = TestApods.image()
        var toggled: String? = null
        composeTestRule.setContent {
            FeedContent(
                uiState = FeedUiState(items = listOf(apod)),
                onApodClick = {},
                onToggleFavorite = { toggled = it.title },
                onRefresh = {},
                onRetry = {},
                onLoadMore = {},
                onErrorDismissed = {},
            )
        }

        composeTestRule.onAllNodesWithTag(FAVORITE_BUTTON_TEST_TAG).onFirst().performClick()

        assertEquals(apod.title, toggled)
    }
}
