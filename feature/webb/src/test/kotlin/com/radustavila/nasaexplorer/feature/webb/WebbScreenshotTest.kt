package com.radustavila.nasaexplorer.feature.webb

import androidx.compose.ui.test.junit4.createComposeRule
import com.radustavila.nasaexplorer.core.domain.usecase.statusAt
import com.radustavila.nasaexplorer.core.model.DataFreshness
import com.radustavila.nasaexplorer.core.screenshottesting.ScreenshotDevices
import com.radustavila.nasaexplorer.core.screenshottesting.captureComponent
import com.radustavila.nasaexplorer.core.screenshottesting.captureScreen
import com.radustavila.nasaexplorer.core.testing.data.TestWebb
import java.time.Duration
import java.time.Instant
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Golden images of the Webb card and week over fixed data; `./gradlew recordRoborazziDebug` rewrites them. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WebbScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val week = TestWebb.week()

    // Over a week old, so the freshness line shows a fixed date rather than a relative time.
    private val freshness = DataFreshness(lastUpdatedAt = Instant.parse("2026-01-01T00:00:00Z"))

    private fun state(now: Instant) = WebbUiState(
        isLoaded = true,
        now = week.statusAt(now),
        visits = week.visits,
        freshness = freshness,
    )

    @Test
    @Config(qualifiers = ScreenshotDevices.COMPACT)
    fun webbNowCard() {
        composeTestRule.captureComponent("WebbNowCard") { WebbNowCardContent(state(TestWebb.NOW), {}, {}) }
    }

    @Test
    @Config(qualifiers = ScreenshotDevices.COMPACT)
    fun webbNowCardSlewing() {
        composeTestRule.captureComponent("WebbNowCardSlewing") {
            WebbNowCardContent(state(Instant.parse("2026-10-23T07:00:00Z")), {}, {})
        }
    }

    @Test
    @Config(qualifiers = ScreenshotDevices.COMPACT)
    fun webbNowCardStale() {
        composeTestRule.captureComponent("WebbNowCardStale") {
            WebbNowCardContent(state(TestWebb.FINE_PHASING.start.plus(Duration.ofDays(9))), {}, {})
        }
    }

    @Test
    @Config(qualifiers = ScreenshotDevices.COMPACT)
    fun webbWeekCompact() {
        composeTestRule.captureScreen("WebbWeekCompact") { WebbContent(state(TestWebb.NOW), {}, {}, {}) }
    }

    @Test
    @Config(qualifiers = ScreenshotDevices.EXPANDED)
    fun webbWeekExpanded() {
        composeTestRule.captureScreen("WebbWeekExpanded") { WebbContent(state(TestWebb.NOW), {}, {}, {}) }
    }
}
