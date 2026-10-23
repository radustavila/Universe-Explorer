package com.radustavila.nasaexplorer.feature.events

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.radustavila.nasaexplorer.core.domain.sky.SkyEventReminderLead
import com.radustavila.nasaexplorer.core.model.SkyEvent
import com.radustavila.nasaexplorer.core.screenshottesting.ScreenshotDevices
import com.radustavila.nasaexplorer.core.screenshottesting.captureComponent
import com.radustavila.nasaexplorer.core.screenshottesting.captureScreen
import com.radustavila.nasaexplorer.core.testing.data.TestSky
import com.radustavila.nasaexplorer.core.testing.data.TestSkyEvents
import com.radustavila.nasaexplorer.feature.events.component.SkyEventDetails
import com.radustavila.nasaexplorer.feature.events.next.NextInTheSkyCardContent
import com.radustavila.nasaexplorer.feature.events.next.NextInTheSkyUiState
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Golden images of the sky events calendar; `./gradlew recordRoborazziDebug` rewrites them. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SkyEventsScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    @Config(qualifiers = ScreenshotDevices.COMPACT)
    fun skyEventsCompact() = captureCalendar("SkyEventsCompact")

    @Test
    @Config(qualifiers = ScreenshotDevices.EXPANDED)
    fun skyEventsExpanded() = captureCalendar("SkyEventsExpanded")

    @Test
    @Config(qualifiers = ScreenshotDevices.COMPACT)
    fun solarEclipseDetails() = captureDetails(
        "SkyEventSolarEclipse",
        TestSkyEvents.totalSolarEclipse(),
        SkyEventReminderLead.ONE_HOUR,
    )

    @Test
    @Config(qualifiers = ScreenshotDevices.COMPACT)
    fun meteorShowerDetails() = captureDetails("SkyEventMeteorShower", TestSkyEvents.perseids(), lead = null)

    @Test
    @Config(qualifiers = ScreenshotDevices.COMPACT)
    fun cometPerihelionDetails() =
        captureDetails("SkyEventCometPerihelion", TestSkyEvents.cometPerihelion(), lead = null)

    @Test
    @Config(qualifiers = ScreenshotDevices.COMPACT)
    fun nextInTheSkyCard() {
        composeTestRule.captureComponent("NextInTheSkyCard", width = 380.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                NextInTheSkyCardContent(
                    uiState = NextInTheSkyUiState(
                        isLoaded = true,
                        events = listOf(
                            TestSkyEvents.totalSolarEclipse(),
                            TestSkyEvents.perseids(),
                            TestSkyEvents.fullMoon(),
                        ),
                        hasPlace = true,
                    ),
                    onOpenCalendar = {},
                    onEventClick = {},
                )
                NextInTheSkyCardContent(
                    uiState = NextInTheSkyUiState(
                        isLoaded = true,
                        events = listOf(
                            TestSkyEvents.totalSolarEclipse(local = false),
                            TestSkyEvents.saturnOpposition(),
                        ),
                        hasPlace = false,
                    ),
                    onOpenCalendar = {},
                    onEventClick = {},
                )
            }
        }
    }

    private fun captureCalendar(name: String) {
        val events = TestSkyEvents.august2026()
        val weeks = listOf(
            SkyEventWeek(LocalDate.parse("2026-08-02"), LocalDate.parse("2026-08-08"), events.take(1)),
            SkyEventWeek(LocalDate.parse("2026-08-09"), LocalDate.parse("2026-08-15"), events.subList(1, 5)),
            SkyEventWeek(LocalDate.parse("2026-08-16"), LocalDate.parse("2026-08-22"), events.subList(5, 6)),
            SkyEventWeek(LocalDate.parse("2026-08-23"), LocalDate.parse("2026-08-29"), events.drop(6)),
        )
        composeTestRule.captureScreen(name) {
            SkyEventsContent(
                uiState = SkyEventsUiState(
                    month = YearMonth.of(2026, 8),
                    isLoading = false,
                    weeks = weeks,
                    isLocationLoaded = true,
                    location = TestSky.location(name = "Zaragoza"),
                    reminders = mapOf(TestSkyEvents.totalSolarEclipse().id to SkyEventReminderLead.ONE_HOUR),
                    now = Instant.parse("2026-08-10T12:00:00Z"),
                ),
                onBackClick = {},
                onSetLocation = {},
                onPreviousMonth = {},
                onNextMonth = {},
                onThisMonth = {},
                onFilterToggle = {},
                onEventClick = {},
                onSheetDismiss = {},
                onReminderChange = { _, _ -> },
                onCometClick = {},
            )
        }
    }

    private fun captureDetails(name: String, event: SkyEvent, lead: SkyEventReminderLead?) {
        composeTestRule.captureComponent(name, width = 400.dp) {
            SkyEventDetails(
                event = event,
                hasPlace = true,
                isPast = false,
                reminderOn = lead != null,
                reminderLead = lead,
                onReminderClick = {},
                onLeadChange = {},
                onCometClick = {},
            )
        }
    }
}
