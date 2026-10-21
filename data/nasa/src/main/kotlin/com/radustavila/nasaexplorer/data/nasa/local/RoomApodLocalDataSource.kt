package com.radustavila.nasaexplorer.data.nasa.local

import com.radustavila.nasaexplorer.core.common.dispatchers.AppDispatcher
import com.radustavila.nasaexplorer.core.common.dispatchers.Dispatcher
import com.radustavila.nasaexplorer.core.common.error.AppError
import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.database.dao.ApodDao
import com.radustavila.nasaexplorer.core.database.entity.ApodEntity
import com.radustavila.nasaexplorer.core.model.Apod
import com.radustavila.nasaexplorer.data.nasa.mapper.toDomain
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

@Singleton
class RoomApodLocalDataSource @Inject constructor(
    private val apodDao: ApodDao,
    @Dispatcher(AppDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
) : ApodLocalDataSource {

    // Room re-runs a query on every write to its table, including writes outside the feed (a day
    // cached for "On this day"); unchanged rows are not mapped and emitted again.
    override fun observeFeed(): Flow<List<Apod>> =
        apodDao.observeFeed().distinctUntilChanged().map { entities -> entities.toDomain() }.flowOn(ioDispatcher)

    override fun observeLatest(): Flow<Apod?> =
        apodDao.observeLatest().distinctUntilChanged().map { entity -> entity?.toDomain() }.flowOn(ioDispatcher)

    override fun observeApod(date: LocalDate): Flow<Apod?> =
        apodDao.observeApod(date.toString())
            .map { entity -> entity?.toDomain() }
            .flowOn(ioDispatcher)

    override fun search(query: String): Flow<List<Apod>> {
        val match = ftsMatchExpression(query) ?: return flowOf(emptyList())
        return apodDao.observeSearch(match = match, limit = MAX_SEARCH_RESULTS)
            .map { entities -> entities.toDomain() }
            .flowOn(ioDispatcher)
    }

    override fun observeSameDayInEarlierYears(date: LocalDate): Flow<List<Apod>> =
        apodDao.observeSameDayBefore(
            monthDay = date.toString().substring(MONTH_DAY_OFFSET),
            excludedDate = date.toString(),
        )
            .map { entities -> entities.toDomain() }
            .flowOn(ioDispatcher)

    override fun observeCount(): Flow<Int> = apodDao.observeCount().flowOn(ioDispatcher)

    override fun observeOldestDate(): Flow<LocalDate?> = apodDao.observeOldestDate()
        .map { raw -> raw?.let(LocalDate::parse) }
        .flowOn(ioDispatcher)

    override suspend fun upsertPreservingLocalState(entities: List<ApodEntity>): DataResult<Int> =
        runDatabaseCall { apodDao.upsertPreservingLocalState(entities) }

    private suspend fun <T> runDatabaseCall(block: suspend () -> T): DataResult<T> =
        withContext(ioDispatcher) {
            try {
                DataResult.Success(block())
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                DataResult.Failure(AppError.Database(throwable.message))
            }
        }

    private companion object {
        /** A screenful of matches many times over; a query that matches more needs another word. */
        const val MAX_SEARCH_RESULTS = 200

        /** `yyyy-MM-dd`: the month and day start after the year and its dash. */
        const val MONTH_DAY_OFFSET = 5
    }
}

/**
 * Builds an FTS4 `MATCH` expression from what the user typed: every word must appear, each as a
 * prefix so results narrow while typing ("andro" finds "Andromeda").
 *
 * Words are reduced to letters and digits and lowercased, which keeps FTS syntax - quotes,
 * `-exclusions`, upper-case `AND`/`OR`/`NOT` operators - from ever reaching the engine: whatever
 * is typed, the query stays valid. Returns `null` when nothing searchable is left.
 */
internal fun ftsMatchExpression(query: String): String? =
    SEARCH_WORD.findAll(query.lowercase())
        .map { word -> "${word.value}*" }
        .take(MAX_SEARCH_WORDS)
        .toList()
        .takeIf { it.isNotEmpty() }
        ?.joinToString(" ")

private val SEARCH_WORD = Regex("""[\p{L}\p{N}]+""")

/** Beyond a handful of words a query only gets slower, not more precise. */
private const val MAX_SEARCH_WORDS = 8
