package com.radustavila.nasaexplorer.feature.exoplanets

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.radustavila.nasaexplorer.core.designsystem.component.FRESHNESS_LABEL_TEST_TAG
import com.radustavila.nasaexplorer.core.designsystem.component.OFFLINE_BANNER_TEST_TAG
import com.radustavila.nasaexplorer.core.designsystem.component.RETRY_BUTTON_TEST_TAG
import com.radustavila.nasaexplorer.core.model.ExoplanetList
import com.radustavila.nasaexplorer.core.testing.data.TestExoplanets
import com.radustavila.nasaexplorer.feature.exoplanets.details.ExoplanetDetailsContent
import com.radustavila.nasaexplorer.feature.exoplanets.details.ExoplanetDetailsUiState
import java.time.Duration
import java.time.Instant
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en-rUS-w400dp-h800dp")
class ExoplanetsScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Before
    fun setUp() {
        Locale.setDefault(Locale.US)
    }

    private val planet = TestExoplanets.exoplanet()

    @Test
    fun `lists planets with their size and distance`() {
        var clicked: String? = null
        composeTestRule.setContent {
            ExoplanetsContent(
                uiState = ExoplanetsUiState(
                    planets = listOf(planet),
                    isLoading = false,
                    neverSynced = false,
                    confirmedPlanets = 6451,
                    newestSinceYear = 2025,
                ),
                onBackClick = {},
                onExoplanetClick = { clicked = it },
                onListSelected = {},
                onRefresh = {},
            )
        }

        composeTestRule.onNodeWithText("Discovered since 2025, newest first").assertIsDisplayed()
        composeTestRule.onNodeWithText("6,451 confirmed planets beyond the Solar System").assertIsDisplayed()
        composeTestRule.onNodeWithText("1.15× Earth").assertIsDisplayed()
        composeTestRule.onNodeWithText("142 ly").assertIsDisplayed()
        composeTestRule.onNodeWithText("TOI-789 · Transit · Sep 2026").assertIsDisplayed()

        composeTestRule.onNodeWithText("TOI-789 c").performClick()
        assertEquals("TOI-789 c", clicked)
    }

    @Test
    fun `tabs select a list`() {
        var selected: ExoplanetList? = null
        composeTestRule.setContent {
            ExoplanetsContent(
                uiState = ExoplanetsUiState(planets = listOf(planet), isLoading = false, neverSynced = false),
                onBackClick = {},
                onExoplanetClick = {},
                onListSelected = { selected = it },
                onRefresh = {},
            )
        }

        composeTestRule.onNodeWithText("Earth-sized").performClick()

        assertEquals(ExoplanetList.EARTH_SIZED, selected)
    }

    @Test
    fun `offline before the first sync explains how to get the catalogue`() {
        var retried = false
        composeTestRule.setContent {
            ExoplanetsContent(
                uiState = ExoplanetsUiState(isLoading = false, isOffline = true, neverSynced = true),
                onBackClick = {},
                onExoplanetClick = {},
                onListSelected = {},
                onRefresh = { retried = true },
            )
        }

        composeTestRule.onNodeWithTag(OFFLINE_BANNER_TEST_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithText("No planets saved yet").assertIsDisplayed()
        composeTestRule.onNodeWithTag(RETRY_BUTTON_TEST_TAG).performClick()
        assertEquals(true, retried)
    }

    @Test
    fun `online before the first sync shows the download in progress`() {
        composeTestRule.setContent {
            ExoplanetsContent(
                uiState = ExoplanetsUiState(isLoading = false, neverSynced = true),
                onBackClick = {},
                onExoplanetClick = {},
                onListSelected = {},
                onRefresh = {},
            )
        }

        composeTestRule.onNodeWithText("Downloading the catalogue…").assertIsDisplayed()
    }

    @Test
    fun `a failed refresh over a cached list keeps the list and says so`() {
        composeTestRule.setContent {
            ExoplanetsContent(
                uiState = ExoplanetsUiState(
                    planets = listOf(planet),
                    isLoading = false,
                    neverSynced = false,
                    lastRefreshFailed = true,
                    lastUpdatedAt = Instant.now().minus(Duration.ofDays(2)),
                ),
                onBackClick = {},
                onExoplanetClick = {},
                onListSelected = {},
                onRefresh = {},
            )
        }

        composeTestRule.onNodeWithTag(FRESHNESS_LABEL_TEST_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithText("The last refresh failed.", substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("TOI-789 c").assertIsDisplayed()
    }

    @Test
    fun `details compare size and distance and explain the method`() {
        composeTestRule.setContent {
            ExoplanetDetailsContent(
                uiState = ExoplanetDetailsUiState.Content(planet = TestExoplanets.proximaB),
                onBackClick = {},
                onToggleFavorite = {},
                onErrorDismissed = {},
            )
        }

        composeTestRule.onNodeWithTag(SIZE_COMPARISON_TEST_TAG).assertIsDisplayed()
        composeTestRule
            .onNodeWithContentDescription("Proxima Cen b compared with Earth and Jupiter: 1.03 times Earth's radius")
            .assertExists()
        composeTestRule.onNodeWithText("1.03× Earth's radius · 0.092× Jupiter's").assertIsDisplayed()
        composeTestRule.onNodeWithText("4.2 light-years (1.3 parsecs)").assertIsDisplayed()
        composeTestRule
            .onNodeWithText(
                "One of the Sun's nearest neighbours: Proxima Centauri, the closest star, is 4.2 light-years away",
            )
            .assertExists()
        // Fahrenheit: Robolectric's default region is the United States.
        composeTestRule.onNodeWithText("234 K (-38 °F)").assertExists()
        composeTestRule.onNodeWithText("Radial velocity").assertExists()
        composeTestRule
            .onNodeWithText("The planet's pull made its star wobble, shifting the colour of its light back and forth.")
            .assertExists()
    }

    @Test
    fun `details of an unmeasured planet say what is unknown`() {
        composeTestRule.setContent {
            ExoplanetDetailsContent(
                uiState = ExoplanetDetailsUiState.Content(
                    planet = planet.copy(radiusEarth = null, distanceParsecs = null),
                ),
                onBackClick = {},
                onToggleFavorite = {},
                onErrorDismissed = {},
            )
        }

        composeTestRule.onNodeWithText("Its size has not been measured.").assertIsDisplayed()
        composeTestRule.onNodeWithText("Its distance has not been measured.").assertIsDisplayed()
    }

    @Test
    fun `distant planets are compared with the nearest star`() {
        composeTestRule.setContent {
            ExoplanetDetailsContent(
                uiState = ExoplanetDetailsUiState.Content(planet = TestExoplanets.hotJupiter),
                onBackClick = {},
                onToggleFavorite = {},
                onErrorDismissed = {},
            )
        }

        composeTestRule.onNodeWithText("647 light-years (198 parsecs)").assertExists()
        composeTestRule.onNodeWithText("152× as far as Proxima Centauri, the nearest star").assertExists()
    }
}
