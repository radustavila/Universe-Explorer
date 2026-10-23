package com.radustavila.nasaexplorer.core.domain.usecase

import com.radustavila.nasaexplorer.core.common.error.AppError
import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.common.time.TimeProvider
import com.radustavila.nasaexplorer.core.domain.connectivity.NetworkMonitor
import com.radustavila.nasaexplorer.core.domain.launch.LaunchReminder
import com.radustavila.nasaexplorer.core.domain.launch.LaunchReminderNotifier
import com.radustavila.nasaexplorer.core.domain.launch.LaunchReminderRepository
import com.radustavila.nasaexplorer.core.domain.launch.LaunchReminderScheduler
import com.radustavila.nasaexplorer.core.domain.repository.LaunchRepository
import com.radustavila.nasaexplorer.core.domain.repository.SyncMetadataRepository
import com.radustavila.nasaexplorer.core.domain.sync.SourceRefreshResult
import com.radustavila.nasaexplorer.core.domain.sync.succeeded
import com.radustavila.nasaexplorer.core.model.Launch
import com.radustavila.nasaexplorer.core.model.LaunchStatus
import com.radustavila.nasaexplorer.core.model.LaunchTimeline
import com.radustavila.nasaexplorer.core.model.SyncSource
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class ObservePastLaunchesUseCase @Inject constructor(
    private val launchRepository: LaunchRepository,
) {
    operator fun invoke(): Flow<List<Launch>> = launchRepository.observePastLaunches()
}

class ObserveLaunchUseCase @Inject constructor(
    private val launchRepository: LaunchRepository,
) {
    operator fun invoke(id: String): Flow<Launch?> = launchRepository.observeLaunch(id)
}

/**
 * Refreshes the past launches when the Launches screen opens, at most once per
 * [MIN_REFRESH_INTERVAL]: what flew changes a few times a day, and every call comes out of the
 * 15-an-hour budget the card and the crew list share.
 */
class RefreshPastLaunchesUseCase @Inject constructor(
    private val launchRepository: LaunchRepository,
    private val networkMonitor: NetworkMonitor,
    private val refreshSource: RefreshSourceUseCase,
    private val rescheduleReminders: RescheduleLaunchRemindersUseCase,
) {
    suspend operator fun invoke(): SourceRefreshResult<Int> {
        if (!networkMonitor.isOnline.first()) {
            return SourceRefreshResult.Completed(DataResult.Failure(AppError.NoConnection))
        }
        return refreshSource(SyncSource.LAUNCHES_PAST, minInterval = MIN_REFRESH_INTERVAL) {
            launchRepository.refreshPastLaunches()
        }.also { outcome -> if (outcome.succeeded()) rescheduleReminders() }
    }

    companion object {
        val MIN_REFRESH_INTERVAL: Duration = Duration.ofHours(6)
    }
}

/** The ids of the launches with a reminder on. */
class ObserveLaunchRemindersUseCase @Inject constructor(
    private val repository: LaunchReminderRepository,
) {
    operator fun invoke(): Flow<Set<String>> = repository.reminders.map { it.keys }
}

/** Rings or silences one launch's bell and re-arms the wake-up. */
class SetLaunchReminderUseCase @Inject constructor(
    private val repository: LaunchReminderRepository,
    private val rescheduleReminders: RescheduleLaunchRemindersUseCase,
) {
    suspend operator fun invoke(launchId: String, enabled: Boolean) {
        val current = repository.reminders.first()
        if (enabled && launchId !in current) {
            repository.setReminder(LaunchReminder(launchId))
        } else if (!enabled && launchId in current) {
            repository.removeReminders(setOf(launchId))
        }
        rescheduleReminders()
    }
}

