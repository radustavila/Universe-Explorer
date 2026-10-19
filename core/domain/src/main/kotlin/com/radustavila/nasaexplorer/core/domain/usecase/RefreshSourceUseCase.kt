package com.radustavila.nasaexplorer.core.domain.usecase

import com.radustavila.nasaexplorer.core.common.error.AppError
import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.common.time.TimeProvider
import com.radustavila.nasaexplorer.core.domain.repository.SyncMetadataRepository
import com.radustavila.nasaexplorer.core.domain.sync.SourceRefreshResult
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
 */
class RefreshSourceUseCase @Inject constructor(
    private val syncMetadataRepository: SyncMetadataRepository,
    private val timeProvider: TimeProvider,
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
        if (!force) {
            val nextAllowed = syncMetadataRepository.observeSourceStatus(source).first().nextAllowedRefreshAt
            if (nextAllowed != null && timeProvider.now().isBefore(nextAllowed)) {
                return SourceRefreshResult.Skipped(nextAllowed)
            }
        }

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
