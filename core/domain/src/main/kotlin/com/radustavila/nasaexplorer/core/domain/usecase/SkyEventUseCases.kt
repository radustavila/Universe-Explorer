package com.radustavila.nasaexplorer.core.domain.usecase

import com.radustavila.nasaexplorer.core.common.dispatchers.AppDispatcher
import com.radustavila.nasaexplorer.core.common.dispatchers.Dispatcher
import com.radustavila.nasaexplorer.core.common.time.TimeProvider
import com.radustavila.nasaexplorer.core.domain.location.ObserverLocationRepository
import com.radustavila.nasaexplorer.core.domain.sky.SkyEventCalendar
import com.radustavila.nasaexplorer.core.domain.sky.SkyEventNotifier
import com.radustavila.nasaexplorer.core.domain.sky.SkyEventReminder
import com.radustavila.nasaexplorer.core.domain.sky.SkyEventReminderLead
import com.radustavila.nasaexplorer.core.domain.sky.SkyEventReminderRepository
import com.radustavila.nasaexplorer.core.domain.sky.SkyEventReminderScheduler
import com.radustavila.nasaexplorer.core.domain.sky.find
import com.radustavila.nasaexplorer.core.model.CometPerihelion
import com.radustavila.nasaexplorer.core.model.GeoCoordinate
import com.radustavila.nasaexplorer.core.model.MoonPhaseEvent
import com.radustavila.nasaexplorer.core.model.MoonQuarter
import com.radustavila.nasaexplorer.core.model.SkyEvent
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectIndexed
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.withContext

/**
 * The sky events from [from] to [until] for the saved place (global facts only without one), with
 * the perihelia of the bright comets F18 tracks, in time order. Recomputed when the place or the
 * comets change. Entirely offline.
 */
class ObserveSkyEventsUseCase @Inject constructor(
    private val calendar: SkyEventCalendar,
    private val locationRepository: ObserverLocationRepository,
    private val observeCometEvents: ObserveCometPerihelionEventsUseCase,
    @Dispatcher(AppDispatcher.Default) private val computeDispatcher: CoroutineDispatcher,
) {
    operator fun invoke(from: Instant, until: Instant): Flow<List<SkyEvent>> =
        combine(locationRepository.observerCoordinate(), observeCometEvents()) { observer, comets ->
            calendar.events(from, until, observer).withComets(comets, from, until)
        }.flowOn(computeDispatcher)
}

/**
 * The next few events worth a card ("Next in the sky"), soonest first: everything but the Moon's
 * first and last quarters, which happen every week. Rolls forward on its own as events pass.
 */
class ObserveUpcomingSkyEventsUseCase @Inject constructor(
    private val calendar: SkyEventCalendar,
    private val locationRepository: ObserverLocationRepository,
    private val observeCometEvents: ObserveCometPerihelionEventsUseCase,
    private val timeProvider: TimeProvider,
    @Dispatcher(AppDispatcher.Default) private val computeDispatcher: CoroutineDispatcher,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(limit: Int = DEFAULT_LIMIT): Flow<List<SkyEvent>> =
        combine(locationRepository.observerCoordinate(), observeCometEvents(), ::Pair)
            .transformLatest { (observer, comets) ->
                while (true) {
                    val now = timeProvider.now()
                    val until = now.plus(WINDOW)
                    emit(
                        calendar.events(from = now, until = until, observer = observer)
                            .withComets(comets, now, until)
                            .filter(SkyEvent::isHeadline)
                            .take(limit),
                    )
                    delay(RECOMPUTE_INTERVAL.toMillis())
                }
            }.flowOn(computeDispatcher)

    companion object {
        const val DEFAULT_LIMIT = 3

        /** Long enough to always hold a few events: there are two full or new Moons a month. */
        val WINDOW: Duration = Duration.ofDays(60)
        val RECOMPUTE_INTERVAL: Duration = Duration.ofMinutes(30)
    }
}

/** The event [id] names, for the saved place; null when it does not exist. */
class FindSkyEventUseCase @Inject constructor(
    private val calendar: SkyEventCalendar,
    private val locationRepository: ObserverLocationRepository,
    private val observeCometEvents: ObserveCometPerihelionEventsUseCase,
    @Dispatcher(AppDispatcher.Default) private val computeDispatcher: CoroutineDispatcher,
) {
    suspend operator fun invoke(id: String): SkyEvent? {
        val observer = locationRepository.location.first()?.coordinate
        val comets = observeCometEvents().first()
        return withContext(computeDispatcher) { calendar.find(id, observer, comets) }
    }
}

class ObserveSkyEventRemindersUseCase @Inject constructor(
    private val repository: SkyEventReminderRepository,
) {
    operator fun invoke(): Flow<Map<String, SkyEventReminder>> = repository.reminders
}

/** Turns the reminder for one event on with [lead], or off with null, and re-arms the wake-up. */
class SetSkyEventReminderUseCase @Inject constructor(
    private val repository: SkyEventReminderRepository,
    private val rescheduleReminders: RescheduleSkyEventRemindersUseCase,
) {
    suspend operator fun invoke(eventId: String, lead: SkyEventReminderLead?) {
        if (lead == null) {
            repository.removeReminders(setOf(eventId))
        } else {
            repository.setReminder(SkyEventReminder(eventId, lead))
        }
        rescheduleReminders()
    }
}

/**
 * Arms the one pending wake-up for the soonest reminder (its event's [SkyEvent.start] minus the
 * lead time), or cancels it when there is none. Events are recomputed for the saved place each
 * time, so a new place moves the local times (an eclipse's first contact). Reminders whose event
 * is over, or no longer exists, are dropped.
 */
