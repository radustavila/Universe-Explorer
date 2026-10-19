package com.radustavila.nasaexplorer.core.domain.model

import com.radustavila.nasaexplorer.core.model.SyncState
import java.time.Instant
import java.time.LocalDate

/**
 * Aggregated view of the moving parts of the offline-first pipeline, surfaced by the debug screen.
 *
 * @property favoriteCount favourites of every source.
 * @property sources bookkeeping of every source that has synced at least once.
 */
data class DebugInfo(
    val isOnline: Boolean,
    val lastSuccessfulSync: Instant?,
    val cachedApodCount: Int,
    val favoriteCount: Int,
    val oldestCachedDate: LocalDate?,
    val oldestRequestedDate: LocalDate?,
    val syncState: SyncState,
    val sources: List<SourceSyncStatus> = emptyList(),
)
