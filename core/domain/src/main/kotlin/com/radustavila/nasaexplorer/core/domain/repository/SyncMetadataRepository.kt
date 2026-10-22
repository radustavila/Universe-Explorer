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
 * APOD-specific: how far back into the archive the user has paged, which entry was last announced
 * outside the app, which one was last set as the wallpaper, and which past days turned out to have
 * no entry when "On this day" asked for them.
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

    /**
     * Forgets every source's next allowed refresh time, so each one refreshes the next time it is
     * asked: after the user cleared the cached data. Servers' own cooldowns still apply, at the
     * HTTP layer.
     */
    suspend fun allowRefreshNow()

    val oldestRequestedDate: Flow<LocalDate?>
    val lastAnnouncedApodDate: Flow<LocalDate?>
    val lastWallpaperApodDate: Flow<LocalDate?>

    suspend fun setOldestRequestedDate(date: LocalDate)
    suspend fun setLastAnnouncedApodDate(date: LocalDate)

    /** `null` forgets it, so the next run applies the current entry again. */
    suspend fun setLastWallpaperApodDate(date: LocalDate?)

    /**
     * Days NASA answered without an entry (the archive has gaps), so "On this day" stops asking for
     * them. Grows by a few dates per day of the year at most.
     */
    val apodDatesWithoutEntry: Flow<Set<LocalDate>>

    suspend fun addApodDatesWithoutEntry(dates: Set<LocalDate>)
}
