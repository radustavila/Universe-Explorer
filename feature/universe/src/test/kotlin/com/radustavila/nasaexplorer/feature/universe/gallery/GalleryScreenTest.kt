package com.radustavila.nasaexplorer.feature.universe.gallery

import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.radustavila.nasaexplorer.core.designsystem.error.UiError
import com.radustavila.nasaexplorer.core.model.GalleryRail
import com.radustavila.nasaexplorer.core.testing.data.TestNasaMedia
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w400dp-h800dp")
class GalleryScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val webb = TestNasaMedia.images(3, prefix = "Webb")

    private fun setContent(
        uiState: GalleryUiState,
        header: (@androidx.compose.runtime.Composable () -> Unit)? = null,
        onMediaClick: (String) -> Unit = {},
        onQueryChange: (String) -> Unit = {},
    ) {
        composeTestRule.setContent {
            GalleryContent(
                uiState = uiState,
                header = header,
                onMediaClick = onMediaClick,
                onQueryChange = onQueryChange,
                onClearQuery = {},
                onToggleMediaType = {},
                onYearRangeChange = { _, _ -> },
                onRefresh = {},
                onRefreshErrorShown = {},
                onRetrySearch = {},
                onLoadMore = {},
            )
        }
    }

    @Test
    fun `renders the rails under the tab's other entries`() {
        var opened: String? = null
        setContent(
            uiState = GalleryUiState(rails = listOf(RailUi(GalleryRail.WEBB_GALAXIES, webb))),
            header = { Text("Exoplanets") },
            onMediaClick = { opened = it },
        )

        composeTestRule.onNodeWithTag(GALLERY_RAILS_TEST_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithText("Exoplanets").assertIsDisplayed()
        composeTestRule.onNodeWithText("Webb galaxies").assertIsDisplayed()
        composeTestRule.onNodeWithText("Webb #0").assertIsDisplayed().performClick()
        assertEquals("Webb-0", opened)
    }

    @Test
    fun `cached rails offline carry the offline banner`() {
        setContent(GalleryUiState(rails = listOf(RailUi(GalleryRail.NEBULAE, webb)), isOffline = true))

        composeTestRule.onNodeWithText("Nebulae").assertIsDisplayed()
        composeTestRule.onNodeWithText("Webb #0").assertIsDisplayed()
    }

    @Test
    fun `no rails offline explains that one connection is needed`() {
        setContent(GalleryUiState(isOffline = true, refreshError = UiError(0)))

        composeTestRule.onNodeWithText("Gallery not downloaded yet").assertIsDisplayed()
    }

    @Test
    fun `offline search says it needs a connection`() {
        setContent(GalleryUiState(query = "nebula", search = SearchUiState.NeedsConnection, isOffline = true))

        composeTestRule.onNodeWithText("Search needs a connection").assertIsDisplayed()
        composeTestRule.onNodeWithText("Images").assertIsDisplayed()
    }

    @Test
    fun `search results show their hit count`() {
        setContent(
            GalleryUiState(
                query = "webb",
                search = SearchUiState.Results(items = webb, totalHits = 814, canLoadMore = true),
            ),
        )

        composeTestRule.onNodeWithTag(GALLERY_RESULTS_TEST_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithText("814 results").assertIsDisplayed()
        composeTestRule.onNodeWithText("Webb #1").assertIsDisplayed()
    }

    @Test
    fun `typing reports the query`() {
        var typed = ""
        setContent(GalleryUiState(rails = listOf(RailUi(GalleryRail.WEBB, webb))), onQueryChange = { typed = it })

        composeTestRule.onNodeWithTag(GALLERY_SEARCH_FIELD_TEST_TAG).performTextInput("crab")

        assertEquals("crab", typed)
    }
}
