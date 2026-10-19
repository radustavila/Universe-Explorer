package com.radustavila.nasaexplorer.core.testing.fake

import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.domain.repository.ApodRepository
import com.radustavila.nasaexplorer.core.model.Apod
import com.radustavila.nasaexplorer.core.model.FavoriteItem
import com.radustavila.nasaexplorer.core.model.FavoriteSource
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * In-memory [ApodRepository] that behaves like the real one where it matters: reads are flows over
 * the stored entries, and [Apod.isFavorite] comes from [favorites], so a favourite saved through it
 * is visible on the next emission.
 *
 * Entries seeded with `isFavorite = true` are saved to [favorites] as well.
 */
class FakeApodRepository(
    val favorites: FakeFavoritesRepository = FakeFavoritesRepository(),
) : ApodRepository {

    private val stored = MutableStateFlow<List<Apod>>(emptyList())

    private val entries: Flow<List<Apod>> =
        combine(stored, favorites.observeFavoriteIds(FavoriteSource.APOD)) { list, favoriteIds ->
            list.map { apod -> apod.copy(isFavorite = apod.date.toString() in favoriteIds) }
        }

    /** Dates cached on their own by [fetchApod]; like the real cache, they stay out of the feed. */
    private val outsideFeed = MutableStateFlow<Set<LocalDate>>(emptySet())

    var refreshResult: DataResult<Int> = DataResult.Success(0)
    var loadOlderResult: DataResult<Int> = DataResult.Success(0)

    /** Forces the outcome of favourite writes, which go through [favorites]. */
    var setFavoriteResult: DataResult<Unit>?
        get() = favorites.writeResult
        set(value) {
            favorites.writeResult = value
        }
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
        stored.value = apods.sortedByDescending(Apod::date)
        apods.filter(Apod::isFavorite).forEach { apod ->
            favorites.seed(
                FavoriteItem(FavoriteSource.APOD, apod.date.toString(), apod.title, apod.previewImageUrl),
            )
        }
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

    override fun observeCachedCount(): Flow<Int> = entries.map { it.size }

    override fun observeOldestCachedDate(): Flow<LocalDate?> =
        entries.map { list -> list.minOfOrNull(Apod::date) }

    override suspend fun refreshRecent(): DataResult<Int> {
        refreshCallCount++
        return refreshResult
    }

    override suspend fun fetchApod(date: LocalDate): DataResult<Unit> {
        fetchedDates = fetchedDates + date
        if (fetchApodResult is DataResult.Success) {
            fetchApodEntry?.let { entry ->
                setEntries(stored.value + entry)
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
