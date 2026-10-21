package com.radustavila.nasaexplorer.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.radustavila.nasaexplorer.core.model.DataFreshness
import com.radustavila.nasaexplorer.core.screenshottesting.ScreenshotDevices
import com.radustavila.nasaexplorer.core.screenshottesting.captureComponent
import com.radustavila.nasaexplorer.core.testing.data.TestApods
import java.time.Duration
import java.time.Instant
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Golden images of the shared components; `./gradlew recordRoborazziDebug` rewrites them. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = ScreenshotDevices.COMPACT)
class ComponentScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun apodCard() {
        composeTestRule.captureComponent("ApodCard") {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                ApodCard(apod = TestApods.image(isFavorite = true), onClick = {}, onToggleFavorite = {})
                ApodCard(apod = TestApods.streamableVideo(), onClick = {}, onToggleFavorite = {})
            }
        }
    }

    @Test
    fun freshnessLabel() {
        // Recent enough to read relatively, or old enough to show a date: either way independent of today.
        val recent = Instant.now().minus(Duration.ofMinutes(5).plusSeconds(10))
        val old = Instant.parse("2026-01-15T09:30:00Z")
        composeTestRule.captureComponent("FreshnessLabel") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FreshnessLabel(DataFreshness(lastUpdatedAt = recent))
                FreshnessLabel(DataFreshness(lastUpdatedAt = old, lastRefreshFailed = true))
                FreshnessLabel(DataFreshness(lastUpdatedAt = old, isOffline = true))
            }
        }
    }
}
