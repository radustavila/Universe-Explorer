package com.radustavila.nasaexplorer.core.domain.repository

import com.radustavila.nasaexplorer.core.common.error.AppError
import com.radustavila.nasaexplorer.core.domain.model.SourceSyncStatus
import com.radustavila.nasaexplorer.core.model.SyncSource
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

/**
 * Bookkeeping that survives process death.
 *
 * Per source (see [SyncSource]): when it last synced successfully, its last error, and when it may
 * be called again. Prefer [com.radustavila.nasaexplorer.core.domain.usecase.RefreshSourceUseCase],
 * which reads and writes these for you, over calling the `record*` functions directly.
 *
 * APOD-specific: how far back into the archive the user has paged, and which entry was last
 * announced outside the app.
 */
interface SyncMetadataRepository {

    /** Every source with at least one recorded attempt, in [SyncSource] declaration order. */
    val sourceStatuses: Flow<List<SourceSyncStatus>>

    /** Always emits; a source never synced has an empty [SourceSyncStatus]. */
    fun observeSourceStatus(source: SyncSource): Flow<SourceSyncStatus>

    suspend fun recordSyncSuccess(
        source: SyncSource,
        at: Instant,
        nextAllowedRefreshAt: Instant? = null,
    )

    suspend fun recordSyncFailure(
        source: SyncSource,
        at: Instant,
        error: AppError,
        nextAllowedRefreshAt: Instant? = null,
    )

    val oldestRequestedDate: Flow<LocalDate?>
    val lastAnnouncedApodDate: Flow<LocalDate?>

    suspend fun setOldestRequestedDate(date: LocalDate)
    suspend fun setLastAnnouncedApodDate(date: LocalDate)
}
