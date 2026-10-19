package com.radustavila.nasaexplorer.feature.mars

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.radustavila.nasaexplorer.core.model.MarsRover
import com.radustavila.nasaexplorer.core.testing.data.TestMarsPhotos
import com.radustavila.nasaexplorer.feature.mars.photo.MARS_PHOTO_ZOOM_TEST_TAG
import com.radustavila.nasaexplorer.feature.mars.photo.MarsPhotoContent
import com.radustavila.nasaexplorer.feature.mars.photo.MarsPhotoUiState
import com.radustavila.nasaexplorer.feature.mars.photos.MarsPhotosContent
import com.radustavila.nasaexplorer.feature.mars.photos.MarsPhotosUiState
import com.radustavila.nasaexplorer.feature.mars.photos.groupBySolAndCamera
import com.radustavila.nasaexplorer.feature.mars.photos.marsRoverTabTestTag
import com.radustavila.nasaexplorer.feature.mars.rail.MarsRailContent
import com.radustavila.nasaexplorer.feature.mars.rail.MarsRailUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w400dp-h800dp")
class MarsScreensTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val curiosity = TestMarsPhotos.curiosity()
    private val perseverance = TestMarsPhotos.perseverance()

    @Test
    fun `the rail shows each photo's rover and sol and opens it`() {
        var opened: String? = null
        var seeAll = 0
        composeTestRule.setContent {
            MarsRailContent(
                uiState = MarsRailUiState(photos = listOf(curiosity, perseverance), isLoaded = true),
                onPhotoClick = { opened = it },
                onSeeAllClick = { seeAll++ },
            )
        }

        composeTestRule.onNodeWithText("Curiosity · Sol 5048").assertIsDisplayed()
        composeTestRule.onNodeWithTag(marsPhotoTestTag(curiosity.id)).performClick()
        composeTestRule.onNodeWithText("See all").performClick()

        assertEquals(curiosity.id, opened)
        assertEquals(1, seeAll)
    }

    @Test
    fun `an empty rail says the photos are downloading`() {
        composeTestRule.setContent {
            MarsRailContent(
                uiState = MarsRailUiState(isSyncing = true, isLoaded = true),
                onPhotoClick = {},
                onSeeAllClick = {},
            )
        }

        composeTestRule.onNodeWithText("Downloading the latest rover photos…").assertIsDisplayed()
    }

    @Test
    fun `the photos screen lists sols and cameras and switches rover`() {
        var selected: MarsRover? = null
        composeTestRule.setContent {
            MarsPhotosContent(
                uiState = MarsPhotosUiState(sols = listOf(curiosity).groupBySolAndCamera(), isLoaded = true),
                onBackClick = {},
                onPhotoClick = {},
                onRoverSelected = { selected = it },
                onRefresh = {},
            )
        }

        composeTestRule.onNodeWithText("Sol 5048").assertIsDisplayed()
        composeTestRule.onNodeWithText("Mars Hand Lens Imager (MAHLI)").assertIsDisplayed()
        composeTestRule.onNodeWithTag(marsRoverTabTestTag(MarsRover.PERSEVERANCE)).performClick()

        assertEquals(MarsRover.PERSEVERANCE, selected)
    }

    @Test
    fun `a rover without photos offline says so`() {
        composeTestRule.setContent {
            MarsPhotosContent(
                uiState = MarsPhotosUiState(rover = MarsRover.PERSEVERANCE, isOffline = true, isLoaded = true),
                onBackClick = {},
                onPhotoClick = {},
                onRoverSelected = {},
                onRefresh = {},
            )
        }

        composeTestRule.onNodeWithText("Rover photos not downloaded yet").assertIsDisplayed()
    }

    @Test
    fun `a photo opens full size`() {
        composeTestRule.setContent {
            MarsPhotoContent(
                uiState = MarsPhotoUiState.Content(photo = perseverance, isFavorite = false),
                onBackClick = {},
                onToggleFavorite = {},
                onErrorDismissed = {},
            )
        }

        composeTestRule.onNodeWithText("Perseverance · Sol 1650").assertIsDisplayed()
        composeTestRule.onNodeWithTag(MARS_PHOTO_ZOOM_TEST_TAG).performClick()
        composeTestRule.onNodeWithContentDescription("Close full size view").assertExists()
    }
}
