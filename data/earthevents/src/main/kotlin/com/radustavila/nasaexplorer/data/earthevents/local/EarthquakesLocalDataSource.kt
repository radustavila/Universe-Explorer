package com.radustavila.nasaexplorer.data.earthevents.local

import com.radustavila.nasaexplorer.core.common.dispatchers.AppDispatcher
import com.radustavila.nasaexplorer.core.common.dispatchers.Dispatcher
import com.radustavila.nasaexplorer.core.common.error.AppError
import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.model.Earthquake
import com.radustavila.nasaexplorer.data.earthevents.mapper.toDomain
import com.radustavila.nasaexplorer.data.earthevents.mapper.toEntity
import com.radustavila.nasaexplorer.data.earthevents.remote.EarthquakeFeed
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** Local boundary of the quake feeds: the cached quakes and each feed's `Last-Modified`. */
interface EarthquakesLocalDataSource {
    /** Every cached quake once, newest first. */
    fun observeEarthquakes(): Flow<List<Earthquake>>

    /** The `Last-Modified` to revalidate [feed] with, or null to download it whole. */
    suspend fun lastModified(feed: EarthquakeFeed): DataResult<String?>

    suspend fun replaceFeed(
        feed: EarthquakeFeed,
        quakes: List<Earthquake>,
        lastModified: String?,
        at: Instant,
    ): DataResult<Unit>
}

class RoomEarthquakesLocalDataSource @Inject constructor(
    private val dao: EarthquakeDao,
    @Dispatcher(AppDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
) : EarthquakesLocalDataSource {

    override fun observeEarthquakes(): Flow<List<Earthquake>> =
        dao.observeEarthquakes()
            .map { rows -> rows.distinctBy(EarthquakeEntity::id).map { it.toDomain() } }
            .flowOn(ioDispatcher)

    // A feed whose quakes were evicted or cleared must be downloaded again, not revalidated.
    override suspend fun lastModified(feed: EarthquakeFeed): DataResult<String?> = io {
        dao.feed(feed.key)?.lastModified?.takeIf { dao.countIn(feed.key) > 0 }
    }

    override suspend fun replaceFeed(
        feed: EarthquakeFeed,
        quakes: List<Earthquake>,
        lastModified: String?,
        at: Instant,
    ): DataResult<Unit> = io {
        dao.replaceFeed(
            feed = EarthquakeFeedEntity(feed.key, lastModified = lastModified, fetchedAtMillis = at.toEpochMilli()),
            quakes = quakes.map { it.toEntity(feed) },
        )
    }

    @Suppress("TooGenericExceptionCaught") // Any SQLite failure becomes AppError.Database.
    private suspend fun <T> io(block: suspend () -> T): DataResult<T> = withContext(ioDispatcher) {
        try {
            DataResult.Success(block())
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            DataResult.Failure(AppError.Database(throwable.message))
        }
    }
}
