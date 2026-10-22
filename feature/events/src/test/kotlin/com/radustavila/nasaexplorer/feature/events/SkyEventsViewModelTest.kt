package com.radustavila.nasaexplorer.feature.events

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.radustavila.nasaexplorer.core.domain.sky.SkyEventReminderLead
import com.radustavila.nasaexplorer.core.model.SkyEventCategory
import com.radustavila.nasaexplorer.core.testing.data.TestSky
import com.radustavila.nasaexplorer.core.testing.data.TestSkyEvents
import com.radustavila.nasaexplorer.core.testing.rule.MainDispatcherRule
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SkyEventsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val defaultZone = TimeZone.getDefault()
    private val defaultLocale = Locale.getDefault()

    @Before
    fun setUp() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        // Weeks start on Sunday.
        Locale.setDefault(Locale.US)
    }

    @After
    fun tearDown() {
        TimeZone.setDefault(defaultZone)
        Locale.setDefault(defaultLocale)
    }

    private fun viewModel(fixture: SkyEventsFixture, eventId: String? = null) = SkyEventsViewModel(
        savedStateHandle = SavedStateHandle(if (eventId == null) emptyMap() else mapOf("event" to eventId)),
        observeSkyEvents = fixture.observeSkyEvents,
        observeLocation = fixture.observeLocation,
        observeReminders = fixture.observeReminders,
        setReminder = fixture.setReminder,
        findEvent = fixture.findEvent,
        timeProvider = fixture.clock,
    )

    @Test
    fun `opens on this month, its events grouped by week`() = runTest {
        viewModel(SkyEventsFixture()).uiState.test {
            val state = expectMostRecentItem()
            assertEquals(YearMonth.of(2026, 8), state.month)
            assertTrue(state.isCurrentMonth)
            assertFalse(state.isLoading)
            assertEquals(
                listOf("2026-08-02", "2026-08-09", "2026-08-16", "2026-08-23").map(LocalDate::parse),
                state.weeks.map { it.firstDay },
            )
            assertEquals(listOf(1, 4, 1, 2), state.weeks.map { it.events.size })
            assertEquals(LocalDate.parse("2026-08-29"), state.weeks.last().lastDay)
            assertNull(state.location)
        }
    }

    @Test
    fun `filters narrow the list, and months move back and forth`() = runTest {
        val viewModel = viewModel(SkyEventsFixture())
        viewModel.uiState.test {
            expectMostRecentItem()
            viewModel.onFilterToggle(SkyEventCategory.ECLIPSES)
            val eclipses = expectMostRecentItem().weeks.flatMap { it.events }
            assertEquals(
                listOf(TestSkyEvents.totalSolarEclipse().id, TestSkyEvents.partialLunarEclipse().id),
                eclipses.map { it.id },
            )

            viewModel.onFilterToggle(SkyEventCategory.METEORS)
            assertEquals(3, expectMostRecentItem().weeks.sumOf { it.events.size })

            viewModel.onNextMonth()
            val september = expectMostRecentItem()
            assertEquals(YearMonth.of(2026, 9), september.month)
            assertFalse(september.isCurrentMonth)
            assertTrue(september.weeks.isEmpty())

            viewModel.onThisMonth()
            assertEquals(YearMonth.of(2026, 8), expectMostRecentItem().month)
        }
    }

    @Test
    fun `opened with an event, it jumps to the event's month with its sheet open`() = runTest {
        val fixture = SkyEventsFixture(
            events = TestSkyEvents.august2026() + TestSkyEvents.geminids(),
            location = TestSky.location(),
        )
        viewModel(fixture, eventId = TestSkyEvents.geminids().id).uiState.test {
            val state = expectMostRecentItem()
            assertEquals(YearMonth.of(2026, 12), state.month)
            assertEquals(TestSkyEvents.geminids(), state.selected)
            assertEquals(TestSky.location(), state.location)
        }
    }

    @Test
    fun `a reminder is set, changed and removed from the sheet`() = runTest {
        val fixture = SkyEventsFixture()
        val viewModel = viewModel(fixture)
        val perseids = TestSkyEvents.perseids()
        viewModel.uiState.test {
            viewModel.onEventClick(perseids)
            assertEquals(perseids, expectMostRecentItem().selected)

            viewModel.onReminderChange(perseids.id, SkyEventReminderLead.ONE_DAY)
            assertEquals(mapOf(perseids.id to SkyEventReminderLead.ONE_DAY), expectMostRecentItem().reminders)
            assertEquals(perseids.start.minus(SkyEventReminderLead.ONE_DAY.duration), fixture.scheduler.scheduledAt)

            viewModel.onReminderChange(perseids.id, null)
            assertTrue(expectMostRecentItem().reminders.isEmpty())
            assertNull(fixture.scheduler.scheduledAt)

            viewModel.onSheetDismiss()
            assertNull(expectMostRecentItem().selected)
        }
    }
}
