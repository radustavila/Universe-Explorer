package com.radustavila.nasaexplorer.core.testing.fake

import com.radustavila.nasaexplorer.core.common.error.AppError
import com.radustavila.nasaexplorer.core.domain.model.SourceSyncStatus
import com.radustavila.nasaexplorer.core.domain.repository.SyncMetadataRepository
import com.radustavila.nasaexplorer.core.model.SyncSource
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

class FakeSyncMetadataRepository(
    initialLastSync: Instant? = null,
    initialOldestRequested: LocalDate? = null,
    initialLastAnnounced: LocalDate? = null,
    initialLastWallpaper: LocalDate? = null,
) : SyncMetadataRepository {

    private val statuses = MutableStateFlow(
        initialLastSync?.let { mapOf(SyncSource.APOD to SourceSyncStatus(SyncSource.APOD, lastSuccessAt = it)) }
            .orEmpty(),
    )
    private val oldestRequested = MutableStateFlow(initialOldestRequested)
    private val lastAnnounced = MutableStateFlow(initialLastAnnounced)
    private val lastWallpaper = MutableStateFlow(initialLastWallpaper)
    private val datesWithoutEntry = MutableStateFlow<Set<LocalDate>>(emptySet())

    override val sourceStatuses: Flow<List<SourceSyncStatus>> = statuses.map { map ->
        SyncSource.entries.mapNotNull { map[it] }.filter { it.hasRecord }
    }

    override fun observeSourceStatus(source: SyncSource): Flow<SourceSyncStatus> =
        statuses.map { map -> map[source] ?: SourceSyncStatus(source) }

    override val oldestRequestedDate: Flow<LocalDate?> = oldestRequested
    override val lastAnnouncedApodDate: Flow<LocalDate?> = lastAnnounced
    override val lastWallpaperApodDate: Flow<LocalDate?> = lastWallpaper
    override val apodDatesWithoutEntry: Flow<Set<LocalDate>> = datesWithoutEntry

    /** APOD's last successful sync, the one the existing tests care about. */
    val currentLastSync: Instant? get() = statusOf(SyncSource.APOD).lastSuccessAt
    val currentOldestRequested: LocalDate? get() = oldestRequested.value
    val currentLastAnnounced: LocalDate? get() = lastAnnounced.value
    val currentLastWallpaper: LocalDate? get() = lastWallpaper.value
    val currentDatesWithoutEntry: Set<LocalDate> get() = datesWithoutEntry.value

    fun statusOf(source: SyncSource): SourceSyncStatus = statuses.value[source] ?: SourceSyncStatus(source)

    override suspend fun recordSyncSuccess(source: SyncSource, at: Instant, nextAllowedRefreshAt: Instant?) {
        statuses.update { map ->
            map + (source to statusOf(source).copy(lastSuccessAt = at, nextAllowedRefreshAt = nextAllowedRefreshAt))
        }
    }

    override suspend fun recordSyncFailure(
        source: SyncSource,
        at: Instant,
        error: AppError,
        nextAllowedRefreshAt: Instant?,
    ) {
        statuses.update { map ->
            map + (
                source to statusOf(source).copy(
                    lastFailureAt = at,
                    lastErrorDescription = error.toString(),
                    nextAllowedRefreshAt = nextAllowedRefreshAt,
                )
                )
        }
    }

    override suspend fun allowRefreshNow() {
        statuses.update { map -> map.mapValues { (_, status) -> status.copy(nextAllowedRefreshAt = null) } }
    }

    override suspend fun setOldestRequestedDate(date: LocalDate) {
        oldestRequested.value = date
    }

    override suspend fun setLastAnnouncedApodDate(date: LocalDate) {
        lastAnnounced.value = date
    }

    override suspend fun setLastWallpaperApodDate(date: LocalDate?) {
        lastWallpaper.value = date
    }

    override suspend fun addApodDatesWithoutEntry(dates: Set<LocalDate>) {
        datesWithoutEntry.update { it + dates }
    }
}
