package com.radustavila.nasaexplorer.feature.favorites

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.radustavila.nasaexplorer.core.model.FavoriteItem
import com.radustavila.nasaexplorer.core.model.FavoriteSource
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w400dp-h800dp")
class FavoritesScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `renders saved favorites`() {
        composeTestRule.setContent {
            FavoritesContent(
                uiState = FavoritesUiState(
                    items = listOf(
                        FavoriteItem(FavoriteSource.APOD, "2026-09-10", "Spiral Galaxy NGC 1300", null),
                        FavoriteItem(FavoriteSource.EPIC, "epic_1b", "Earth, 18 Oct 2026", null),
                    ),
                    isLoading = false,
                ),
                onFavoriteClick = {},
                onRemoveFavorite = {},
                onErrorDismissed = {},
            )
        }

        composeTestRule.onNodeWithTag(FAVORITES_LIST_TEST_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithText("Spiral Galaxy NGC 1300").assertIsDisplayed()
        composeTestRule.onNodeWithText("Astronomy Picture of the Day").assertIsDisplayed()
        composeTestRule.onNodeWithText("Earth from DSCOVR").assertExists()
    }

    @Test
    fun `explains what favorites are when there are none`() {
        composeTestRule.setContent {
            FavoritesContent(
                uiState = FavoritesUiState(items = emptyList(), isLoading = false),
                onFavoriteClick = {},
                onRemoveFavorite = {},
                onErrorDismissed = {},
            )
        }

        composeTestRule.onNodeWithText("No favorites yet").assertIsDisplayed()
    }
}
