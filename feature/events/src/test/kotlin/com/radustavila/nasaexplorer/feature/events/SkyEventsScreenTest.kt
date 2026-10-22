package com.radustavila.nasaexplorer.feature.events

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import com.radustavila.nasaexplorer.core.designsystem.theme.NasaTheme
import com.radustavila.nasaexplorer.core.domain.sky.SkyEventReminderLead
import com.radustavila.nasaexplorer.core.model.SkyEvent
import com.radustavila.nasaexplorer.core.model.SkyEventCategory
import com.radustavila.nasaexplorer.core.testing.data.TestSky
import com.radustavila.nasaexplorer.core.testing.data.TestSkyEvents
import com.radustavila.nasaexplorer.feature.events.component.SKY_EVENT_REMINDER_SWITCH_TEST_TAG
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SkyEventsScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val august = SkyEventsUiState(
        month = YearMonth.of(2026, 8),
        isLoading = false,
        weeks = listOf(
            SkyEventWeek(
                LocalDate.parse("2026-08-09"),
                LocalDate.parse("2026-08-15"),
                listOf(TestSkyEvents.totalSolarEclipse(), TestSkyEvents.perseids()),
            ),
        ),
        isLocationLoaded = true,
        location = TestSky.location(),
        reminders = mapOf(TestSkyEvents.perseids().id to SkyEventReminderLead.ONE_HOUR),
        now = Instant.parse("2026-08-01T00:00:00Z"),
    )

    private var clicked: SkyEvent? = null
    private var filter: SkyEventCategory? = null
    private var reminder: Pair<String, SkyEventReminderLead?>? = null
    private var placeClicks = 0

    private fun show(state: SkyEventsUiState) {
        composeTestRule.setContent {
            NasaTheme {
                SkyEventsContent(
                    uiState = state,
                    onBackClick = {},
                    onSetLocation = { placeClicks++ },
                    onPreviousMonth = {},
                    onNextMonth = {},
                    onThisMonth = {},
                    onFilterToggle = { filter = it },
                    onEventClick = { clicked = it },
                    onSheetDismiss = {},
                    onReminderChange = { id, lead -> reminder = id to lead },
                )
            }
        }
    }

    @Test
    fun `lists the month's events with what to expect from the user's place`() {
        show(august)

        composeTestRule.onNodeWithText("August 2026").assertIsDisplayed()
        composeTestRule.onNodeWithText("Times and visibility for Bucharest").assertIsDisplayed()
        composeTestRule.onNodeWithText("Total solar eclipse").assertIsDisplayed()
        composeTestRule.onNodeWithText("Total from your place").assertIsDisplayed()
        composeTestRule.onNodeWithText("Perseids peak").assertIsDisplayed()
        composeTestRule.onNodeWithText("Up to 100 an hour, Moon 0% lit").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Reminder set").assertIsDisplayed()

        composeTestRule.onNodeWithText("Perseids peak").performClick()
        assertEquals(TestSkyEvents.perseids(), clicked)
        composeTestRule.onNodeWithText("Meteors").performClick()
        assertEquals(SkyEventCategory.METEORS, filter)
    }

    @Test
    fun `without a place it offers to set one`() {
        val unseen = SkyEventWeek(
            LocalDate.parse("2026-08-09"),
            LocalDate.parse("2026-08-15"),
            listOf(TestSkyEvents.totalSolarEclipse(local = false)),
        )
        show(august.copy(location = null, weeks = listOf(unseen)))

        composeTestRule.onNodeWithText("Set place").performClick()
        assertEquals(1, placeClicks)
        composeTestRule.onNodeWithText("Visible from part of the Earth").assertIsDisplayed()
    }

    @Test
    fun `the sheet shows the local contacts and switches the reminder off`() {
        show(august.copy(selected = TestSkyEvents.perseids()))

        composeTestRule.onNodeWithText("Up to 100 meteors an hour under a dark sky").assertIsDisplayed()
        composeTestRule.onNodeWithText("Meteors seem to come from Perseus").assertIsDisplayed()
        composeTestRule.onNodeWithText("1 hour before").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("1 day before").performClick()
        assertEquals(TestSkyEvents.perseids().id to SkyEventReminderLead.ONE_DAY, reminder)

        composeTestRule.onNodeWithTag(SKY_EVENT_REMINDER_SWITCH_TEST_TAG).performScrollTo().performClick()
        assertEquals(TestSkyEvents.perseids().id to null, reminder)
    }

    @Test
    fun `a past event's sheet has no reminder`() {
        show(august.copy(selected = TestSkyEvents.totalSolarEclipse(), now = Instant.parse("2026-09-01T00:00:00Z")))

        composeTestRule.onNodeWithText("Partial phase begins").assertIsDisplayed()
        composeTestRule.onNodeWithText("Remind me").assertDoesNotExist()
    }

    @Test
    fun `an empty month says so`() {
        show(august.copy(weeks = emptyList()))

        composeTestRule.onNodeWithTag(SKY_EVENTS_LIST_TEST_TAG)
            .performScrollToNode(hasText("Nothing in this category this month."))
    }
}
