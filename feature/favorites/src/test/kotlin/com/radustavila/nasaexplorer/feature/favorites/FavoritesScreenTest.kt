package com.radustavila.nasaexplorer.feature.favorites

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.radustavila.nasaexplorer.core.testing.data.TestApods
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
                    items = listOf(TestApods.image(isFavorite = true)),
                    isLoading = false,
                ),
                onApodClick = {},
                onToggleFavorite = {},
                onErrorDismissed = {},
            )
        }

        composeTestRule.onNodeWithTag(FAVORITES_LIST_TEST_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithText("Spiral Galaxy NGC 1300").assertIsDisplayed()
    }

    @Test
    fun `explains what favorites are when there are none`() {
        composeTestRule.setContent {
            FavoritesContent(
                uiState = FavoritesUiState(items = emptyList(), isLoading = false),
                onApodClick = {},
                onToggleFavorite = {},
                onErrorDismissed = {},
            )
        }

        composeTestRule.onNodeWithText("No favorites yet").assertIsDisplayed()
    }
}
