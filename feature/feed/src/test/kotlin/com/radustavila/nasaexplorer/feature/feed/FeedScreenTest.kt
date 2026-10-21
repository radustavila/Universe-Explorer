package com.radustavila.nasaexplorer.feature.feed

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import com.radustavila.nasaexplorer.core.designsystem.component.FAVORITE_BUTTON_TEST_TAG
import com.radustavila.nasaexplorer.core.designsystem.component.LOADING_INDICATOR_TEST_TAG
import com.radustavila.nasaexplorer.core.designsystem.component.OFFLINE_BANNER_TEST_TAG
import com.radustavila.nasaexplorer.core.designsystem.component.RETRY_BUTTON_TEST_TAG
import com.radustavila.nasaexplorer.core.designsystem.error.UiError
import com.radustavila.nasaexplorer.core.model.DataFreshness
import com.radustavila.nasaexplorer.core.testing.data.TestApods
import java.time.Instant
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
    fun `renders the header slot above the cached entries`() {
        composeTestRule.setContent {
            FeedContent(
                uiState = FeedUiState(items = TestApods.feed(size = 1)),
                onApodClick = {},
                onToggleFavorite = {},
                onRefresh = {},
                onRetry = {},
                onLoadMore = {},
                onErrorDismissed = {},
                areNotificationsOn = false,
                onNotificationsClick = {},
                header = { Text("Next launches") },
            )
        }

        composeTestRule.onNodeWithText("Next launches").assertIsDisplayed()
    }

    @Test
    fun `an empty header leaves the usual gutter above the first entry`() {
        val apod = TestApods.feed(size = 1).single()
        var header: @Composable () -> Unit by mutableStateOf({})
        composeTestRule.setContent {
            FeedContent(
                uiState = FeedUiState(items = listOf(apod)),
                onApodClick = {},
                onToggleFavorite = {},
                onRefresh = {},
                onRetry = {},
                onLoadMore = {},
                onErrorDismissed = {},
                areNotificationsOn = false,
                onNotificationsClick = {},
                header = header,
            )
        }
        val withoutHeader = composeTestRule.onNodeWithText(apod.title).getUnclippedBoundsInRoot().top

        header = { Box(Modifier.height(100.dp)) }
        val withHeader = composeTestRule.onNodeWithText(apod.title).getUnclippedBoundsInRoot().top

        // A 100dp header adds itself, its 16dp top gutter and the 16dp gap below it; when empty it
        // took no space, so the entry sat one 16dp gap below the top, as before the slot existed.
        assertEquals(132.dp - 16.dp, withHeader - withoutHeader)
    }

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
                areNotificationsOn = false,
                onNotificationsClick = {},
            )
        }

        composeTestRule.onNodeWithTag(FEED_LIST_TEST_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithText("Apod #0").assertIsDisplayed()
    }

    @Test
    fun `Today cards supplied by the app are shown above the entries`() {
        composeTestRule.setContent {
            FeedContent(
                uiState = FeedUiState(items = TestApods.feed(size = 1)),
                onApodClick = {},
                onToggleFavorite = {},
                onRefresh = {},
                onRetry = {},
                onLoadMore = {},
                onErrorDismissed = {},
                areNotificationsOn = false,
                onNotificationsClick = {},
                header = { androidx.compose.material3.Text("Next launch in 2 h") },
            )
        }

        composeTestRule.onNodeWithText("Next launch in 2 h").assertIsDisplayed()
        composeTestRule.onNodeWithText("Apod #0").assertIsDisplayed()
    }

    @Test
    fun `the calendar action opens the date picker`() {
        val today = LocalDate.of(2026, 9, 10)
        composeTestRule.setContent {
            FeedContent(
                uiState = FeedUiState(
                    items = TestApods.feed(size = 1),
                    latestSelectableDate = today,
                ),
                onApodClick = {},
                onToggleFavorite = {},
                onRefresh = {},
                onRetry = {},
                onLoadMore = {},
                onErrorDismissed = {},
                areNotificationsOn = false,
                onNotificationsClick = {},
            )
        }

        composeTestRule.onNodeWithTag(FEED_CALENDAR_BUTTON_TEST_TAG).performClick()

        composeTestRule.onNodeWithText("Jump to a day").assertIsDisplayed()
    }

    @Test
    fun `the notification action reflects its state and reports taps`() {
        var clicks = 0
        composeTestRule.setContent {
            FeedContent(
                uiState = FeedUiState(items = TestApods.feed(size = 1)),
                areNotificationsOn = false,
                onApodClick = {},
                onToggleFavorite = {},
                onRefresh = {},
                onRetry = {},
                onLoadMore = {},
                onErrorDismissed = {},
                onNotificationsClick = { clicks++ },
            )
        }

        composeTestRule
            .onNodeWithContentDescription("Notify me about each new picture of the day")
            .performClick()

        assertEquals(1, clicks)
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
                areNotificationsOn = false,
                onNotificationsClick = {},
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
                areNotificationsOn = false,
                onNotificationsClick = {},
            )
        }

        composeTestRule.onNodeWithTag(OFFLINE_BANNER_TEST_TAG).assertIsDisplayed()
    }

    @Test
    fun `the entries say when they were last updated`() {
        composeTestRule.setContent {
            FeedContent(
                uiState = FeedUiState(
                    items = TestApods.feed(size = 1),
                    freshness = DataFreshness(lastUpdatedAt = Instant.now().minusSeconds(5)),
                ),
                onApodClick = {},
                onToggleFavorite = {},
                onRefresh = {},
                onRetry = {},
                onLoadMore = {},
                onErrorDismissed = {},
                areNotificationsOn = false,
                onNotificationsClick = {},
            )
        }

        composeTestRule.onNodeWithText("Updated just now").assertIsDisplayed()
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
                areNotificationsOn = false,
                onNotificationsClick = {},
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
                areNotificationsOn = false,
                onNotificationsClick = {},
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
                areNotificationsOn = false,
                onNotificationsClick = {},
            )
        }

        composeTestRule.onAllNodesWithTag(FAVORITE_BUTTON_TEST_TAG).onFirst().performClick()

        assertEquals(apod.title, toggled)
    }

    @Test
    fun `cards announce NASA's alt text for the picture`() {
        composeTestRule.setContent {
            FeedContent(
                uiState = FeedUiState(
                    items = listOf(TestApods.image().copy(altText = "Barred spiral galaxy, face on")),
                ),
                areNotificationsOn = false,
                onApodClick = {},
                onToggleFavorite = {},
                onRefresh = {},
                onRetry = {},
                onLoadMore = {},
                onErrorDismissed = {},
                onNotificationsClick = {},
            )
        }

        composeTestRule.onNodeWithContentDescription("Barred spiral galaxy, face on").assertExists()
    }

    @Test
    fun `search opens with its scope stated and reports what is typed`() {
        val queries = mutableListOf<String?>()
        composeTestRule.setContent {
            FeedContent(
                uiState = FeedUiState(items = TestApods.feed(size = 2)),
                areNotificationsOn = false,
                onApodClick = {},
                onToggleFavorite = {},
                onRefresh = {},
                onRetry = {},
                onLoadMore = {},
                onErrorDismissed = {},
                onNotificationsClick = {},
                onSearchQueryChange = { queries += it },
            )
        }

        composeTestRule.onNodeWithTag(FEED_SEARCH_BUTTON_TEST_TAG).performClick()
        composeTestRule.onNodeWithTag(FEED_SEARCH_FIELD_TEST_TAG).performTextInput("nebula")

        composeTestRule
            .onNodeWithText("Search only looks through pictures saved on this device", substring = true)
            .assertIsDisplayed()
        composeTestRule.waitForIdle()
        assertEquals(listOf(null, "", "nebula"), queries)

        composeTestRule.onNodeWithContentDescription("Close search").performClick()
        composeTestRule.waitForIdle()
        assertEquals(null, queries.last())
        composeTestRule.onNodeWithTag(FEED_LIST_TEST_TAG).assertIsDisplayed()
    }

    @Test
    fun `search results open their entry`() {
        var opened: LocalDate? = null
        val match = TestApods.image(title = "Crab Nebula")
        composeTestRule.setContent {
            FeedContent(
                uiState = FeedUiState(
                    items = TestApods.feed(size = 2),
                    search = FeedSearchState(query = "crab", results = listOf(match)),
                ),
                areNotificationsOn = false,
                onApodClick = { opened = it },
                onToggleFavorite = {},
                onRefresh = {},
                onRetry = {},
                onLoadMore = {},
                onErrorDismissed = {},
                onNotificationsClick = {},
            )
        }

        composeTestRule.onNodeWithTag(FEED_SEARCH_BUTTON_TEST_TAG).performClick()
        composeTestRule.onNodeWithText("Crab Nebula").performClick()

        assertEquals(match.date, opened)
    }

    @Test
    fun `On this day shows earlier years and opens the entry`() {
        var opened: LocalDate? = null
        val lastYear = TestApods.image(date = LocalDate.of(2025, 9, 10), title = "A year ago")
        composeTestRule.setContent {
            FeedContent(
                uiState = FeedUiState(items = TestApods.feed(size = 2), onThisDay = listOf(lastYear)),
                areNotificationsOn = false,
                onApodClick = { opened = it },
                onToggleFavorite = {},
                onRefresh = {},
                onRetry = {},
                onLoadMore = {},
                onErrorDismissed = {},
                onNotificationsClick = {},
            )
        }

        composeTestRule.onNodeWithTag(FEED_ON_THIS_DAY_TEST_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithText("2025").assertIsDisplayed()
        composeTestRule.onNodeWithText("A year ago").performClick()

        assertEquals(lastYear.date, opened)
    }

    @Test
    fun `the daily wallpaper switch lives in the overflow menu`() {
        val changes = mutableListOf<Boolean>()
        composeTestRule.setContent {
            FeedContent(
                uiState = FeedUiState(items = TestApods.feed(size = 1), isDailyWallpaperEnabled = false),
                areNotificationsOn = false,
                onApodClick = {},
                onToggleFavorite = {},
                onRefresh = {},
                onRetry = {},
                onLoadMore = {},
                onErrorDismissed = {},
                onNotificationsClick = {},
                onDailyWallpaperChange = { changes += it },
            )
        }

        composeTestRule.onNodeWithTag(FEED_MORE_BUTTON_TEST_TAG).performClick()
        composeTestRule.onNodeWithTag(FEED_DAILY_WALLPAPER_TEST_TAG).assertIsOff().performClick()

        assertEquals(listOf(true), changes)
    }

    @Test
    fun `On this day arriving after the feed is shown at the top, not scrolled out of view`() {
        var uiState by mutableStateOf(FeedUiState(items = TestApods.feed(size = 5)))
        composeTestRule.setContent {
            FeedContent(
                uiState = uiState,
                areNotificationsOn = false,
                onApodClick = {},
                onToggleFavorite = {},
                onRefresh = {},
                onRetry = {},
                onLoadMore = {},
                onErrorDismissed = {},
                onNotificationsClick = {},
            )
        }
        composeTestRule.onNodeWithText("Apod #0").assertIsDisplayed()

        uiState = uiState.copy(
            onThisDay = listOf(TestApods.image(date = LocalDate.of(2025, 9, 10), title = "A year ago")),
        )

        composeTestRule.onNodeWithTag(FEED_ON_THIS_DAY_TEST_TAG).assertIsDisplayed()
    }

    @Test
    fun `a header and On this day arriving after the feed are shown at the top`() {
        var uiState by mutableStateOf(FeedUiState(items = TestApods.feed(size = 5)))
        var header: @Composable () -> Unit by mutableStateOf({})
        composeTestRule.setContent {
            FeedContent(
                uiState = uiState,
                areNotificationsOn = false,
                onApodClick = {},
                onToggleFavorite = {},
                onRefresh = {},
                onRetry = {},
                onLoadMore = {},
                onErrorDismissed = {},
                onNotificationsClick = {},
                header = header,
            )
        }
        composeTestRule.onNodeWithText("Apod #0").assertIsDisplayed()

        // Both load later than the feed, as on a first launch: launches first, then On this day.
        header = { Text("Next launches", modifier = Modifier.height(120.dp)) }
        composeTestRule.waitForIdle()
        uiState = uiState.copy(
            onThisDay = listOf(TestApods.image(date = LocalDate.of(2025, 9, 10), title = "A year ago")),
        )

        composeTestRule.onNodeWithText("Next launches").assertIsDisplayed()
        composeTestRule.onNodeWithTag(FEED_ON_THIS_DAY_TEST_TAG).assertIsDisplayed()
    }

    @Test
    fun `content arriving above the feed does not pull back a reader who has scrolled`() {
        var uiState by mutableStateOf(FeedUiState(items = TestApods.feed(size = 8)))
        composeTestRule.setContent {
            FeedContent(
                uiState = uiState,
                areNotificationsOn = false,
                onApodClick = {},
                onToggleFavorite = {},
                onRefresh = {},
                onRetry = {},
                onLoadMore = {},
                onErrorDismissed = {},
                onNotificationsClick = {},
            )
        }
        composeTestRule.onNodeWithTag(FEED_LIST_TEST_TAG).performTouchInput { swipeUp() }
        composeTestRule.waitForIdle()

        uiState = uiState.copy(
            onThisDay = listOf(TestApods.image(date = LocalDate.of(2025, 9, 10), title = "A year ago")),
        )

        composeTestRule.onNodeWithTag(FEED_ON_THIS_DAY_TEST_TAG).assertDoesNotExist()
    }
}