/**
 * Arms the one wake-up for the soonest due step of any reminder (the re-check [RECHECK_BEFORE] NET,
 * else the notification [REMIND_BEFORE] NET), or cancels it when there is none. Runs after every
 * refresh, so a NET that moved moves the wake-up with it.
 *
 * A reminder waits without a wake-up while its launch has no firm time (TBD, a hold, a month-only
 * NET); the refresh that firms it up arms it. Reminders of launches that are over (flown, failed,
 * or gone from the cache) are dropped.
 */
class RescheduleLaunchRemindersUseCase @Inject constructor(
    private val launchRepository: LaunchRepository,
    private val reminderRepository: LaunchReminderRepository,
    private val scheduler: LaunchReminderScheduler,
    private val timeProvider: TimeProvider,
) {
    suspend operator fun invoke() {
        val reminders = reminderRepository.reminders.first().values
        if (reminders.isEmpty()) {
            scheduler.cancel()
            return
        }
        val now = timeProvider.now()
        val launches = reminders.associateWith { launchRepository.observeLaunch(it.launchId).first() }
        // Right after a wiped cache nothing is known: keep every reminder until the next refresh.
        val cacheIsEmpty = launches.values.all { it == null } &&
            launchRepository.observeUpcomingLaunches().first().isEmpty()
        val over = launches.filter { (_, launch) -> if (launch == null) !cacheIsEmpty else launch.isOverAt(now) }.keys
        if (over.isNotEmpty()) reminderRepository.removeReminders(over.mapTo(mutableSetOf()) { it.launchId })

        val next = launches
            .filterKeys { it !in over }
            .mapNotNull { (reminder, launch) -> launch?.let { reminder.nextStepAt(it, now) } }
            .minOrNull()
        if (next == null) scheduler.cancel() else scheduler.scheduleAt(next)
    }
}

/**
 * Restores the wake-up at app start when there are reminders (they come back with a backup, the
 * WorkManager job does not). Without reminders it touches nothing, so a cold start stays light.
 */
class RestoreLaunchRemindersUseCase @Inject constructor(
    private val reminderRepository: LaunchReminderRepository,
    private val rescheduleReminders: RescheduleLaunchRemindersUseCase,
) {
    suspend operator fun invoke() {
        if (reminderRepository.reminders.first().isNotEmpty()) rescheduleReminders()
    }
}

/**
 * What the reminder's wake-up runs, from the cache, so it works offline:
 *
 * 1. About [RECHECK_BEFORE] NET it re-checks each reminded launch with one `launches/{id}/` call,
 *    unless the upcoming list was refreshed in the last [FRESH_ENOUGH] or the phone is offline.
 * 2. From [REMIND_BEFORE] NET until lift-off it posts the notification, once per NET, and only while
 *    the launch is Go or TBC: a hold or a scrub silences it, and the slipped NET re-arms it.
 *
 * @return the launches a notification was posted for.
 */
