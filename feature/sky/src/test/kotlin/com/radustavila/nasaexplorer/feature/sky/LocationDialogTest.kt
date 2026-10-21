package com.radustavila.nasaexplorer.feature.sky

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.radustavila.nasaexplorer.core.model.City
import com.radustavila.nasaexplorer.core.model.GeoCoordinate
import com.radustavila.nasaexplorer.core.testing.data.TestSky
import com.radustavila.nasaexplorer.feature.sky.component.LocationDialog
import com.radustavila.nasaexplorer.feature.sky.component.LocationPrompt
import com.radustavila.nasaexplorer.feature.sky.component.SKY_CHOOSE_CITY_BUTTON_TEST_TAG
import com.radustavila.nasaexplorer.feature.sky.component.SKY_CITY_RESULTS_TEST_TAG
import com.radustavila.nasaexplorer.feature.sky.component.SKY_CITY_SEARCH_FIELD_TEST_TAG
import com.radustavila.nasaexplorer.feature.sky.component.SKY_LATITUDE_FIELD_TEST_TAG
import com.radustavila.nasaexplorer.feature.sky.component.SKY_LONGITUDE_FIELD_TEST_TAG
import com.radustavila.nasaexplorer.feature.sky.component.SKY_USE_COORDINATES_TEST_TAG
import com.radustavila.nasaexplorer.feature.sky.component.SKY_USE_LOCATION_BUTTON_TEST_TAG
import com.radustavila.nasaexplorer.feature.sky.component.parseCoordinate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
class LocationDialogTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val queries = mutableListOf<String>()
    private val picked = mutableListOf<City>()
    private val entered = mutableListOf<GeoCoordinate>()

    private fun setDialog(search: CitySearchUiState = CitySearchUiState(), fontScale: Float = 1f) {
        composeTestRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                LocationDialog(
                    citySearch = search,
                    onQueryChange = { queries += it },
                    onCitySelected = { picked += it },
                    onCoordinatesEntered = { entered += it },
                    onDismiss = {},
                )
            }
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

        composeTestRule.onNodeWithText("Bucharest").assertHeightIsAtLeast(48.dp).performClick()

        assertEquals(listOf(city), picked)
    }

    @Test
    fun `at twice the font size every result can still be scrolled to and picked`() {
        val cities = listOf("Bucharest", "Budapest", "Buenos Aires", "Bujumbura").map { name ->
            City(name, "Country of $name", TestSky.BUCHAREST)
        }
        setDialog(CitySearchUiState(query = "", results = cities), fontScale = 2f)

        composeTestRule.onNodeWithText("Bujumbura").performScrollTo()
        // Scrolled into the results area itself, not drawn past its bottom edge over the fields.
        val area = composeTestRule.onNodeWithTag(SKY_CITY_RESULTS_TEST_TAG).getUnclippedBoundsInRoot()
        val row = composeTestRule.onNodeWithText("Bujumbura").getUnclippedBoundsInRoot()
        assertTrue(row.bottom <= area.bottom)
        composeTestRule.onNodeWithText("Bujumbura").performClick()
        composeTestRule.onNodeWithTag(SKY_USE_COORDINATES_TEST_TAG).performScrollTo().assertIsDisplayed()

        assertEquals(listOf(cities.last()), picked)
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp")
    // Real text metrics: the legacy shadows measure glyphs far narrower than a device does.
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `at twice the font size the prompt's buttons wrap instead of squeezing`() {
        composeTestRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
                LocationPrompt(isLocating = false, onUseMyLocation = {}, onChooseCity = {})
            }
        }

        val useLocation = composeTestRule.onNodeWithTag(SKY_USE_LOCATION_BUTTON_TEST_TAG).getUnclippedBoundsInRoot()
        val chooseCity = composeTestRule.onNodeWithTag(SKY_CHOOSE_CITY_BUTTON_TEST_TAG).getUnclippedBoundsInRoot()
        // Its own line, at its natural width, rather than a sliver beside the first button.
        assertTrue(chooseCity.top >= useLocation.bottom)
        assertTrue(chooseCity.right - chooseCity.left > 100.dp)
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
