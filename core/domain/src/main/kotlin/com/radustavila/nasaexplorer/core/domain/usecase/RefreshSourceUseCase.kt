package com.radustavila.nasaexplorer.core.domain.usecase

import com.radustavila.nasaexplorer.core.common.error.AppError
import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.common.time.TimeProvider
import com.radustavila.nasaexplorer.core.domain.repository.SyncMetadataRepository
import com.radustavila.nasaexplorer.core.domain.sync.SourceRefreshResult
import com.radustavila.nasaexplorer.core.domain.sync.SyncSourceLocks
import com.radustavila.nasaexplorer.core.model.SyncSource
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/**
 * The one way a data source is refreshed: skips the call while the source is not due, runs it
 * otherwise, and records the outcome per [SyncSource] (shown on the Debug screen).
 *
 * ```
 * refreshSource(SyncSource.LAUNCHES, minInterval = Duration.ofHours(1)) {
 *     launchRepository.refreshUpcoming()
 * }
 * ```
 *
 * After a success the source is not due again for [minInterval]. After a rate-limited failure it is
 * not due until the server's `Retry-After` (or [minInterval] when the server gave none). Any other
 * failure leaves it due, so the next sync tries again.
 *
 * Refreshes of one source are serialised through [SyncSourceLocks]: a caller that waited for another
 * refresh of the same source checks again whether it is due, so the source is not fetched twice.
 */
class RefreshSourceUseCase @Inject constructor(
    private val syncMetadataRepository: SyncMetadataRepository,
    private val timeProvider: TimeProvider,
    private val locks: SyncSourceLocks = SyncSourceLocks(),
) {
    /**
     * @param force ignore the source's interval (e.g. pull-to-refresh). A host that is cooling
     * down still refuses the call at the HTTP layer.
     */
    suspend operator fun <T> invoke(
        source: SyncSource,
        minInterval: Duration = Duration.ZERO,
        force: Boolean = false,
        refresh: suspend () -> DataResult<T>,
    ): SourceRefreshResult<T> {
        // Not due: skip without queueing behind a refresh that is running.
        if (!force) notDueUntil(source)?.let { return SourceRefreshResult.Skipped(it) }

        return locks.withLock(source) {
            // Whoever held the lock may just have refreshed the source.
            if (!force) notDueUntil(source)?.let { return@withLock SourceRefreshResult.Skipped(it) }
            refreshAndRecord(source, minInterval, refresh)
        }
    }

    private suspend fun notDueUntil(source: SyncSource): Instant? {
        val nextAllowed = syncMetadataRepository.observeSourceStatus(source).first().nextAllowedRefreshAt
        return nextAllowed?.takeIf { timeProvider.now().isBefore(it) }
    }

    private suspend fun <T> refreshAndRecord(
        source: SyncSource,
        minInterval: Duration,
        refresh: suspend () -> DataResult<T>,
    ): SourceRefreshResult<T> {
        val result = refresh()
        val finishedAt = timeProvider.now()
        when (result) {
            is DataResult.Success -> syncMetadataRepository.recordSyncSuccess(
                source = source,
                at = finishedAt,
                nextAllowedRefreshAt = finishedAt.plusOrNull(minInterval),
            )

            is DataResult.Failure -> syncMetadataRepository.recordSyncFailure(
                source = source,
                at = finishedAt,
                error = result.error,
                nextAllowedRefreshAt = (result.error as? AppError.RateLimited)?.let { error ->
                    error.retryAfterSeconds?.let { finishedAt.plusSeconds(it) }
                        ?: finishedAt.plusOrNull(minInterval)
                },
            )
        }
        return SourceRefreshResult.Completed(result)
    }

    private fun Instant.plusOrNull(interval: Duration): Instant? =
        if (interval.isZero || interval.isNegative) null else plus(interval)
}
