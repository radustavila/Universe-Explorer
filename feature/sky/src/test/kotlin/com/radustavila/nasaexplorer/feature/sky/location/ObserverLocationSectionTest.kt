package com.radustavila.nasaexplorer.feature.sky.location

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.radustavila.nasaexplorer.core.testing.data.TestSky
import com.radustavila.nasaexplorer.feature.sky.component.SKY_CHOOSE_CITY_BUTTON_TEST_TAG
import com.radustavila.nasaexplorer.feature.sky.component.SKY_CITY_SEARCH_FIELD_TEST_TAG
import com.radustavila.nasaexplorer.feature.sky.component.SKY_LOCATION_HEADER_TEST_TAG
import com.radustavila.nasaexplorer.feature.sky.component.SKY_LOCATION_PROMPT_TEST_TAG
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ObserverLocationSectionTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private var cleared = 0

    private fun setSection(state: ObserverLocationUiState) {
        composeTestRule.setContent {
            ObserverLocationContent(
                uiState = state,
                onLocateDevice = {},
                onLocationPermissionDenied = {},
                onQueryChange = {},
                onCitySelected = {},
                onCoordinatesEntered = {},
                onClear = { cleared++ },
            )
        }
    }

    @Test
    fun `a saved place is shown with a way to forget it`() {
        setSection(ObserverLocationUiState(isLoaded = true, location = TestSky.location()))

        composeTestRule.onNodeWithTag(SKY_LOCATION_HEADER_TEST_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithTag(OBSERVER_LOCATION_CLEAR_TEST_TAG).performClick()

        assertEquals(1, cleared)
    }

    @Test
    fun `without a place it offers the device fix and Sky tonight's city picker`() {
        setSection(ObserverLocationUiState(isLoaded = true, location = null))

        composeTestRule.onNodeWithTag(SKY_LOCATION_PROMPT_TEST_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithTag(SKY_CHOOSE_CITY_BUTTON_TEST_TAG).performClick()
        composeTestRule.onNodeWithTag(SKY_CITY_SEARCH_FIELD_TEST_TAG).assertIsDisplayed()
    }

    @Test
    fun `a refused permission is explained`() {
        setSection(ObserverLocationUiState(isLoaded = true, message = ObserverLocationMessage.PERMISSION_DENIED))

        composeTestRule.onNodeWithText("Without location access", substring = true).assertIsDisplayed()
    }
}
