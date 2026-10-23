package com.radustavila.nasaexplorer.core.domain.launch

import com.radustavila.nasaexplorer.core.model.Launch
import java.time.Instant
import kotlinx.coroutines.flow.Flow

/**
 * The user asked to be told shortly before [launchId] lifts off.
 *
 * The reminder follows the launch: it remembers for which NET it already re-checked the launch and
 * posted the notification, so a NET that slips after either arms both again for the new time.
 */
data class LaunchReminder(
    val launchId: String,
    /** The NET the notification was posted for; null while not posted yet. */
    val notifiedNet: Instant? = null,
    /** The NET the launch was last re-checked against Launch Library 2 for. */
    val checkedNet: Instant? = null,
)

/** The launch reminders the user turned on. Opt-in: none until the user rings a launch's bell. */
interface LaunchReminderRepository {

    /** Keyed by launch id. */
    val reminders: Flow<Map<String, LaunchReminder>>

    /** Adds the reminder, or replaces the one for the same launch. */
    suspend fun setReminder(reminder: LaunchReminder)

    suspend fun removeReminders(launchIds: Set<String>)
}

/** Wakes the app when the next reminder (or its re-check) is due, even after a reboot. */
interface LaunchReminderScheduler {

    /** Replaces any pending wake-up with one at [at]. */
    fun scheduleAt(at: Instant)

    fun cancel()
}

/** Posts "Falcon 9 | Starlink lifts off in 15 minutes". */
interface LaunchReminderNotifier {
    suspend fun notifyLiftoffSoon(launch: Launch)
}
