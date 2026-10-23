package com.radustavila.nasaexplorer.feature.comets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.radustavila.nasaexplorer.core.model.CometsTonight
import com.radustavila.nasaexplorer.core.model.DataFreshness
import com.radustavila.nasaexplorer.core.model.GeoCoordinate
import com.radustavila.nasaexplorer.core.screenshottesting.ScreenshotDevices
import com.radustavila.nasaexplorer.core.screenshottesting.captureComponent
import com.radustavila.nasaexplorer.core.screenshottesting.captureScreen
import com.radustavila.nasaexplorer.core.testing.data.TestComets
import com.radustavila.nasaexplorer.feature.comets.sky.CometsTonightCardContent
import java.time.Instant
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Golden images of the comets screens over fixed data; `./gradlew recordRoborazziDebug` rewrites them. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CometsScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    // Over a week old, so the freshness line shows a fixed date rather than a relative time.
    private val freshness = DataFreshness(lastUpdatedAt = Instant.parse("2026-01-01T00:00:00Z"))
    private val bucharest = GeoCoordinate(latitude = 44.43, longitude = 26.10)
    private val bright = TestComets.tonight(
        comet = TestComets.comet(designation = "2026 B1", name = "C/2026 B1 (Bright)", peakMagnitude = 6.8),
        magnitude = 7.6,
        constellation = "Ursa Major",
    )
    private val nakedEye = TestComets.tonight(
        comet = TestComets.comet(designation = "2026 C1", name = "C/2026 C1 (Lemmon)", peakMagnitude = 3.9),
        magnitude = 4.4,
        constellation = "Bootes",
        sky = TestComets.localSky(isAlwaysUp = true),
    )
    private val faint = TestComets.tonight()
    private val withComets = CometsUiState(
        comets = CometsTonight(bucharest, visible = listOf(nakedEye, bright), faint = listOf(faint)),
        freshness = freshness,
    )
    private val withoutComets = CometsUiState(
        comets = CometsTonight(null, visible = emptyList(), faint = listOf(faint)),
        freshness = freshness,
    )

    @Test
    @Config(qualifiers = ScreenshotDevices.COMPACT)
    fun cometsCompact() {
        composeTestRule.captureScreen("CometsCompact") { CometsContent(withComets, {}, {}, {}, {}) }
    }

    @Test
    @Config(qualifiers = ScreenshotDevices.EXPANDED)
    fun cometsExpanded() {
        composeTestRule.captureScreen("CometsExpanded") { CometsContent(withComets, {}, {}, {}, {}) }
    }

    @Test
    @Config(qualifiers = ScreenshotDevices.COMPACT)
    fun cometsEmpty() {
        composeTestRule.captureScreen("CometsEmpty") { CometsContent(withoutComets, {}, {}, {}, {}) }
    }

    @Test
    @Config(qualifiers = ScreenshotDevices.COMPACT)
    fun cometDetails() {
        composeTestRule.captureScreen("CometDetails") {
            CometDetailsContent(
                CometDetailsUiState(designation = "2026 B1", isLoaded = true, comet = bright),
                onBackClick = {},
            )
        }
    }

    @Test
    @Config(qualifiers = ScreenshotDevices.COMPACT)
    fun cometsTonightCard() {
        composeTestRule.captureComponent("CometsTonightCard", width = 380.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                CometsTonightCardContent(withComets, {}, {})
                CometsTonightCardContent(withoutComets, {}, {})
            }
        }
    }
}
