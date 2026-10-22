package com.radustavila.nasaexplorer.feature.news

import androidx.compose.ui.test.junit4.createComposeRule
import com.radustavila.nasaexplorer.core.model.DataFreshness
import com.radustavila.nasaexplorer.core.screenshottesting.ScreenshotDevices
import com.radustavila.nasaexplorer.core.screenshottesting.captureComponent
import com.radustavila.nasaexplorer.core.screenshottesting.captureScreen
import com.radustavila.nasaexplorer.core.testing.data.TestNews
import com.radustavila.nasaexplorer.feature.news.item.NewsItemContent
import com.radustavila.nasaexplorer.feature.news.item.NewsItemUiState
import com.radustavila.nasaexplorer.feature.news.list.NewsContent
import com.radustavila.nasaexplorer.feature.news.list.NewsUiState
import com.radustavila.nasaexplorer.feature.news.today.LatestFromNasaCardContent
import com.radustavila.nasaexplorer.feature.news.today.LatestFromNasaUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Golden images of the News screens; `./gradlew recordRoborazziDebug` rewrites them. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = ScreenshotDevices.COMPACT)
class NewsScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun newsList() {
        composeTestRule.captureScreen("NewsList") {
            NewsContent(
                uiState = NewsUiState(
                    items = TestNews.ALL,
                    liveItem = TestNews.video(),
                    hasNews = true,
                    isLoaded = true,
                    freshness = DataFreshness(lastUpdatedAt = TestNews.NOW.minusSeconds(1_200)),
                    now = TestNews.NOW,
                ),
                onBackClick = {},
                onItemClick = {},
                onKindSelected = {},
                onRefresh = {},
                onErrorShown = {},
            )
        }
    }

    @Test
    fun imageOfTheDay() {
        composeTestRule.captureScreen("NewsImageOfTheDay") {
            NewsItemContent(
                uiState = NewsItemUiState.Content(TestNews.imageOfTheDay(), isFavorite = true, now = TestNews.NOW),
                onBackClick = {},
                onOpenPage = {},
                onToggleFavorite = {},
                onErrorDismissed = {},
            )
        }
    }

    @Test
    fun latestFromNasaCard() {
        composeTestRule.captureComponent("LatestFromNasaCard") {
            LatestFromNasaCardContent(
                uiState = LatestFromNasaUiState(
                    live = TestNews.video(),
                    latest = listOf(TestNews.story(), TestNews.science(), TestNews.imageOfTheDay()),
                    now = TestNews.NOW,
                ),
                onItemClick = {},
                onSeeAllClick = {},
            )
        }
    }
}
