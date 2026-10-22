package com.radustavila.nasaexplorer.feature.news

import android.content.Intent
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import com.radustavila.nasaexplorer.core.model.DataFreshness
import com.radustavila.nasaexplorer.core.testing.data.TestNews
import com.radustavila.nasaexplorer.feature.news.item.NEWS_ITEM_OPEN_TEST_TAG
import com.radustavila.nasaexplorer.feature.news.item.NewsItemContent
import com.radustavila.nasaexplorer.feature.news.item.NewsItemUiState
import com.radustavila.nasaexplorer.feature.news.list.NEWS_LIST_TEST_TAG
import com.radustavila.nasaexplorer.feature.news.list.NewsContent
import com.radustavila.nasaexplorer.feature.news.list.NewsUiState
import com.radustavila.nasaexplorer.feature.news.today.LATEST_FROM_NASA_SEE_ALL_TEST_TAG
import com.radustavila.nasaexplorer.feature.news.today.LatestFromNasaCardContent
import com.radustavila.nasaexplorer.feature.news.today.LatestFromNasaUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

/**
 * Where a tap leads: stories to a Custom Tab, videos to whatever opens YouTube links (the YouTube
 * app), Images of the Day to their own screen. Robolectric records the activities started.
 */
@RunWith(RobolectricTestRunner::class)
class NewsHandOffTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private var openedImage: String? = null

    private fun nextStartedActivity(): Intent? = shadowOf(RuntimeEnvironment.getApplication()).nextStartedActivity

    private fun showList() {
        composeTestRule.setContent {
            NewsContent(
                uiState = NewsUiState(
                    items = TestNews.ALL,
                    liveItem = TestNews.video(),
                    hasNews = true,
                    isLoaded = true,
                    freshness = DataFreshness(lastUpdatedAt = TestNews.NOW),
                    now = TestNews.NOW,
                ),
                onBackClick = {},
                onItemClick = rememberNewsItemOpener { openedImage = it },
                onKindSelected = {},
                onRefresh = {},
                onErrorShown = {},
            )
        }
    }

    private fun clickRow(id: String) {
        composeTestRule.onNodeWithTag(NEWS_LIST_TEST_TAG).performScrollToNode(hasTestTag(newsRowTestTag(id)))
        composeTestRule.onNodeWithTag(newsRowTestTag(id)).performClick()
    }

    @Test
    fun `a story opens in a Custom Tab`() {
        showList()

        clickRow(TestNews.story().id)

        val intent = nextStartedActivity()!!
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals(TestNews.story().url, intent.dataString)
        assertTrue(intent.hasExtra(CustomTabsIntent.EXTRA_SESSION))
        assertEquals(CustomTabsIntent.COLOR_SCHEME_DARK, intent.getIntExtra(CustomTabsIntent.EXTRA_COLOR_SCHEME, -1))
        assertNull(openedImage)
    }

    @Test
    fun `the live stream is handed to YouTube, not a Custom Tab`() {
        showList()

        composeTestRule.onNodeWithTag(NEWS_LIVE_BANNER_TEST_TAG).performClick()

        val intent = nextStartedActivity()!!
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals("https://www.youtube.com/watch?v=ROboUHfPUrU", intent.dataString)
        assertTrue(!intent.hasExtra(CustomTabsIntent.EXTRA_SESSION))
    }

    @Test
    fun `an Image of the Day opens its own screen`() {
        showList()

        clickRow(TestNews.imageOfTheDay().id)

        assertEquals(TestNews.imageOfTheDay().id, openedImage)
        assertNull(nextStartedActivity())
    }

    @Test
    fun `the Today card opens items the same way and links to the whole list`() {
        var seeAll = 0
        composeTestRule.setContent {
            LatestFromNasaCardContent(
                uiState = LatestFromNasaUiState(
                    live = TestNews.video(),
                    latest = listOf(TestNews.science()),
                    now = TestNews.NOW,
                ),
                onItemClick = rememberNewsItemOpener { openedImage = it },
                onSeeAllClick = { seeAll++ },
            )
        }

        composeTestRule.onNodeWithText("Latest from NASA").assertExists()
        composeTestRule.onNodeWithTag(newsRowTestTag(TestNews.science().id)).performClick()
        assertTrue(nextStartedActivity()!!.hasExtra(CustomTabsIntent.EXTRA_SESSION))
        composeTestRule.onNodeWithTag(LATEST_FROM_NASA_SEE_ALL_TEST_TAG).performClick()
        assertEquals(1, seeAll)
    }

    @Test
    fun `an empty cache shows no Today card`() {
        composeTestRule.setContent {
            LatestFromNasaCardContent(uiState = LatestFromNasaUiState(), onItemClick = {}, onSeeAllClick = {})
        }

        composeTestRule.onNodeWithText("Latest from NASA").assertDoesNotExist()
    }

    @Test
    fun `a video's own screen offers to watch it on YouTube`() {
        composeTestRule.setContent {
            NewsItemContent(
                uiState = NewsItemUiState.Content(
                    item = TestNews.video(isLive = false),
                    isFavorite = false,
                    now = TestNews.NOW,
                ),
                onBackClick = {},
                onOpenPage = rememberPageOpener(),
                onToggleFavorite = {},
                onErrorDismissed = {},
            )
        }

        composeTestRule.onNodeWithText("Watch on YouTube").assertExists()
        composeTestRule.onNodeWithTag(NEWS_ITEM_OPEN_TEST_TAG).performScrollTo().performClick()
        assertEquals("https://www.youtube.com/watch?v=ROboUHfPUrU", nextStartedActivity()!!.dataString)
    }
}
