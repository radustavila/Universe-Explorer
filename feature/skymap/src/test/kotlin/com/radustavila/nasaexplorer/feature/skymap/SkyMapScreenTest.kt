package com.radustavila.nasaexplorer.feature.skymap

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.radustavila.nasaexplorer.core.designsystem.theme.NasaTheme
import com.radustavila.nasaexplorer.core.model.SolarSystemBody
import com.radustavila.nasaexplorer.core.skymap.SkyLens
import com.radustavila.nasaexplorer.core.skymap.SkyView
import com.radustavila.nasaexplorer.feature.skymap.ar.ArLens
import com.radustavila.nasaexplorer.feature.skymap.ar.ArProblem
import com.radustavila.nasaexplorer.feature.skymap.ar.SKY_MAP_ALIGN_BUTTON_TEST_TAG
import com.radustavila.nasaexplorer.feature.skymap.ar.SkyArState
import com.radustavila.nasaexplorer.core.model.ObservingConditions
import com.radustavila.nasaexplorer.core.testing.data.TestDeepSky
import com.radustavila.nasaexplorer.feature.skymap.deepsky.MARK_SEEN_DIALOG_TEST_TAG
import com.radustavila.nasaexplorer.feature.skymap.deepsky.MARK_SEEN_NOTE_TEST_TAG
import com.radustavila.nasaexplorer.feature.skymap.deepsky.formatDay
import java.time.LocalDate
import java.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
    private val cometClicks = mutableListOf<String>()

    private var arClicks = 0
    private val layerChanges = mutableListOf<Boolean>()
    private val marks = mutableListOf<List<Any?>>()
    private val removals = mutableListOf<String>()
    private var logClicks = 0

    private fun show(
        uiState: SkyMapUiState,
        camera: SkyCameraState = SkyCameraState(CameraMode.TOUCH),
        hasCompass: Boolean = false,
        ar: SkyArState = SkyArState(),
    ) {
        val deepSky = DeepSkyActions(
            onLayerChange = { layerChanges += it },
            onMarkSeen = { id, day, conditions, note -> marks += listOf(id, day, conditions, note) },
            onRemoveSeen = { removals += it },
            onObservingLogClick = { logClicks++ },
        )
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
                    onCometClick = { cometClicks += it },
                    locationSection = { Text("Pick a place") },
                    ar = ar,
                    onArClick = { arClicks++ },
                    arCamera = { modifier ->
                        Box(modifier.testTag(CAMERA_STAND_IN))
                        LaunchedEffect(Unit) { ar.onCameraReady(ArLens(fieldOfViewDegrees = 40.0)) }
                    },
                    deepSky = deepSky,
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
    fun `a selected comet shows its predicted brightness and opens its details`() {
        show(
            SkyMapFixture.state(
                selected = SkyTarget.Comet("2026 B1"),
                selectedConstellation = "Cet",
                bodies = SkyMapFixture.bodiesWithComet,
            ),
        )

        composeTestRule.onNodeWithText("C/2026 B1 (Lemmon)").assertIsDisplayed()
        composeTestRule.onNodeWithText("Comet in Cetus").assertIsDisplayed()
        composeTestRule.onNodeWithText("22° above the horizon, towards the south-east").assertIsDisplayed()
        composeTestRule.onNodeWithText("Predicted magnitude 8.4").assertIsDisplayed()

        composeTestRule.onNodeWithTag(SKY_MAP_COMET_DETAILS_TEST_TAG).assertTextEquals("Comet details").performClick()
        assertEquals(listOf("2026 B1"), cometClicks)
    }

    @Test
    fun `a selected galaxy shows its kind, size, best month and a way to log it`() {
        val m31 = SkyMapFixture.deepSkyId(messier = 31)
        show(SkyMapFixture.state(selected = SkyTarget.DeepSky(m31), selectedConstellation = "And"))

        composeTestRule.onNodeWithText("M31 · Andromeda Galaxy").assertIsDisplayed()
        composeTestRule.onNodeWithText("Galaxy in Andromeda").assertIsDisplayed()
        composeTestRule.onNodeWithText("Magnitude 3.4 · 178′ × 70′").assertIsDisplayed()
        composeTestRule.onNodeWithText("NGC 224").assertIsDisplayed()
        composeTestRule.onNodeWithText("Best in October · Naked eye").assertIsDisplayed()

        composeTestRule.onNodeWithTag(SKY_MAP_MARK_SEEN_TEST_TAG).assertTextEquals("Mark as seen").performClick()
        composeTestRule.onNodeWithTag(MARK_SEEN_DIALOG_TEST_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithText("Good").performClick()
        composeTestRule.onNodeWithTag(MARK_SEEN_NOTE_TEST_TAG).performTextInput("Dark site, 10x50")
        composeTestRule.onNodeWithText("Save").performClick()

        assertEquals(listOf(listOf(m31, LocalDate.now(), ObservingConditions.GOOD, "Dark site, 10x50")), marks)
    }

    @Test
    fun `a logged object shows the day it was seen and can be taken out of the log`() {
        val m42 = SkyMapFixture.deepSkyId(messier = 42)
        val entry = TestDeepSky.entry(objectId = m42, observedOn = LocalDate.of(2026, 1, 12))
        show(
            SkyMapFixture.state(selected = SkyTarget.DeepSky(m42), selectedConstellation = "Ori")
                .copy(observingLog = mapOf(m42 to entry)),
        )

        composeTestRule.onNodeWithText("Seen on ${entry.observedOn.formatDay()}").assertIsDisplayed()
        composeTestRule.onNodeWithTag(SKY_MAP_MARK_SEEN_TEST_TAG).assertTextEquals("Edit").performClick()
        composeTestRule.onNodeWithText("Remove from my log").performClick()

        assertEquals(listOf(m42), removals)
    }

    @Test
    fun `Messier objects are found by number, with or without a space`() {
        show(SkyMapFixture.state())

        composeTestRule.onNodeWithContentDescription("Search the sky").performClick()
        composeTestRule.onNodeWithTag(SKY_MAP_SEARCH_FIELD_TEST_TAG).performTextInput("m 13")
        composeTestRule.onNodeWithText("M13 · Hercules Globular Cluster").performClick()

        assertEquals(listOf<SkyTarget?>(SkyTarget.DeepSky(SkyMapFixture.deepSkyId(messier = 13))), selections)
    }

    @Test
    fun `the layers menu switches the deep-sky layer and opens the observing log`() {
        show(SkyMapFixture.state())

        composeTestRule.onNodeWithTag(SKY_MAP_LAYERS_TEST_TAG).performClick()
        composeTestRule.onNodeWithTag(SKY_MAP_DEEP_SKY_LAYER_TEST_TAG).performClick()
        composeTestRule.onNodeWithText("Observing log").performClick()

        assertEquals(listOf(false), layerChanges)
        assertEquals(1, logClicks)
    }

    @Test
    fun `comets are found by search`() {
        show(SkyMapFixture.state(bodies = SkyMapFixture.bodiesWithComet))

        composeTestRule.onNodeWithContentDescription("Search the sky").performClick()
        composeTestRule.onNodeWithTag(SKY_MAP_SEARCH_FIELD_TEST_TAG).performTextInput("lemmon")
        composeTestRule.onNodeWithText("C/2026 B1 (Lemmon)").performClick()

        assertEquals(listOf<SkyTarget?>(SkyTarget.Comet("2026 B1")), selections)
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

    private fun arCompass() = SkyCameraState(CameraMode.COMPASS).apply { sensorView = SkyView.looking(150.0, 30.0) }

    private fun arOn() = SkyArState().apply { turnOn(nowMillis = 0) }

    @Test
    fun `the AR switch asks for the camera`() {
        show(SkyMapFixture.state())

        composeTestRule.onNodeWithContentDescription("Show the camera behind the map").performClick()
        assertEquals(1, arClicks)
    }

    @Test
    fun `in AR the camera fills the back, the map takes its lens and the time bar gives way to Align`() {
        val camera = arCompass()
        show(SkyMapFixture.state(isNightMode = true), camera = camera, hasCompass = true, ar = arOn())

        composeTestRule.onNodeWithTag(CAMERA_STAND_IN).assertIsDisplayed()
        composeTestRule.onNodeWithText("Stars drawn over the camera").assertIsDisplayed()
        composeTestRule.onNodeWithTag(SKY_MAP_TIME_SLIDER_TEST_TAG).assertDoesNotExist()
        // Red night mode stays off over the bright picture.
        composeTestRule.onNodeWithContentDescription("Red night mode").assertIsNotEnabled()
        assertEquals(40.0, camera.fieldOfViewDegrees, 1e-9)
        assertEquals(SkyLens.RECTILINEAR, camera.lens)

        composeTestRule.onNodeWithTag(SKY_MAP_ALIGN_BUTTON_TEST_TAG).performClick()
        composeTestRule.onNodeWithText("Hold the phone still and drag the drawn Moon or a planet onto the real one.")
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("Done").performClick()
        composeTestRule.onNodeWithText("Stars drawn over the camera").assertIsDisplayed()
    }

    @Test
    fun `moving the map by touch leaves AR`() {
        val ar = arOn()
        val camera = arCompass()
        show(SkyMapFixture.state(), camera = camera, hasCompass = true, ar = ar)

        composeTestRule.onNodeWithContentDescription("Move the map by touch").performClick()
        composeTestRule.waitForIdle()

        assertFalse(ar.isOn)
        assertEquals(SkyLens.STEREOGRAPHIC, camera.lens)
        composeTestRule.onNodeWithTag(CAMERA_STAND_IN).assertDoesNotExist()
        composeTestRule.onNodeWithText("Now").assertIsDisplayed()
    }

    @Test
    fun `a refused camera says where to allow it and the map stays plain`() {
        val ar = SkyArState().apply { turnOff(ArProblem.CAMERA_PERMISSION_DENIED) }
        show(SkyMapFixture.state(), ar = ar)

        composeTestRule.onNodeWithText(
            "AR needs the camera. Its picture stays on the screen and is never saved. " +
                "You can allow it in the app's settings.",
        ).assertIsDisplayed()
        composeTestRule.onNodeWithText("Open settings").assertIsDisplayed()
        composeTestRule.onNodeWithTag(CAMERA_STAND_IN).assertDoesNotExist()

        composeTestRule.onNodeWithContentDescription("Dismiss").performClick()
        composeTestRule.onNodeWithText("Open settings").assertDoesNotExist()
    }

    @Test
    fun `missing hardware and a phone left still each explain themselves`() {
        val ar = SkyArState().apply { turnOff(ArProblem.NO_ROTATION_SENSOR) }
        show(SkyMapFixture.state(), ar = ar)
        composeTestRule.onNodeWithText(
            "AR needs a rotation sensor to follow the phone, and this device has none. The map still works by touch.",
        ).assertIsDisplayed()

        ar.turnOff(ArProblem.NO_BACK_CAMERA)
        composeTestRule.onNodeWithText("AR needs a camera on the back of the device, and this one has none.")
            .assertIsDisplayed()

        ar.turnOff(ArProblem.TURNED_OFF_WHILE_STILL)
        composeTestRule.onNodeWithText("Turn it back on").performClick()
        assertEquals(1, arClicks)
        assertNull(ar.problem)
    }

    private companion object {
        const val CAMERA_STAND_IN = "camera-stand-in"
    }
}
