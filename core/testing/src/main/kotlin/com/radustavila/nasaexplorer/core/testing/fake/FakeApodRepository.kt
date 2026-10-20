package com.radustavila.nasaexplorer.core.testing.fake

import com.radustavila.nasaexplorer.core.common.error.AppError
import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.domain.repository.ApodBatchResult
import com.radustavila.nasaexplorer.core.domain.repository.ApodRepository
import com.radustavila.nasaexplorer.core.model.Apod
import com.radustavila.nasaexplorer.core.model.FavoriteItem
import com.radustavila.nasaexplorer.core.model.FavoriteSource
import com.radustavila.nasaexplorer.core.model.MediaType
import java.time.LocalDate
import kotlinx.coroutines.delay
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

    /** How long [refreshRecent] and a whole [fetchApods] batch take, in virtual time. */
    var refreshDelayMillis: Long = 0
    var batchDelayMillis: Long = 0
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

    /** Per-date overrides of [fetchApodResult], for runs that fetch several days. */
    val fetchApodResults: MutableMap<LocalDate, DataResult<Unit>> = mutableMapOf()

    /**
     * When set and no [fetchApodEntry] is given, every successful [fetchApod] caches an entry for
     * the requested date, as the real download would.
     */
    var cacheEveryFetchedDate: Boolean = false

    var refreshCallCount: Int = 0
        private set
    var loadOlderCallCount: Int = 0
        private set
    var fetchedDates: List<LocalDate> = emptyList()
        private set

    /** Every [fetchApods] call, as (dates, maxConcurrent). */
    var batchRequests: List<Pair<List<LocalDate>, Int>> = emptyList()
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

    /** Same contract as the real search: every word must appear, as a prefix, case-insensitively. */
    override fun searchCached(query: String): Flow<List<Apod>> {
        val words = query.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter(String::isNotEmpty)
        return entries.map { list ->
            if (words.isEmpty()) return@map emptyList()
            list.filter { apod ->
                val text = "${apod.title} ${apod.explanation}".lowercase()
                    .split(Regex("[^\\p{L}\\p{N}]+"))
                words.all { word -> text.any { it.startsWith(word) } }
            }
        }
    }

    override fun observeSameDayInEarlierYears(date: LocalDate): Flow<List<Apod>> =
        entries.map { list ->
            list.filter { apod ->
                apod.date.isBefore(date) &&
                    apod.date.month == date.month &&
                    apod.date.dayOfMonth == date.dayOfMonth
            }
        }

    override fun observeCachedCount(): Flow<Int> = entries.map { it.size }

    override fun observeOldestCachedDate(): Flow<LocalDate?> =
        entries.map { list -> list.minOfOrNull(Apod::date) }

    override suspend fun refreshRecent(): DataResult<Int> {
        refreshCallCount++
        delay(refreshDelayMillis)
        return refreshResult
    }

    override suspend fun fetchApod(date: LocalDate): DataResult<Unit> {
        fetchedDates = fetchedDates + date
        val result = fetchApodResults[date] ?: fetchApodResult
        if (result is DataResult.Success) {
            val downloaded = fetchApodEntry ?: if (cacheEveryFetchedDate) downloadedEntry(date) else null
            downloaded?.let { entry ->
                setEntries(stored.value + entry)
                outsideFeed.update { it + entry.date }
            }
        }
        return result
    }

    /**
     * Requests the dates one after another (the outcome the real, concurrent batch reaches when the
     * requests complete in order), stopping at the first retryable failure; downloads are cached
     * like [fetchApod]'s.
     */
    override suspend fun fetchApods(dates: List<LocalDate>, maxConcurrent: Int): ApodBatchResult {
        batchRequests = batchRequests + (dates to maxConcurrent)
        delay(batchDelayMillis)
        val cached = mutableSetOf<LocalDate>()
        val failed = mutableMapOf<LocalDate, AppError>()
        for (date in dates) {
            when (val result = fetchApod(date)) {
                is DataResult.Success -> cached += date
                is DataResult.Failure -> {
                    failed[date] = result.error
                    if (result.error.isRetryable) break
                }
            }
        }
        return ApodBatchResult(cached, failed)
    }

    private fun downloadedEntry(date: LocalDate) = Apod(
        date = date,
        title = "Downloaded $date",
        explanation = "",
        mediaType = MediaType.IMAGE,
        url = "https://example.com/$date.jpg",
    )

    override suspend fun loadOlderWindow(): DataResult<Int> {
        loadOlderCallCount++
        return loadOlderResult
    }
}
