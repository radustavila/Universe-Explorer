package com.radustavila.nasaexplorer.feature.sky

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.radustavila.nasaexplorer.core.model.City
import com.radustavila.nasaexplorer.core.model.GeoCoordinate
import com.radustavila.nasaexplorer.core.testing.data.TestSky
import com.radustavila.nasaexplorer.feature.sky.component.LocationDialog
import com.radustavila.nasaexplorer.feature.sky.component.SKY_CITY_SEARCH_FIELD_TEST_TAG
import com.radustavila.nasaexplorer.feature.sky.component.SKY_LATITUDE_FIELD_TEST_TAG
import com.radustavila.nasaexplorer.feature.sky.component.SKY_LONGITUDE_FIELD_TEST_TAG
import com.radustavila.nasaexplorer.feature.sky.component.SKY_USE_COORDINATES_TEST_TAG
import com.radustavila.nasaexplorer.feature.sky.component.parseCoordinate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LocationDialogTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val queries = mutableListOf<String>()
    private val picked = mutableListOf<City>()
    private val entered = mutableListOf<GeoCoordinate>()

    private fun setDialog(search: CitySearchUiState = CitySearchUiState()) {
        composeTestRule.setContent {
            LocationDialog(
                citySearch = search,
                onQueryChange = { queries += it },
                onCitySelected = { picked += it },
                onCoordinatesEntered = { entered += it },
                onDismiss = {},
            )
        }
    }

    @Test
    fun `the city field keeps every keystroke even before the search answers`() {
        // The ViewModel's state lags behind the field: it still holds an empty query.
        setDialog()

        composeTestRule.onNodeWithTag(SKY_CITY_SEARCH_FIELD_TEST_TAG).performTextInput("Bucha")

        composeTestRule.onNodeWithText("Bucha").assertExists()
        assertEquals("Bucha", queries.last())
    }

    @Test
    fun `a search result is picked with a tap`() {
        val city = City("Bucharest", "Romania", TestSky.BUCHAREST)
        setDialog(CitySearchUiState(query = "", results = listOf(city)))

        composeTestRule.onNodeWithText("Bucharest").performClick()

        assertEquals(listOf(city), picked)
    }

    @Test
    fun `coordinates are validated before they are used`() {
        setDialog()

        composeTestRule.onNodeWithTag(SKY_LATITUDE_FIELD_TEST_TAG).performTextInput("95")
        composeTestRule.onNodeWithTag(SKY_LONGITUDE_FIELD_TEST_TAG).performTextInput("26,1")
        composeTestRule.onNodeWithTag(SKY_USE_COORDINATES_TEST_TAG).performScrollTo().performClick()

        assertEquals(emptyList<GeoCoordinate>(), entered)
        composeTestRule.onNodeWithText("Enter a latitude", substring = true).assertExists()
    }

    @Test
    fun `coordinates accept a decimal comma and a typographic minus`() {
        assertEquals(GeoCoordinate(-33.87, 151.21), parseCoordinate("−33,87", " 151.21 "))
        assertNull(parseCoordinate("abc", "1"))
        assertNull(parseCoordinate("10", "181"))
    }
}
