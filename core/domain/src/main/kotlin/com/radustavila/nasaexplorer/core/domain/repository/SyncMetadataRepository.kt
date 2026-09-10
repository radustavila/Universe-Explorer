package com.radustavila.nasaexplorer.core.domain.repository

import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

/**
 * Bookkeeping that survives process death: when the cache was last refreshed successfully, and how
 * far back into the archive the user has paged.
 */
interface SyncMetadataRepository {
    val lastSuccessfulSync: Flow<Instant?>
    val oldestRequestedDate: Flow<LocalDate?>

    suspend fun setLastSuccessfulSync(instant: Instant)
    suspend fun setOldestRequestedDate(date: LocalDate)
}
