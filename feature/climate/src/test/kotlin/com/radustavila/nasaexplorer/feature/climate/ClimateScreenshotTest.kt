package com.radustavila.nasaexplorer.feature.climate

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import com.radustavila.nasaexplorer.core.designsystem.units.LocalUnitPreferences
import com.radustavila.nasaexplorer.core.model.ClimateIndicator
import com.radustavila.nasaexplorer.core.model.DataFreshness
import com.radustavila.nasaexplorer.core.model.MeasurementSystem
import com.radustavila.nasaexplorer.core.model.UnitPreferences
import com.radustavila.nasaexplorer.core.screenshottesting.ScreenshotDevices
import com.radustavila.nasaexplorer.core.screenshottesting.captureComponent
import com.radustavila.nasaexplorer.core.screenshottesting.captureScreen
import com.radustavila.nasaexplorer.core.testing.data.TestClimate
import java.time.Instant
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Golden images of the vital signs and the detail charts over fixed data; `./gradlew recordRoborazziDebug`
 * rewrites them.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ClimateScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    // Over a week old, so the freshness line shows a fixed date rather than a relative time.
    private val freshness = DataFreshness(lastUpdatedAt = Instant.parse("2026-01-01T00:00:00Z"))

    private val section = VitalSignsUiState(isLoaded = true, series = TestClimate.all(), freshness = freshness)

    private fun detail(indicator: ClimateIndicator) = ClimateUiState(
        indicator = indicator,
        isLoaded = true,
        series = TestClimate.all().first { it.indicator == indicator },
        freshness = freshness,
    )

    @Test
    @Config(qualifiers = ScreenshotDevices.COMPACT)
    fun vitalSignsCompact() {
        composeTestRule.captureComponent("VitalSignsCompact") { Metric { VitalSignsContent(section, {}) } }
    }

    @Test
    @Config(qualifiers = ScreenshotDevices.EXPANDED)
    fun vitalSignsExpanded() {
        composeTestRule.captureComponent("VitalSignsExpanded") { Metric { VitalSignsContent(section, {}) } }
    }

    @Test
    @Config(qualifiers = ScreenshotDevices.COMPACT)
    fun co2Compact() {
        composeTestRule.captureScreen("ClimateCo2Compact") {
            Metric { ClimateContent(detail(ClimateIndicator.CO2), {}, {}, {}) }
        }
    }

    @Test
    @Config(qualifiers = ScreenshotDevices.EXPANDED)
    fun temperatureExpanded() {
        composeTestRule.captureScreen("ClimateTemperatureExpanded") {
            Metric { ClimateContent(detail(ClimateIndicator.GLOBAL_TEMPERATURE), {}, {}, {}) }
        }
    }

    @Test
    @Config(qualifiers = ScreenshotDevices.COMPACT)
    fun seaIceImperialCompact() {
        composeTestRule.captureScreen("ClimateSeaIceImperialCompact") {
            CompositionLocalProvider(LocalUnitPreferences provides UnitPreferences(MeasurementSystem.IMPERIAL)) {
                ClimateContent(detail(ClimateIndicator.ARCTIC_SEA_ICE), {}, {}, {})
            }
        }
    }

    /** The brief's values read in metric; the imperial golden checks the other system. */
    @Composable
    private fun Metric(content: @Composable () -> Unit) {
        val metric = UnitPreferences(MeasurementSystem.METRIC)
        CompositionLocalProvider(LocalUnitPreferences provides metric, content = content)
    }
}
