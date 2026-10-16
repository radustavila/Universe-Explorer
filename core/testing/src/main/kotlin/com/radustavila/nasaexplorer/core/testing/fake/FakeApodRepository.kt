package com.radustavila.nasaexplorer.core.testing.fake

import com.radustavila.nasaexplorer.core.common.error.AppError
import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.domain.repository.ApodRepository
import com.radustavila.nasaexplorer.core.model.Apod
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * In-memory [ApodRepository] that behaves like the real one where it matters: reads are flows over
 * the stored entries, and favourite toggles are visible on the next emission.
 */
class FakeApodRepository : ApodRepository {

    private val entries = MutableStateFlow<List<Apod>>(emptyList())

    /** Dates cached on their own by [fetchApod]; like the real cache, they stay out of the feed. */
    private val outsideFeed = MutableStateFlow<Set<LocalDate>>(emptySet())

    var refreshResult: DataResult<Int> = DataResult.Success(0)
    var loadOlderResult: DataResult<Int> = DataResult.Success(0)
    var setFavoriteResult: DataResult<Unit>? = null
    var fetchApodResult: DataResult<Unit> = DataResult.Success(Unit)

    /** Entry handed back by [fetchApod] on success, as a download would populate the cache. */
    var fetchApodEntry: Apod? = null

    var refreshCallCount: Int = 0
        private set
    var loadOlderCallCount: Int = 0
        private set
    var fetchedDates: List<LocalDate> = emptyList()
        private set

    fun setEntries(apods: List<Apod>) {
        entries.value = apods.sortedByDescending(Apod::date)
        // Anything the test seeds directly stands in for a synced window.
        outsideFeed.update { excluded -> excluded - apods.map(Apod::date).toSet() }
    }

    fun emit(apods: List<Apod>) = setEntries(apods)

    override fun observeFeed(): Flow<List<Apod>> =
        combine(entries, outsideFeed) { list, excluded ->
            list.filterNot { apod -> apod.date in excluded }
        }

    override fun observeLatestApod(): Flow<Apod?> = entries.map { list -> list.firstOrNull() }

    override fun observeApod(date: LocalDate): Flow<Apod?> =
        entries.map { list -> list.firstOrNull { it.date == date } }

    override fun observeFavorites(): Flow<List<Apod>> =
        entries.map { list -> list.filter(Apod::isFavorite) }

    override fun observeCachedCount(): Flow<Int> = entries.map { it.size }

    override fun observeFavoritesCount(): Flow<Int> = entries.map { list -> list.count(Apod::isFavorite) }

    override fun observeOldestCachedDate(): Flow<LocalDate?> =
        entries.map { list -> list.minOfOrNull(Apod::date) }

    override suspend fun setFavorite(date: LocalDate, isFavorite: Boolean): DataResult<Unit> {
        setFavoriteResult?.let { forced -> return forced }

        val known = entries.value.any { it.date == date }
        if (!known) return DataResult.Failure(AppError.Database("No cached APOD for $date"))

        entries.update { list ->
            list.map { apod -> if (apod.date == date) apod.copy(isFavorite = isFavorite) else apod }
        }
        return DataResult.Success(Unit)
    }

    override suspend fun refreshRecent(): DataResult<Int> {
        refreshCallCount++
        return refreshResult
    }

    override suspend fun fetchApod(date: LocalDate): DataResult<Unit> {
        fetchedDates = fetchedDates + date
        if (fetchApodResult is DataResult.Success) {
            fetchApodEntry?.let { entry ->
                setEntries(entries.value + entry)
                outsideFeed.update { it + entry.date }
            }
        }
        return fetchApodResult
    }

    override suspend fun loadOlderWindow(): DataResult<Int> {
        loadOlderCallCount++
        return loadOlderResult
    }
}
