package com.radustavila.nasaexplorer.feature.favorites

import androidx.compose.ui.test.junit4.createComposeRule
import com.radustavila.nasaexplorer.core.model.FavoriteItem
import com.radustavila.nasaexplorer.core.model.FavoriteSource
import com.radustavila.nasaexplorer.core.screenshottesting.ScreenshotDevices
import com.radustavila.nasaexplorer.core.screenshottesting.captureScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Golden images of the Favourites grid; `./gradlew recordRoborazziDebug` rewrites them. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FavoritesScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    @Config(qualifiers = ScreenshotDevices.COMPACT)
    fun favoritesCompact() = captureFavorites("FavoritesCompact")

    @Test
    @Config(qualifiers = ScreenshotDevices.EXPANDED)
    fun favoritesExpanded() = captureFavorites("FavoritesExpanded")

    private fun captureFavorites(name: String) {
        val items = listOf(
            FavoriteItem(FavoriteSource.APOD, "2026-09-10", "Spiral Galaxy NGC 1300", image("ngc1300.jpg")),
            FavoriteItem(
                FavoriteSource.EPIC,
                "natural/2026-10-16/20261016002713",
                "Earth, 16 Oct 2026, 00:27 UTC",
                image("epic.png"),
            ),
            FavoriteItem(FavoriteSource.APOD, "2026-09-07", "The Pillars of Creation", image("pillars.jpg")),
            FavoriteItem(FavoriteSource.APOD, "2026-08-30", "A Video Without a Still Frame", null),
        )
        composeTestRule.captureScreen(name) {
            FavoritesContent(
                uiState = FavoritesUiState(items = items, isLoading = false),
                onFavoriteClick = {},
                onRemoveFavorite = {},
                onErrorDismissed = {},
            )
        }
    }

    private fun image(file: String) = "https://example.test/$file"
}