class RescheduleSkyEventRemindersUseCase @Inject constructor(
    private val repository: SkyEventReminderRepository,
    private val calendar: SkyEventCalendar,
    private val locationRepository: ObserverLocationRepository,
    private val observeCometEvents: ObserveCometPerihelionEventsUseCase,
    private val scheduler: SkyEventReminderScheduler,
    private val timeProvider: TimeProvider,
    @Dispatcher(AppDispatcher.Default) private val computeDispatcher: CoroutineDispatcher,
) {
    suspend operator fun invoke() {
        val reminders = repository.reminders.first().values
        if (reminders.isEmpty()) {
            scheduler.cancel()
            return
        }
        val observer = locationRepository.location.first()?.coordinate
        val comets = observeCometEvents().first()
        val now = timeProvider.now()
        val resolved = withContext(computeDispatcher) {
            reminders.associateWith { reminder -> calendar.find(reminder.eventId, observer, comets) }
        }
        val over = resolved.filter { (_, event) -> event == null || event.isOverAt(now) }.keys
        if (over.isNotEmpty()) repository.removeReminders(over.mapTo(mutableSetOf()) { it.eventId })

        val next = resolved.mapNotNull { (reminder, event) ->
            event?.takeUnless { it.isOverAt(now) }?.let { maxOf(now, it.start.minus(reminder.lead.duration)) }
        }.minOrNull()
        if (next == null) scheduler.cancel() else scheduler.scheduleAt(next)
    }
}

/**
 * What the reminder's wake-up runs: posts every reminder that is due (each once, then forgets it)
 * and arms the next wake-up. A wake-up that comes late, e.g. after the phone was off, still posts
 * for an event that started less than [LATE_GRACE] ago.
 */
class RemindOfSkyEventsUseCase @Inject constructor(
    private val repository: SkyEventReminderRepository,
    private val calendar: SkyEventCalendar,
    private val locationRepository: ObserverLocationRepository,
    private val observeCometEvents: ObserveCometPerihelionEventsUseCase,
    private val notifier: SkyEventNotifier,
    private val rescheduleReminders: RescheduleSkyEventRemindersUseCase,
    private val timeProvider: TimeProvider,
    @Dispatcher(AppDispatcher.Default) private val computeDispatcher: CoroutineDispatcher,
) {
    /** @return the events a reminder was posted for. */
    suspend operator fun invoke(): List<SkyEvent> {
        val reminders = repository.reminders.first().values
        val observer = locationRepository.location.first()?.coordinate
        val comets = observeCometEvents().first()
        val now = timeProvider.now()
        val due = withContext(computeDispatcher) {
            reminders.mapNotNull { reminder ->
                calendar.find(reminder.eventId, observer, comets)?.takeIf { event ->
                    val remindAt = event.start.minus(reminder.lead.duration)
                    !remindAt.isAfter(now.plus(EARLY_WAKE_UP_SLACK)) && !event.isOverAt(now)
                }
            }
        }
        due.forEach { notifier.notifyUpcoming(it) }
        if (due.isNotEmpty()) repository.removeReminders(due.mapTo(mutableSetOf()) { it.id })
        rescheduleReminders()
        return due
    }

    private companion object {
        /** WorkManager may run a delayed job a little early. */
        val EARLY_WAKE_UP_SLACK: Duration = Duration.ofMinutes(5)
    }
}

/**
 * Re-arms the reminders whenever the saved place changes, from any screen. Collected for the whole
 * life of the process from app start. The first value (the place already saved) re-arms only when
 * there are reminders: that also restores the wake-up for reminders brought back from a backup,
 * which WorkManager's jobs are not part of.
 */
class RescheduleSkyEventRemindersOnLocationChangeUseCase @Inject constructor(
    private val locationRepository: ObserverLocationRepository,
    private val reminderRepository: SkyEventReminderRepository,
    private val rescheduleReminders: RescheduleSkyEventRemindersUseCase,
) {
    suspend operator fun invoke() {
        locationRepository.observerCoordinate().collectIndexed { index, _ ->
            if (index > 0 || reminderRepository.reminders.first().isNotEmpty()) rescheduleReminders()
        }
    }
}

/** An event whose start is more than this in the past is over for reminders. */
val LATE_GRACE: Duration = Duration.ofHours(1)

private fun SkyEvent.isOverAt(now: Instant): Boolean = start.plus(LATE_GRACE).isBefore(now)

/** The computed events with the comet perihelia from [from] (inclusive) to [until] (exclusive), in time order. */
private fun List<SkyEvent>.withComets(comets: List<CometPerihelion>, from: Instant, until: Instant): List<SkyEvent> {
    val inRange = comets.filter { it.time >= from && it.time < until }
    return if (inRange.isEmpty()) this else (this + inRange).sortedBy { it.time }
}

/** A comet perihelion by its id, else the computed event [id] names. */
private fun SkyEventCalendar.find(id: String, observer: GeoCoordinate?, comets: List<CometPerihelion>): SkyEvent? =
    comets.firstOrNull { it.id == id } ?: find(id, observer)

/** Weekly quarter Moons are left out of the headline lists. */
private fun SkyEvent.isHeadline(): Boolean =
    this !is MoonPhaseEvent || quarter == MoonQuarter.FULL_MOON || quarter == MoonQuarter.NEW_MOON

private fun ObserverLocationRepository.observerCoordinate(): Flow<GeoCoordinate?> =
    location.map { it?.coordinate }.distinctUntilChanged()
