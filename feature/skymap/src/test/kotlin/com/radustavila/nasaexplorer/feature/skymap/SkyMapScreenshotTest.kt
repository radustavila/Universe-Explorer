package com.radustavila.nasaexplorer.feature.skymap

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import com.radustavila.nasaexplorer.core.model.SolarSystemBody
import com.radustavila.nasaexplorer.core.screenshottesting.ScreenshotDevices
import com.radustavila.nasaexplorer.core.skymap.SkyView
import com.radustavila.nasaexplorer.feature.skymap.ar.ArLens
import com.radustavila.nasaexplorer.feature.skymap.ar.SkyArState
import com.radustavila.nasaexplorer.core.screenshottesting.captureScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Golden images of the sky map over the bundled catalogue; `./gradlew recordRoborazziDebug` rewrites them. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SkyMapScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun capture(
        name: String,
        uiState: SkyMapUiState,
        camera: SkyCameraState = SkyCameraState(CameraMode.TOUCH, azimuthDegrees = 150.0, altitudeDegrees = 30.0),
        ar: SkyArState = SkyArState(),
    ) {
        composeTestRule.captureScreen(name) {
            SkyMapContent(
                uiState = uiState,
                camera = camera,
                hasCompass = true,
                isCompassUnsure = false,
                onBackClick = {},
                onSelect = {},
                onTimeOffsetChange = {},
                onBackToNow = {},
                onNightModeChange = {},
                onCometClick = {},
                locationSection = { Text("Location") },
                ar = ar,
                arCamera = { modifier ->
                    // A dusk sky over a dark skyline stands in for the camera's picture.
                    val picture = Brush.verticalGradient(
                        0f to DUSK_TOP,
                        SKYLINE_TOP to DUSK_LOW,
                        SKYLINE_TOP to SKYLINE,
                    )
                    Box(modifier.background(picture))
                    LaunchedEffect(Unit) { ar.onCameraReady(ArLens(fieldOfViewDegrees = 34.0)) }
                },
            )
        }
    }

    @Test
    @Config(qualifiers = ScreenshotDevices.COMPACT)
    fun skyMapCompact() {
        capture("SkyMapCompact", SkyMapFixture.state())
    }

    @Test
    @Config(qualifiers = ScreenshotDevices.EXPANDED)
    fun skyMapExpanded() {
        capture("SkyMapExpanded", SkyMapFixture.state())
    }

    @Test
    @Config(qualifiers = ScreenshotDevices.COMPACT)
    fun skyMapNightModeWithSelection() {
        capture(
            "SkyMapNightSelection",
            SkyMapFixture.state(
                selected = SkyTarget.Body(SolarSystemBody.MOON),
                selectedConstellation = "Sgr",
                isNightMode = true,
            ),
        )
    }

    @Test
    @Config(qualifiers = ScreenshotDevices.COMPACT)
    fun skyMapCometSelected() {
        capture(
            "SkyMapComet",
            SkyMapFixture.state(
                selected = SkyTarget.Comet(SkyMapFixture.comet.designation),
                selectedConstellation = "Cet",
                bodies = SkyMapFixture.bodiesWithComet,
            ),
        )
    }

    @Test
    @Config(qualifiers = ScreenshotDevices.COMPACT)
    fun skyMapPointingArrow() {
        // The phone points east while Vega is low in the north-west: an arrow leads to it.
        val compass = SkyCameraState(CameraMode.COMPASS).apply { sensorView = SkyView.looking(90.0, 30.0) }
        val vega = SkyTarget.StarTarget(SkyMapFixture.starIndex("Vega"))
        capture("SkyMapArrow", SkyMapFixture.state(selected = vega), compass)
    }

    @Test
    @Config(qualifiers = ScreenshotDevices.COMPACT)
    fun skyMapOverTheCamera() {
        // AR: the map drawn as the lens sees it, over the camera, with the Moon selected.
        val compass = SkyCameraState(CameraMode.COMPASS).apply { sensorView = SkyView.looking(195.0, 18.0) }
        capture(
            "SkyMapAr",
            SkyMapFixture.state(
                selected = SkyTarget.Body(SolarSystemBody.MOON),
                selectedConstellation = "Sgr",
                isNightMode = true,
            ),
            compass,
            SkyArState().apply { turnOn(nowMillis = 0) },
        )
    }

    private companion object {
        val DUSK_TOP = Color(0xFF14233F)
        val DUSK_LOW = Color(0xFF5B5470)
        val SKYLINE = Color(0xFF121212)
        const val SKYLINE_TOP = 0.62f
    }
}
