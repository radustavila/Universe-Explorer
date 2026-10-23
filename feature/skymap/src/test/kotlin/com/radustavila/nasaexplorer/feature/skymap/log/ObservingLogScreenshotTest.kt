package com.radustavila.nasaexplorer.feature.skymap.log

import androidx.compose.ui.test.junit4.createComposeRule
import com.radustavila.nasaexplorer.core.model.DeepSkyTonight
import com.radustavila.nasaexplorer.core.model.DeepSkyVisibility
import com.radustavila.nasaexplorer.core.screenshottesting.ScreenshotDevices
import com.radustavila.nasaexplorer.core.screenshottesting.captureComponent
import com.radustavila.nasaexplorer.core.screenshottesting.captureScreen
import com.radustavila.nasaexplorer.core.testing.data.TestDeepSky
import java.time.Instant
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Golden images of the observing log (F23); `./gradlew recordRoborazziDebug` rewrites them. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ObservingLogScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val tonight = DeepSkyTonight(
        darkStart = Instant.parse("2026-10-22T17:30:00Z"),
        darkEnd = Instant.parse("2026-10-23T03:30:00Z"),
        visible = listOf(
            DeepSkyVisibility(TestDeepSky.M31.id, Instant.parse("2026-10-22T21:40:00Z"), 78.0, 20.0),
            DeepSkyVisibility(TestDeepSky.M13.id, Instant.parse("2026-10-22T17:30:00Z"), 41.0, 285.0),
        ).associateBy { it.objectId },
    )

    private val state = ObservingLogUiState(
        objects = TestDeepSky.objects(),
        constellationNames = mapOf("And" to "Andromeda", "Ori" to "Orion", "Her" to "Hercules"),
        log = mapOf(TestDeepSky.M42.id to TestDeepSky.entry(objectId = TestDeepSky.M42.id, note = "Trapezium split")),
        tonight = TonightSky.Known(tonight),
    )

    @Test
    @Config(qualifiers = ScreenshotDevices.COMPACT)
    fun observingLog() {
        composeTestRule.captureScreen("ObservingLog") {
            ObservingLogContent(
                uiState = state,
                onBackClick = {},
                onShowOnSkyMap = {},
                onCatalogueChange = {},
                onNotSeenOnlyChange = {},
                onUpTonightOnlyChange = {},
                onMarkSeen = { _, _, _, _ -> },
                onRemoveSeen = {},
                today = LocalDate.of(2026, 10, 22),
            )
        }
    }

    @Test
    fun deepSkyTonightCard() {
        composeTestRule.captureComponent("DeepSkyTonightCard") {
            DeepSkyTonightCardContent(state, onOpenLog = {}, onObjectClick = {})
        }
    }
}
