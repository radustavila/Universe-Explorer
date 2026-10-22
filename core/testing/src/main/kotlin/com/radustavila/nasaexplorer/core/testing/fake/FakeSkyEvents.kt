package com.radustavila.nasaexplorer.core.testing.fake

import com.radustavila.nasaexplorer.core.domain.sky.SkyEventCalendar
import com.radustavila.nasaexplorer.core.domain.sky.SkyEventNotifier
import com.radustavila.nasaexplorer.core.domain.sky.SkyEventReminder
import com.radustavila.nasaexplorer.core.domain.sky.SkyEventReminderRepository
import com.radustavila.nasaexplorer.core.domain.sky.SkyEventReminderScheduler
import com.radustavila.nasaexplorer.core.model.GeoCoordinate
import com.radustavila.nasaexplorer.core.model.SkyEvent
import com.radustavila.nasaexplorer.core.model.SkyEventCategory
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/**
 * Answers from a fixed list, filtered to the requested range and categories like the real calendar.
 * [eventsFor] can make the answer depend on the observer (local eclipse times).
 */
class FakeSkyEventCalendar(
    var eventsFor: (GeoCoordinate?) -> List<SkyEvent> = { emptyList() },
) : SkyEventCalendar {

    constructor(events: List<SkyEvent>) : this({ _: GeoCoordinate? -> events })

    /** The observer of every request, oldest first. */
    val observers = mutableListOf<GeoCoordinate?>()

    override fun events(
        from: Instant,
        until: Instant,
        observer: GeoCoordinate?,
        categories: Set<SkyEventCategory>,
    ): List<SkyEvent> {
        observers += observer
        return eventsFor(observer)
            .filter { it.time >= from && it.time < until && it.category in categories }
            .sortedBy { it.time }
    }
}

class FakeSkyEventReminderRepository(initial: List<SkyEventReminder> = emptyList()) : SkyEventReminderRepository {

    private val state = MutableStateFlow(initial.associateBy { it.eventId })

    val current: Map<String, SkyEventReminder> get() = state.value

    override val reminders: Flow<Map<String, SkyEventReminder>> = state

    override suspend fun setReminder(reminder: SkyEventReminder) {
        state.update { it + (reminder.eventId to reminder) }
    }

    override suspend fun removeReminders(eventIds: Set<String>) {
        state.update { it - eventIds }
    }
}

/** Remembers the pending wake-up; null after [cancel]. */
class FakeSkyEventReminderScheduler : SkyEventReminderScheduler {

    var scheduledAt: Instant? = null
        private set

    override fun scheduleAt(at: Instant) {
        scheduledAt = at
    }

    override fun cancel() {
        scheduledAt = null
    }
}

class FakeSkyEventNotifier : SkyEventNotifier {

    val notified = mutableListOf<SkyEvent>()

    override suspend fun notifyUpcoming(event: SkyEvent) {
        notified += event
    }
}
