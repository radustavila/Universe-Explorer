package com.radustavila.nasaexplorer.feature.spaceweather

import androidx.compose.foundation.layout.Box
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import com.radustavila.nasaexplorer.core.designsystem.component.OFFLINE_BANNER_TEST_TAG
import com.radustavila.nasaexplorer.core.map.overlay.MapOverlay
import com.radustavila.nasaexplorer.core.model.AuroraAlertSettings
import com.radustavila.nasaexplorer.core.testing.data.TestSpaceWeather
import com.radustavila.nasaexplorer.feature.spaceweather.components.AURORA_ALERT_LOCATION_TEST_TAG
import com.radustavila.nasaexplorer.feature.spaceweather.components.AURORA_ALERT_SWITCH_TEST_TAG
import com.radustavila.nasaexplorer.feature.spaceweather.components.AURORA_LOCATION_LATITUDE_TEST_TAG
import com.radustavila.nasaexplorer.feature.spaceweather.components.AURORA_LOCATION_LONGITUDE_TEST_TAG
import com.radustavila.nasaexplorer.feature.spaceweather.components.AuroraLocationFields
import com.radustavila.nasaexplorer.feature.spaceweather.components.parsePlace
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.radustavila.nasaexplorer.feature.spaceweather.components.AuroraOval
import com.radustavila.nasaexplorer.feature.spaceweather.components.CME_TEST_TAG
import com.radustavila.nasaexplorer.feature.spaceweather.components.KP_FORECAST_TEST_TAG
import com.radustavila.nasaexplorer.feature.spaceweather.components.SOLAR_FLARE_TEST_TAG
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w400dp-h900dp")
class SpaceWeatherScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val forecast = TestSpaceWeather.forecast(3.33, 4.67, 6.0, 2.0)
    private val aurora = TestSpaceWeather.auroraForecast()
    private val content = SpaceWeatherUiState(
        currentKp = 5.67,
        currentKpTime = TestSpaceWeather.NOW,
        forecast = forecast,
        aurora = AuroraMapUi(AuroraOval.overlays(aurora), aurora.observedAt, aurora.forecastFor),
        flare = TestSpaceWeather.flare(),
        cme = TestSpaceWeather.cme(),
    )

    private var mapOverlays: List<MapOverlay> = emptyList()

    private fun setContent(uiState: SpaceWeatherUiState) {
        composeTestRule.setContent {
            SpaceWeatherContent(
                uiState = uiState,
                onBackClick = {},
                onRefresh = {},
                onHemisphereSelected = {},
                onAlertsEnabledChange = {},
                onAlertLocationChange = { _, _, _ -> },
                map = { overlays, modifier ->
                    mapOverlays = overlays
                    Box(modifier)
                },
            )
        }
    }

    @Test
    fun `the gauge states the Kp and its storm level`() {
        setContent(content)

        // The gauge speaks as one node; its drawn labels are not separate semantics.
        composeTestRule.onNodeWithContentDescription("Kp index 5.7: Moderate storm").assertIsDisplayed()
        composeTestRule.onNodeWithTag(KP_FORECAST_TEST_TAG).assertIsDisplayed()
    }

    @Test
    fun `the aurora bands reach the shared map, with the user's place when known`() {
        setContent(content.copy(alerts = AuroraAlertSettings(latitude = 59.3, longitude = 18.1)))

        composeTestRule.onNodeWithTag(SPACE_WEATHER_LIST_TEST_TAG).performScrollToNode(hasTestTag(AURORA_MAP_TEST_TAG))
        composeTestRule.onNodeWithTag(AURORA_MAP_TEST_TAG).assertIsDisplayed()
        assertEquals(listOf("aurora-5", "aurora-20", "aurora-50", "place"), mapOverlays.map { it.id })
        assertEquals(1, mapOverlays.last().shapes.size)
    }

    @Test
    fun `section titles are headings TalkBack can jump to`() {
        setContent(content)

        composeTestRule.onNodeWithText("Next 3 days").assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
    }

    @Test
    fun `the latest flare and CME are listed`() {
        setContent(content)

        composeTestRule.onNodeWithTag(SPACE_WEATHER_LIST_TEST_TAG).performScrollToNode(hasTestTag(CME_TEST_TAG))
        composeTestRule.onNodeWithTag(SOLAR_FLARE_TEST_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithText("Class M1.6").assertIsDisplayed()
        composeTestRule.onNodeWithText("590 km/s").assertIsDisplayed()
        composeTestRule.onNodeWithText("Not expected to reach Earth.").assertIsDisplayed()
    }

    /**
     * The dialog window itself never lets Compose idle under Robolectric, so the device test opens
     * it; here its fields are driven directly.
     */
    @Test
    fun `the location fields show the Kp a typed place needs`() {
        composeTestRule.setContent {
            var latitude by remember { mutableStateOf("") }
            var longitude by remember { mutableStateOf("") }
            AuroraLocationFields(latitude, longitude, { latitude = it }, { longitude = it })
        }

        composeTestRule.onNodeWithTag(AURORA_LOCATION_LATITUDE_TEST_TAG).performTextInput("59,3")
        composeTestRule.onNodeWithTag(AURORA_LOCATION_LONGITUDE_TEST_TAG).performTextInput("18.1")

        composeTestRule.onNodeWithText("From here the aurora shows from Kp 5 (G1).").assertIsDisplayed()
    }

    @Test
    fun `typed places are parsed, decimal commas included`() {
        assertEquals(59.3 to 18.1, parsePlace("59,3", " 18.1 "))
        assertEquals(null, parsePlace("95", "18"))
        assertEquals(null, parsePlace("59", "east"))
    }

    @Test
    fun `without a place the card offers to set one`() {
        setContent(content)

        composeTestRule.onNodeWithTag(SPACE_WEATHER_LIST_TEST_TAG)
            .performScrollToNode(hasTestTag(AURORA_ALERT_LOCATION_TEST_TAG))
        composeTestRule.onNodeWithTag(AURORA_ALERT_SWITCH_TEST_TAG).assertIsOff()
        composeTestRule.onNodeWithText("Set where you watch from to get alerts.").assertIsDisplayed()
        composeTestRule.onNodeWithText("Set your place").assertIsDisplayed()
    }

    @Test
    fun `a saved place states the Kp it needs`() {
        setContent(content.copy(alerts = AuroraAlertSettings(enabled = true, latitude = 59.3, longitude = 18.1)))

        composeTestRule.onNodeWithTag(SPACE_WEATHER_LIST_TEST_TAG)
            .performScrollToNode(hasTestTag(AURORA_ALERT_LOCATION_TEST_TAG))
        composeTestRule.onNodeWithText("59.3° N, 18.1° E: alerts from Kp 5 (G1)").assertIsDisplayed()
    }

    @Test
    fun `offline with nothing cached says so`() {
        setContent(SpaceWeatherUiState(isOffline = true))

        composeTestRule.onNodeWithText("No space weather yet").assertIsDisplayed()
    }

    @Test
    fun `offline with a cache shows the cache under a banner`() {
        setContent(content.copy(isOffline = true))

        composeTestRule.onNodeWithTag(OFFLINE_BANNER_TEST_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Kp index 5.7: Moderate storm").assertIsDisplayed()
    }
}
