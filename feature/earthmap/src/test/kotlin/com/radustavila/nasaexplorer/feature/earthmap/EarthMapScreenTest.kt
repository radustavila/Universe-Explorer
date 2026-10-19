package com.radustavila.nasaexplorer.feature.earthmap

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.radustavila.nasaexplorer.core.designsystem.component.OFFLINE_BANNER_TEST_TAG
import com.radustavila.nasaexplorer.core.designsystem.util.formatMedium
import com.radustavila.nasaexplorer.core.map.TileLoadStatus
import com.radustavila.nasaexplorer.core.map.gibs.GibsImagery
import com.radustavila.nasaexplorer.core.map.gibs.GibsLayer
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w400dp-h800dp")
class EarthMapScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val yesterday = LocalDate.of(2026, 10, 18)
    private val baseState = EarthMapUiState(
        mode = EarthMapMode.TrueColor,
        date = yesterday,
        minDate = LocalDate.of(2015, 1, 1),
        maxDate = yesterday,
    )

    private var shownImagery: GibsImagery? = null

    private fun setContent(
        uiState: EarthMapUiState = baseState,
        tileStatus: TileLoadStatus = TileLoadStatus.Loaded,
        onModeSelected: (EarthMapMode) -> Unit = {},
        onPreviousDay: () -> Unit = {},
    ) {
        composeTestRule.setContent {
            EarthMapScreen(
                uiState = uiState,
                tileStatus = tileStatus,
                onBackClick = {},
                onModeSelected = onModeSelected,
                onDateSelected = {},
                onPreviousDay = onPreviousDay,
                onNextDay = {},
                map = { imagery, modifier ->
                    shownImagery = imagery
                    Box(modifier.testTag(FAKE_MAP_TAG))
                },
            )
        }
    }

    @Test
    fun `shows the map with yesterday's true colour and the date controls`() {
        setContent()

        composeTestRule.onNodeWithTag(FAKE_MAP_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithText("True colour").assertIsSelected()
        composeTestRule.onNodeWithTag(EARTH_MAP_DATE_LABEL_TEST_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithText(yesterday.formatMedium()).assertIsDisplayed()
        composeTestRule.onNodeWithTag(EARTH_MAP_DATE_SLIDER_TEST_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Next day").assertIsNotEnabled()
        assertEquals(GibsImagery(GibsLayer.ViirsSnppTrueColor, yesterday), shownImagery)
    }

    @Test
    fun `switching layers updates the map and hides the date controls`() {
        var uiState by mutableStateOf(baseState)
        composeTestRule.setContent {
            EarthMapScreen(
                uiState = uiState,
                tileStatus = TileLoadStatus.Loaded,
                onBackClick = {},
                onModeSelected = { uiState = uiState.copy(mode = it) },
                onDateSelected = {},
                onPreviousDay = {},
                onNextDay = {},
                map = { imagery, modifier ->
                    shownImagery = imagery
                    Box(modifier.testTag(FAKE_MAP_TAG))
                },
            )
        }

        composeTestRule.onNodeWithText("Night lights").performClick()

        composeTestRule.onNodeWithText("Night lights").assertIsSelected()
        composeTestRule.onNodeWithText("City lights at night, 2016 composite").assertIsDisplayed()
        composeTestRule.onNodeWithTag(EARTH_MAP_DATE_SLIDER_TEST_TAG).assertDoesNotExist()
        assertEquals(GibsLayer.ViirsBlackMarble, shownImagery?.layer)
    }

    @Test
    fun `previous day button asks for the day before`() {
        var clicks = 0
        setContent(onPreviousDay = { clicks++ })

        composeTestRule.onNodeWithContentDescription("Previous day").performClick()

        assertEquals(1, clicks)
    }

    @Test
    fun `offline shows the offline banner`() {
        setContent(uiState = baseState.copy(isOffline = true))

        composeTestRule.onNodeWithTag(OFFLINE_BANNER_TEST_TAG).assertIsDisplayed()
    }

    @Test
    fun `failed tile loads show a banner`() {
        setContent(tileStatus = TileLoadStatus.NetworkError)

        composeTestRule.onNodeWithTag(EARTH_MAP_TILE_BANNER_TEST_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithTag(OFFLINE_BANNER_TEST_TAG).assertDoesNotExist()
    }

    @Test
    fun `a day without imagery says so`() {
        setContent(tileStatus = TileLoadStatus.ImageryUnavailable)

        composeTestRule.onNodeWithText("No imagery for this day yet. Try an earlier date.").assertIsDisplayed()
    }

    @Test
    fun `no banner while tiles load fine`() {
        setContent()

        composeTestRule.onNodeWithTag(EARTH_MAP_TILE_BANNER_TEST_TAG).assertDoesNotExist()
        composeTestRule.onNodeWithTag(OFFLINE_BANNER_TEST_TAG).assertDoesNotExist()
    }

    private companion object {
        const val FAKE_MAP_TAG = "fake-map"
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w900dp-h400dp")
class EarthMapScreenLandscapeTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `landscape puts the controls beside the map`() {
        val day = LocalDate.of(2026, 10, 18)
        composeTestRule.setContent {
            EarthMapScreen(
                uiState = EarthMapUiState(EarthMapMode.TrueColor, day, LocalDate.of(2015, 1, 1), day),
                tileStatus = TileLoadStatus.Loaded,
                onBackClick = {},
                onModeSelected = {},
                onDateSelected = {},
                onPreviousDay = {},
                onNextDay = {},
                map = { _, modifier -> Box(modifier.testTag("fake-map")) },
            )
        }

        val map = composeTestRule.onNodeWithTag("fake-map").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val slider = composeTestRule.onNodeWithTag(EARTH_MAP_DATE_SLIDER_TEST_TAG).assertIsDisplayed()
            .fetchSemanticsNode().boundsInRoot
        assertTrue(slider.left >= map.right)
        composeTestRule.onNodeWithText("Night lights").assertIsDisplayed()
    }
}
