package com.radustavila.nasaexplorer.feature.events

import com.radustavila.nasaexplorer.core.domain.usecase.FindSkyEventUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveObserverLocationUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveSkyEventRemindersUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveSkyEventsUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveUpcomingSkyEventsUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.RescheduleSkyEventRemindersUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.SetSkyEventReminderUseCase
import com.radustavila.nasaexplorer.core.model.ObserverLocation
import com.radustavila.nasaexplorer.core.model.SkyEvent
import com.radustavila.nasaexplorer.core.testing.data.TestSkyEvents
import com.radustavila.nasaexplorer.core.testing.fake.FakeObserverLocationRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeSkyEventCalendar
import com.radustavila.nasaexplorer.core.testing.fake.FakeSkyEventReminderRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeSkyEventReminderScheduler
import com.radustavila.nasaexplorer.core.testing.fake.FakeTimeProvider
import java.time.Instant
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/** The calendar's use cases wired to fakes, as the screen and its cards see them. */
class SkyEventsFixture(
    location: ObserverLocation? = null,
    events: List<SkyEvent> = TestSkyEvents.august2026(),
    now: Instant = Instant.parse("2026-08-10T12:00:00Z"),
    dispatcher: CoroutineDispatcher = Dispatchers.Unconfined,
) {
    val clock = FakeTimeProvider(currentInstant = now)
    val locationRepository = FakeObserverLocationRepository(location)
    val calendar = FakeSkyEventCalendar(events)
    val reminders = FakeSkyEventReminderRepository()
    val scheduler = FakeSkyEventReminderScheduler()

    private val reschedule = RescheduleSkyEventRemindersUseCase(
        reminders,
        calendar,
        locationRepository,
        scheduler,
        clock,
        dispatcher,
    )

    val observeSkyEvents = ObserveSkyEventsUseCase(calendar, locationRepository, dispatcher)
    val observeUpcoming = ObserveUpcomingSkyEventsUseCase(calendar, locationRepository, clock, dispatcher)
    val observeLocation = ObserveObserverLocationUseCase(locationRepository)
    val observeReminders = ObserveSkyEventRemindersUseCase(reminders)
    val setReminder = SetSkyEventReminderUseCase(reminders, reschedule)
    val findEvent = FindSkyEventUseCase(calendar, locationRepository, dispatcher)
}