class RemindOfLaunchesUseCase @Inject constructor(
    private val launchRepository: LaunchRepository,
    private val reminderRepository: LaunchReminderRepository,
    private val notifier: LaunchReminderNotifier,
    private val networkMonitor: NetworkMonitor,
    private val syncMetadataRepository: SyncMetadataRepository,
    private val rescheduleReminders: RescheduleLaunchRemindersUseCase,
    private val timeProvider: TimeProvider,
) {
    suspend operator fun invoke(): List<Launch> {
        recheckDueLaunches()
        val notified = notifyDueLaunches()
        rescheduleReminders()
        return notified
    }

    private suspend fun recheckDueLaunches() {
        val now = timeProvider.now()
        val due = reminderRepository.reminders.first().values.mapNotNull { reminder ->
            launchRepository.observeLaunch(reminder.launchId).first()
                ?.takeIf { reminder.isRecheckDue(it, now) }
                ?.let { reminder to it }
        }
        if (due.isEmpty()) return
        val canCall = networkMonitor.isOnline.first() && !upcomingListIsFresh(now)
        due.forEach { (reminder, launch) ->
            val checked = if (canCall) {
                (launchRepository.refreshLaunch(launch.id) as? DataResult.Success)?.data ?: launch
            } else {
                launch
            }
            // Recorded against the NET it has now, so a slipped NET is not re-checked again at once.
            reminderRepository.setReminder(reminder.copy(checkedNet = checked.net))
        }
    }

    private suspend fun notifyDueLaunches(): List<Launch> {
        val now = timeProvider.now()
        return reminderRepository.reminders.first().values.mapNotNull { reminder ->
            val launch = launchRepository.observeLaunch(reminder.launchId).first()
                ?.takeIf { reminder.isNotificationDue(it, now) }
                ?: return@mapNotNull null
            notifier.notifyLiftoffSoon(launch)
            reminderRepository.setReminder(reminder.copy(notifiedNet = launch.net))
            launch
        }
    }

    private suspend fun upcomingListIsFresh(now: Instant): Boolean {
        val lastSuccess = syncMetadataRepository.observeSourceStatus(SyncSource.LAUNCHES).first().lastSuccessAt
        return lastSuccess != null && Duration.between(lastSuccess, now) < FRESH_ENOUGH
    }

    companion object {
        /** The list refreshed this recently already carries what a re-check would fetch. */
        val FRESH_ENOUGH: Duration = Duration.ofMinutes(30)
    }
}

/** How long before NET the notification is posted. */
val REMIND_BEFORE: Duration = Duration.ofMinutes(15)

/** How long before NET a reminded launch is fetched again. */
val RECHECK_BEFORE: Duration = Duration.ofMinutes(60)

/** WorkManager may run a delayed job a little early. */
private val EARLY_WAKE_UP_SLACK: Duration = Duration.ofMinutes(2)

/** A launch this long past its NET without a newer NET is over for reminders. */
private val OVER_AFTER: Duration = Duration.ofDays(1)

private val FINAL_STATUSES = setOf(
    LaunchStatus.SUCCESS,
    LaunchStatus.FAILURE,
    LaunchStatus.PARTIAL_FAILURE,
    LaunchStatus.IN_FLIGHT,
)

/** Upcoming, Go or TBC, and timed to the hour or better: worth counting down to. */
private val Launch.isRemindable: Boolean
    get() = timeline == LaunchTimeline.UPCOMING && status.isFirm && netPrecision.isExact

/**
 * Over: it flew (or failed), or its NET is a day gone with no update. A scrub on the pad moves the
 * NET, which keeps the reminder alive for the new date.
 */
private fun Launch.isOverAt(now: Instant): Boolean = status in FINAL_STATUSES || net.plus(OVER_AFTER).isBefore(now)

/** When this reminder next needs a wake-up for [launch], or null when it needs none now. */
internal fun LaunchReminder.nextStepAt(launch: Launch, now: Instant): Instant? {
    if (!launch.isRemindable || notifiedNet == launch.net || !now.isBefore(launch.net)) return null
    val notifyAt = launch.net.minus(REMIND_BEFORE)
    val checkAt = launch.net.minus(RECHECK_BEFORE)
    val step = if (checkedNet != launch.net && now.isBefore(notifyAt)) checkAt else notifyAt
    return maxOf(now, step)
}

private fun LaunchReminder.isRecheckDue(launch: Launch, now: Instant): Boolean =
    launch.isRemindable &&
        notifiedNet != launch.net &&
        checkedNet != launch.net &&
        !launch.net.minus(RECHECK_BEFORE).isAfter(now.plus(EARLY_WAKE_UP_SLACK)) &&
        now.isBefore(launch.net.minus(REMIND_BEFORE))

private fun LaunchReminder.isNotificationDue(launch: Launch, now: Instant): Boolean =
    launch.isRemindable &&
        notifiedNet != launch.net &&
        !launch.net.minus(REMIND_BEFORE).isAfter(now.plus(EARLY_WAKE_UP_SLACK)) &&
        now.isBefore(launch.net)
