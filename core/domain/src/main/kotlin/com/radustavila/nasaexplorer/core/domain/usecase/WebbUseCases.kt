package com.radustavila.nasaexplorer.core.domain.usecase

import com.radustavila.nasaexplorer.core.common.time.TimeProvider
import com.radustavila.nasaexplorer.core.domain.repository.WebbScheduleRepository
import com.radustavila.nasaexplorer.core.domain.sync.SourceRefreshResult
import com.radustavila.nasaexplorer.core.model.SyncSource
import com.radustavila.nasaexplorer.core.model.WebbNow
import com.radustavila.nasaexplorer.core.model.WebbSchedule
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.transformLatest

/**
 * Refreshes Webb's schedule at most once per [MIN_REFRESH_INTERVAL], unless [force]d by the user:
 * STScI's index page and the newest weekly file, which the terms ask to fetch once a day. Runs from
 * the daily job and when the card or the schedule screen opens (skipped while fresh).
 */
class RefreshWebbScheduleUseCase @Inject constructor(
    private val repository: WebbScheduleRepository,
    private val refreshSource: RefreshSourceUseCase,
) {
    suspend operator fun invoke(force: Boolean = false): SourceRefreshResult<Int> =
        refreshSource(SyncSource.JWST_SCHEDULE, minInterval = MIN_REFRESH_INTERVAL, force = force) {
            repository.refresh()
        }

    companion object {
        /** A little under a day, so the daily job is not skipped by a refresh made the day before. */
        val MIN_REFRESH_INTERVAL: Duration = Duration.ofHours(20)
    }
}

/** The stored schedule, every visit of the week, for the timeline. Null before the first download. */
class ObserveWebbScheduleUseCase @Inject constructor(
    private val repository: WebbScheduleRepository,
) {
    operator fun invoke(): Flow<WebbSchedule?> = repository.observeSchedule()
}

/**
 * What Webb is scheduled to do now and next, re-read from the stored schedule every
 * [RECOMPUTE_INTERVAL] and whenever a new schedule is stored. Null before the first download or for
 * a schedule without visits.
 */
class ObserveWebbNowUseCase @Inject constructor(
    private val repository: WebbScheduleRepository,
    private val timeProvider: TimeProvider,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(): Flow<WebbNow?> = repository.observeSchedule()
        .transformLatest { schedule ->
            if (schedule == null || schedule.visits.isEmpty()) {
                emit(null)
                return@transformLatest
            }
            while (true) {
                emit(schedule.statusAt(timeProvider.now()))
                delay(RECOMPUTE_INTERVAL.toMillis())
            }
        }
        .distinctUntilChanged()

    companion object {
        /** The card shows minutes, so once a minute is enough. */
        val RECOMPUTE_INTERVAL: Duration = Duration.ofMinutes(1)
    }
}

/**
 * The schedule at [now]: the visit on, or the visit that ended last and the gap until the next,
 * plus up to [WebbNow.UP_NEXT] following visits, repeats of the visit before folded in. Visits
 * that overlap (a revised week's plan) are read in start order; the latest one that has started
 * wins.
 */
fun WebbSchedule.statusAt(now: Instant): WebbNow? {
    val sorted = visits.sortedBy { it.start }
    val first = sorted.firstOrNull() ?: return null
    val current = sorted.lastOrNull { it.isOnAt(now) }
    val previous = if (current == null) sorted.filter { !it.end.isAfter(now) }.maxByOrNull { it.end } else null
    val after = current?.start ?: now
    val following = sorted.filter { it.start.isAfter(after) && it != current }
    val upNext = following
        .filterIndexed { index, visit ->
            val before = if (index == 0) current else following[index - 1]
            before == null || !visit.repeats(before)
        }
        .take(WebbNow.UP_NEXT)
    return WebbNow(
        now = now,
        current = current,
        previous = previous,
        upNext = upNext,
        fetchedAt = fetchedAt,
        isStale = Duration.between(first.start, now) > WebbNow.STALE_AFTER,
        scheduleStart = first.start,
    )
}
