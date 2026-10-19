package com.radustavila.nasaexplorer.feature.details

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import com.radustavila.nasaexplorer.core.model.ApodLink
import com.radustavila.nasaexplorer.core.testing.data.TestApods
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w400dp-h800dp")
class DetailsScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `the hero announces NASA's alt text`() {
        render(TestApods.image().copy(altText = "Barred spiral galaxy, face on"))

        composeTestRule.onNodeWithContentDescription("Barred spiral galaxy, face on").assertExists()
    }

    @Test
    fun `without alt text the hero falls back to the title`() {
        render(TestApods.image(title = "Spiral Galaxy NGC 1300"))

        composeTestRule.onNodeWithContentDescription("Spiral Galaxy NGC 1300").assertExists()
    }

    @Test
    fun `the explanation keeps its text when it carries links`() {
        val explanation = "See the Pleiades and Wikipedia."
        render(
            TestApods.image().copy(
                explanation = explanation,
                explanationLinks = listOf(
                    ApodLink(start = 4, end = 16, url = "https://apod.nasa.gov/apod/ap950620.html"),
                    ApodLink(start = 21, end = 30, url = "https://en.wikipedia.org/"),
                ),
            ),
        )

        composeTestRule.onNodeWithText(explanation).assertExists()
    }

    private fun render(apod: com.radustavila.nasaexplorer.core.model.Apod) {
        composeTestRule.setContent {
            DetailsContent(
                uiState = DetailsUiState.Content(apod = apod),
                onBackClick = {},
                onToggleFavorite = {},
                onRetry = {},
                onErrorDismissed = {},
            )
        }
    }
}
