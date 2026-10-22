package com.radustavila.nasaexplorer.feature.skymap

import androidx.compose.material3.Text
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.radustavila.nasaexplorer.core.designsystem.theme.NasaTheme
import com.radustavila.nasaexplorer.core.model.SolarSystemBody
import java.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SkyMapScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val selections = mutableListOf<SkyTarget?>()
    private val offsets = mutableListOf<Duration>()
    private val nightModes = mutableListOf<Boolean>()

    private fun show(
        uiState: SkyMapUiState,
        camera: SkyCameraState = SkyCameraState(CameraMode.TOUCH),
        hasCompass: Boolean = false,
    ) {
        composeTestRule.setContent {
            NasaTheme {
                SkyMapContent(
                    uiState = uiState,
                    camera = camera,
                    hasCompass = hasCompass,
                    isCompassUnsure = false,
                    onBackClick = {},
                    onSelect = { selections += it },
                    onTimeOffsetChange = { offsets += it },
                    onBackToNow = { offsets += Duration.ZERO },
                    onNightModeChange = { nightModes += it },
                    locationSection = { Text("Pick a place") },
                )
            }
        }
    }

    @Test
    fun `without a location the map asks for one`() {
        show(SkyMapUiState(isLocationLoaded = true))

        composeTestRule.onNodeWithText("Choose where you are to see the sky above you.").assertIsDisplayed()
        composeTestRule.onNodeWithText("Pick a place").assertIsDisplayed()
    }

    @Test
    fun `the map says where it looks`() {
        val camera = SkyCameraState(CameraMode.TOUCH, azimuthDegrees = 135.0, altitudeDegrees = 30.0)
        show(SkyMapFixture.state(), camera = camera)

        composeTestRule.onNodeWithText("Looking south-east, 30° up").assertIsDisplayed()
        composeTestRule.onNodeWithTag(SKY_MAP_CANVAS_TEST_TAG)
            .assertIsDisplayed()
            .assertContentDescriptionEquals("Sky map. Looking south-east, 30° up")
    }

    @Test
    fun `searching selects what was found`() {
        show(SkyMapFixture.state())

        composeTestRule.onNodeWithContentDescription("Search the sky").performClick()
        composeTestRule.onNodeWithTag(SKY_MAP_SEARCH_FIELD_TEST_TAG).performTextInput("vega")
        composeTestRule.onNodeWithText("Vega").performClick()

        assertEquals(listOf<SkyTarget?>(SkyTarget.StarTarget(SkyMapFixture.starIndex("Vega"))), selections)
    }

    @Test
    fun `a selection shows what and where it is`() {
        show(SkyMapFixture.state(selected = SkyTarget.Body(SolarSystemBody.MOON), selectedConstellation = "Sgr"))

        composeTestRule.onNodeWithText("Moon").assertIsDisplayed()
        composeTestRule.onNodeWithText("Earth's moon in Sagittarius").assertIsDisplayed()
        composeTestRule.onNodeWithText("22° above the horizon, towards the south").assertIsDisplayed()
        composeTestRule.onNodeWithText("Magnitude -10.5 · 62% lit").assertIsDisplayed()

        composeTestRule.onNodeWithContentDescription("Close details").performClick()
        assertEquals(listOf<SkyTarget?>(null), selections)
    }

    @Test
    fun `hour steps move the time and night mode can be switched on`() {
        show(SkyMapFixture.state())

        composeTestRule.onNodeWithText("Now").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("One hour later").performClick()
        composeTestRule.onNodeWithContentDescription("Red night mode").performClick()

        assertEquals(listOf(Duration.ofHours(1)), offsets)
        assertEquals(listOf(true), nightModes)
    }

    @Test
    fun `away from now the time is shown with a way back`() {
        show(SkyMapFixture.state().copy(timeOffset = Duration.ofHours(-2)))

        composeTestRule.onNodeWithText("Back to now").performClick()
        assertTrue(offsets.single().isZero)
    }

    @Test
    fun `the compass switch appears only with the sensor`() {
        val camera = SkyCameraState(CameraMode.COMPASS)
        show(SkyMapFixture.state(), camera = camera, hasCompass = true)

        composeTestRule.onNodeWithContentDescription("Move the map by touch").performClick()
        assertEquals(CameraMode.TOUCH, camera.mode)
    }
}
