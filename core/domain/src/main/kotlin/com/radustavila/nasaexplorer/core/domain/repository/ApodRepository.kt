package com.radustavila.nasaexplorer.core.domain.repository

import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.model.Apod
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

/**
 * The single contract the domain layer knows about for APOD data.
 *
 * Reads are [Flow]s backed by the local database (the single source of truth); writes and refreshes
 * are one-shot operations returning a [DataResult]. Nothing here mentions HTTP, Room or WorkManager.
 *
 * Every emitted [Apod] carries [Apod.isFavorite] from the favourites store
 * ([FavoritesRepository]); favourites themselves are written there, not here.
 */
interface ApodRepository {

    /** Cached feed, newest first. Emits whenever the local cache changes. */
    fun observeFeed(): Flow<List<Apod>>

    /** Most recently published entry in the cache, or `null` when the cache is empty. */
    fun observeLatestApod(): Flow<Apod?>

    /** Single entry, or `null` while it is not cached yet. */
    fun observeApod(date: LocalDate): Flow<Apod?>

    fun observeCachedCount(): Flow<Int>

    /** Oldest publication date currently cached, or `null` when the cache is empty. */
    fun observeOldestCachedDate(): Flow<LocalDate?>

    /**
     * Fetches the most recent window of entries and merges it into the cache.
     *
     * @return the number of entries written.
     */
    suspend fun refreshRecent(): DataResult<Int>

    /**
     * Fetches one specific day and merges it into the cache.
     *
     * The feed only caches contiguous windows walking back from today, so an arbitrary archive date
     * picked from the calendar is usually absent; this fills that single gap without paging through
     * everything in between.
     */
    suspend fun fetchApod(date: LocalDate): DataResult<Unit>

    /**
     * Fetches the next window of *older* entries (date-range pagination) and merges it into the
     * cache.
     *
     * @return the number of entries written; `0` means the archive start was reached.
     */
    suspend fun loadOlderWindow(): DataResult<Int>
}
