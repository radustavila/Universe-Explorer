package com.radustavila.nasaexplorer.feature.feed

import androidx.compose.ui.test.junit4.createComposeRule
import com.radustavila.nasaexplorer.core.model.DataFreshness
import com.radustavila.nasaexplorer.core.screenshottesting.ScreenshotDevices
import com.radustavila.nasaexplorer.core.screenshottesting.captureScreen
import com.radustavila.nasaexplorer.core.testing.data.TestApods
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Golden images of Today; `./gradlew recordRoborazziDebug` rewrites them. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FeedScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    @Config(qualifiers = ScreenshotDevices.COMPACT)
    fun feedCompact() = captureFeed("FeedCompact")

    @Test
    @Config(qualifiers = ScreenshotDevices.EXPANDED)
    fun feedExpanded() = captureFeed("FeedExpanded")

    private fun captureFeed(name: String) {
        val uiState = FeedUiState(
            items = listOf(
                TestApods.image(isFavorite = true),
                TestApods.video(),
                TestApods.streamableVideo(),
                TestApods.image(date = LocalDate.of(2026, 9, 7), title = "The Pillars of Creation"),
                TestApods.image(date = LocalDate.of(2026, 9, 6), title = "Comet over Lake Constance"),
                TestApods.image(date = LocalDate.of(2026, 9, 5), title = "Saturn at Opposition"),
            ),
            latestSelectableDate = LocalDate.of(2026, 9, 10),
            freshness = DataFreshness(lastUpdatedAt = Instant.now().minus(Duration.ofMinutes(5).plusSeconds(10))),
        )
        composeTestRule.captureScreen(name) {
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
    }
}
