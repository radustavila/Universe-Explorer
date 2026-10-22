package com.radustavila.nasaexplorer.feature.spaceweather.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.radustavila.nasaexplorer.core.designsystem.component.MEDIA_PLAYER_TEST_TAG
import com.radustavila.nasaexplorer.core.model.SunChannel
import com.radustavila.nasaexplorer.core.testing.fake.FakeSunImageRepository
import com.radustavila.nasaexplorer.feature.spaceweather.SunUiState
import com.radustavila.nasaexplorer.feature.spaceweather.SunVideo
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w400dp-h900dp")
class SunCardTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val credit = "Courtesy of NASA/SDO and the AIA, EVE, and HMI science teams"
    private val picture = FakeSunImageRepository.image(SunChannel.CORONA, Instant.now().minusSeconds(600))
    private val sun = SunUiState(channel = SunChannel.CORONA, image = picture, videoUrl = "https://sdo.test/video.mp4")

    @Test
    fun `the card names the channel, explains it, dates the picture and credits SDO`() {
        var picked: SunChannel? = null
        var opened = false
        composeTestRule.setContent {
            SunCard(sun = sun, isOffline = false, onChannelSelected = { picked = it }, onOpen = { opened = true })
        }

        composeTestRule.onNodeWithText("171 Å corona").assertIsDisplayed()
        composeTestRule.onNodeWithText("The quiet outer atmosphere", substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithText(credit).assertIsDisplayed()
        composeTestRule.onNodeWithText("Updated", substring = true).assertIsDisplayed()

        composeTestRule.onNodeWithText("131 Å flares").performClick()
        composeTestRule.onNodeWithTag(SUN_IMAGE_TEST_TAG).performClick()

        assertEquals(SunChannel.FLARES, picked)
        assertTrue(opened)
    }

    @Test
    fun `offline, the cached picture keeps its age`() {
        composeTestRule.setContent {
            SunCard(sun = sun, isOffline = true, onChannelSelected = {}, onOpen = {})
        }

        composeTestRule.onNodeWithText("Offline", substring = true).assertIsDisplayed()
    }

    @Test
    fun `without any picture offline the card says so and does not open`() {
        var opened = false
        composeTestRule.setContent {
            SunCard(sun = SunUiState(), isOffline = true, onChannelSelected = {}, onOpen = { opened = true })
        }

        composeTestRule.onNodeWithText("It downloads once you are online", substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithTag(SUN_IMAGE_TEST_TAG).performClick()
        assertFalse(opened)
    }

    @Test
    fun `on mobile data the video is not loaded until the user taps play`() {
        var confirmed = false
        composeTestRule.setContent {
            SunViewerContent(
                sun = sun.copy(video = SunVideo.AskFirst),
                isOffline = false,
                onChannelSelected = {},
                onVideoSelected = {},
                onVideoConfirmed = { confirmed = true },
                onVideoClosed = {},
                onClose = {},
            )
        }

        composeTestRule.onNodeWithTag(SUN_VIDEO_ASK_TEST_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithTag(MEDIA_PLAYER_TEST_TAG).assertDoesNotExist()
        composeTestRule.onNodeWithText("Play video").performClick()
        assertTrue(confirmed)
    }

    @Test
    fun `the full-screen view switches between the picture and the 48 hours`() {
        var videoSelected = false
        composeTestRule.setContent {
            SunViewerContent(
                sun = sun,
                isOffline = false,
                onChannelSelected = {},
                onVideoSelected = { videoSelected = true },
                onVideoConfirmed = {},
                onVideoClosed = {},
                onClose = {},
            )
        }

        composeTestRule.onNodeWithTag(MEDIA_PLAYER_TEST_TAG).assertDoesNotExist()
        composeTestRule.onNodeWithText("Last 48 hours").performClick()
        assertTrue(videoSelected)
        composeTestRule.onNodeWithText(credit).assertIsDisplayed()
    }
}
