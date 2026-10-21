package com.radustavila.nasaexplorer.feature.earth

import androidx.compose.ui.test.junit4.createComposeRule
import com.radustavila.nasaexplorer.core.model.DataFreshness
import com.radustavila.nasaexplorer.core.screenshottesting.ScreenshotDevices
import com.radustavila.nasaexplorer.core.screenshottesting.captureScreen
import com.radustavila.nasaexplorer.core.testing.data.TestEpicImages
import java.time.Instant
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Golden images of Earth today; `./gradlew recordRoborazziDebug` rewrites them. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class EarthTodayScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    @Config(qualifiers = ScreenshotDevices.COMPACT)
    fun earthTodayCompact() = captureEarthToday("EarthTodayCompact")

    @Test
    @Config(qualifiers = ScreenshotDevices.EXPANDED)
    fun earthTodayExpanded() = captureEarthToday("EarthTodayExpanded")

    private fun captureEarthToday(name: String) {
        val images = TestEpicImages.day(size = 3)
        val uiState = EarthTodayUiState(
            images = images,
            availableDates = setOf(TestEpicImages.LATEST_DATE),
            favoriteIds = setOf(images.first().ref.favoriteId),
            freshness = DataFreshness(lastUpdatedAt = Instant.parse("2026-01-15T09:30:00Z")),
        )
        composeTestRule.captureScreen(name) {
            EarthTodayContent(
                uiState = uiState,
                onBackClick = null,
                onCollectionSelected = {},
                onDateSelected = {},
                onRefresh = {},
                onRetry = {},
                onErrorDismissed = {},
                onToggleFavorite = {},
                // A running rotation would never let the UI go idle for the capture.
                autoPlay = false,
            )
        }
    }
}
