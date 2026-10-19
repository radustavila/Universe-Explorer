package com.radustavila.nasaexplorer.feature.universe.details

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.radustavila.nasaexplorer.core.testing.data.TestNasaMedia
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w400dp-h800dp")
class NasaMediaDetailsScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun setContent(uiState: NasaMediaDetailsUiState, onToggleFavorite: () -> Unit = {}) {
        composeTestRule.setContent {
            NasaMediaDetailsContent(
                uiState = uiState,
                onBackClick = {},
                onToggleFavorite = onToggleFavorite,
                onRetry = {},
                onRetryPlayback = {},
                onErrorDismissed = {},
            )
        }
    }

    @Test
    fun `an image shows its text, credit and download`() {
        val image = TestNasaMedia.image()
        setContent(NasaMediaDetailsUiState.Content(media = image, isFavorite = false))

        composeTestRule.onNodeWithTag(MEDIA_DETAILS_CONTENT_TEST_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithText(image.title).assertIsDisplayed()
        composeTestRule.onNodeWithText("Credit: NASA/STScI").assertIsDisplayed()
        composeTestRule.onNodeWithTag(MEDIA_DETAILS_ZOOM_TEST_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithText("Download original").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `the heart saves the item`() {
        var toggled = false
        setContent(
            NasaMediaDetailsUiState.Content(media = TestNasaMedia.image(), isFavorite = false),
            onToggleFavorite = { toggled = true },
        )

        composeTestRule.onNodeWithContentDescription("Add to favorites").performClick()

        assertTrue(toggled)
    }

    @Test
    fun `a video offline explains that playing needs a connection`() {
        setContent(
            NasaMediaDetailsUiState.Content(
                media = TestNasaMedia.video(),
                isFavorite = true,
                isOffline = true,
                playback = PlaybackState.NeedsConnection,
            ),
        )

        composeTestRule.onNodeWithText("Playing needs a connection.").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Remove from favorites").assertIsDisplayed()
    }

    @Test
    fun `a long description starts collapsed`() {
        val long = TestNasaMedia.audio().copy(description = "word ".repeat(400))
        setContent(NasaMediaDetailsUiState.Content(media = long, isFavorite = false))

        composeTestRule.onNodeWithText("Read more").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `an item not kept offline says so`() {
        setContent(NasaMediaDetailsUiState.Unavailable(isOffline = true))

        composeTestRule.onNodeWithText("Not available offline").assertIsDisplayed()
    }
}
