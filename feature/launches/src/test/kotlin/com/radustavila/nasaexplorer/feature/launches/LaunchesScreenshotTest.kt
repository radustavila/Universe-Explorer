package com.radustavila.nasaexplorer.feature.launches

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.radustavila.nasaexplorer.core.designsystem.notification.NotificationPermissionToggle
import com.radustavila.nasaexplorer.core.model.DataFreshness
import com.radustavila.nasaexplorer.core.model.Launch
import com.radustavila.nasaexplorer.core.model.LaunchStatus
import com.radustavila.nasaexplorer.core.model.NetPrecision
import com.radustavila.nasaexplorer.core.screenshottesting.ScreenshotDevices
import com.radustavila.nasaexplorer.core.screenshottesting.captureComponent
import com.radustavila.nasaexplorer.core.screenshottesting.captureScreen
import com.radustavila.nasaexplorer.core.testing.data.TestLaunches
import com.radustavila.nasaexplorer.feature.launches.details.LaunchDetailsContent
import com.radustavila.nasaexplorer.feature.launches.details.LaunchDetailsUiState
import com.radustavila.nasaexplorer.feature.launches.list.LaunchFilters
import com.radustavila.nasaexplorer.feature.launches.list.LaunchesContent
import com.radustavila.nasaexplorer.feature.launches.list.LaunchesTab
import com.radustavila.nasaexplorer.feature.launches.list.LaunchesUiState
import java.time.Duration
import java.time.Instant
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Golden images of the Launches screens over fixed data; `./gradlew recordRoborazziDebug` rewrites them. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LaunchesScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val now = TestLaunches.NOW

    // Over a week old, so the freshness line shows a fixed date rather than a relative time.
    private val freshness = DataFreshness(lastUpdatedAt = Instant.parse("2026-01-01T00:00:00Z"))
    private val upcoming = listOf(
        TestLaunches.detailed(net = now.plus(Duration.ofMinutes(42))),
        TestLaunches.launch(
            id = "crew",
            name = "Falcon 9 Block 5 | Crew-14",
            net = now.plus(Duration.parse("P2DT3H")),
            crewCount = 4,
            livestreamUrl = null,
        ),
        TestLaunches.launch(
            id = "neutron",
            name = "Neutron | Maiden flight",
            net = Instant.parse("2026-12-31T00:00:00Z"),
            status = LaunchStatus.TBD,
            netPrecision = NetPrecision.QUARTER,
            provider = "Rocket Lab",
        ),
    )
    private val past = listOf(
        TestLaunches.detailed(id = "flown", net = now.minus(Duration.ofDays(1)), status = LaunchStatus.SUCCESS)
            .copy(timeline = com.radustavila.nasaexplorer.core.model.LaunchTimeline.PAST),
        TestLaunches.launch(
            id = "failed",
            name = "Electron | Return to sender",
            net = now.minus(Duration.ofDays(3)),
            status = LaunchStatus.FAILURE,
            provider = "Rocket Lab",
        ),
    )
    private val state = LaunchesUiState(
        upcoming = upcoming,
        past = past,
        reminders = setOf(upcoming.first().id),
        upcomingFreshness = freshness,
        pastFreshness = freshness,
    )

    @Test
    @Config(qualifiers = ScreenshotDevices.COMPACT)
    fun launchesUpcoming() {
        composeTestRule.captureScreen("LaunchesUpcoming") { Launches(state) }
    }

    @Test
    @Config(qualifiers = ScreenshotDevices.EXPANDED)
    fun launchesExpanded() {
        composeTestRule.captureScreen("LaunchesExpanded") { Launches(state) }
    }

    @Test
    @Config(qualifiers = ScreenshotDevices.COMPACT)
    fun launchesPast() {
        composeTestRule.captureScreen("LaunchesPast") {
            Launches(state.copy(filters = LaunchFilters(tab = LaunchesTab.PAST)))
        }
    }

    @Test
    @Config(qualifiers = ScreenshotDevices.COMPACT)
    fun launchDetails() {
        composeTestRule.captureScreen("LaunchDetails") {
            LaunchDetailsContent(
                uiState = LaunchDetailsUiState(id = upcoming.first().id, isLoaded = true, launch = upcoming.first()),
                reminder = NotificationPermissionToggle(isOn = true, onClick = {}),
                now = { now },
                onBackClick = {},
                onOpenUrl = {},
                padMap = { _, modifier -> Box(modifier.background(Color(0xFF16324F))) },
            )
        }
    }

    @Test
    @Config(qualifiers = ScreenshotDevices.COMPACT)
    fun nextLaunchesCard() {
        composeTestRule.captureComponent("NextLaunchesCard", width = 380.dp) {
            NextLaunchesCardContent(
                launches = upcoming,
                now = { now },
                onLaunchClick = {},
                onOpenUrl = {},
                onSeeAllClick = {},
                freshness = freshness,
            )
        }
    }

    @androidx.compose.runtime.Composable
    private fun Launches(uiState: LaunchesUiState) {
        LaunchesContent(
            uiState = uiState,
            onBackClick = {},
            onLaunchClick = {},
            onTabSelected = {},
            onProviderSelected = {},
            onCrewedOnlyChange = {},
            reminderToggle = { launch: Launch ->
                NotificationPermissionToggle(isOn = launch.id in uiState.reminders, onClick = {})
            },
            now = { now },
        )
    }
}
