package com.radustavila.nasaexplorer.core.domain.sky

import com.radustavila.nasaexplorer.core.model.SkyEvent
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.flow.Flow

/** How long before an event's [SkyEvent.start] its reminder is posted. Stored by name. */
enum class SkyEventReminderLead(val duration: Duration) {
    FIFTEEN_MINUTES(Duration.ofMinutes(QUARTER_HOUR_MINUTES)),
    ONE_HOUR(Duration.ofHours(1)),
    ONE_DAY(Duration.ofDays(1)),
    ;

    companion object {
        val DEFAULT = ONE_HOUR
    }
}

private const val QUARTER_HOUR_MINUTES = 15L

/** The user asked to be reminded of [eventId] (a [SkyEvent.id]) [lead] before it starts. */
data class SkyEventReminder(
    val eventId: String,
    val lead: SkyEventReminderLead,
)

/** The reminders the user turned on, one per event. Opt-in: none until the user picks an event. */
interface SkyEventReminderRepository {

    /** Keyed by event id. */
    val reminders: Flow<Map<String, SkyEventReminder>>

    /** Adds the reminder, or replaces the lead time of the one for the same event. */
    suspend fun setReminder(reminder: SkyEventReminder)

    suspend fun removeReminders(eventIds: Set<String>)
}

/** Wakes the app when the next reminder is due, even when it is not running or after a reboot. */
interface SkyEventReminderScheduler {

    /** Replaces any pending wake-up with one at [at]. */
    fun scheduleAt(at: Instant)

    fun cancel()
}

/** Posts "the total lunar eclipse begins at 11:44". */
interface SkyEventNotifier {
    suspend fun notifyUpcoming(event: SkyEvent)
}
