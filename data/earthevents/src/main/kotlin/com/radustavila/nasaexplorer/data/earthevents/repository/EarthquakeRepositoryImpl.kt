package com.radustavila.nasaexplorer.data.earthevents.repository

import com.radustavila.nasaexplorer.core.common.error.AppError
import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.common.time.TimeProvider
import com.radustavila.nasaexplorer.core.domain.earthquake.EarthquakeRepository
import com.radustavila.nasaexplorer.core.model.Earthquake
import com.radustavila.nasaexplorer.data.earthevents.local.EarthquakesLocalDataSource
import com.radustavila.nasaexplorer.data.earthevents.mapper.toDomainOrNull
import com.radustavila.nasaexplorer.data.earthevents.remote.EarthquakeFeed
import com.radustavila.nasaexplorer.data.earthevents.remote.EarthquakeFeedDownload
import com.radustavila.nasaexplorer.data.earthevents.remote.UsgsRemoteDataSource
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/**
 * USGS quakes, offline-first: the maps read Room, and each feed is revalidated with
 * `If-Modified-Since` and, when it changed, replaced in one transaction. A feed that fails keeps its
 * cached quakes; the refresh fails only when every feed did.
 */
@Singleton
class EarthquakeRepositoryImpl @Inject constructor(
    private val localDataSource: EarthquakesLocalDataSource,
    private val remoteDataSource: UsgsRemoteDataSource,
    private val timeProvider: TimeProvider,
) : EarthquakeRepository {

    override fun observeEarthquakes(): Flow<List<Earthquake>> = localDataSource.observeEarthquakes()

    override suspend fun refreshEarthquakes(): DataResult<Int> {
        val failures = EarthquakeFeed.entries.mapNotNull { feed -> (refresh(feed) as? DataResult.Failure)?.error }
        if (failures.size == EarthquakeFeed.entries.size) return DataResult.Failure(failures.first())
        return DataResult.Success(localDataSource.observeEarthquakes().first().size)
    }

    private suspend fun refresh(feed: EarthquakeFeed): DataResult<Unit> {
        val lastModified = (localDataSource.lastModified(feed) as? DataResult.Success)?.data
        val download = when (val result = remoteDataSource.fetchFeed(feed, lastModified)) {
            is DataResult.Success -> result.data
            is DataResult.Failure -> return result
        }
        if (download !is EarthquakeFeedDownload.Updated) return DataResult.Success(Unit)

        val features = download.feed.features
        val quakes = features.mapNotNull { it.toDomainOrNull() }.distinctBy(Earthquake::id)
        // A non-empty feed of which nothing parses is a format change, not a quiet planet.
        if (quakes.isEmpty() && features.isNotEmpty()) return DataResult.Failure(AppError.EmptyResponse)
        return localDataSource.replaceFeed(feed, quakes, download.lastModified, timeProvider.now())
    }
}
