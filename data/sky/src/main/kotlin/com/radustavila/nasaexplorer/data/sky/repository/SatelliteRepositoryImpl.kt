package com.radustavila.nasaexplorer.data.sky.repository

import com.radustavila.nasaexplorer.core.common.dispatchers.AppDispatcher
import com.radustavila.nasaexplorer.core.common.dispatchers.Dispatcher
import com.radustavila.nasaexplorer.core.common.error.AppError
import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.common.time.TimeProvider
import com.radustavila.nasaexplorer.core.domain.sky.SatelliteRepository
import com.radustavila.nasaexplorer.core.model.OrbitalElements
import com.radustavila.nasaexplorer.core.model.SatellitePosition
import com.radustavila.nasaexplorer.data.sky.local.OrbitalElementsDao
import com.radustavila.nasaexplorer.data.sky.local.OrbitalElementsEntity
import com.radustavila.nasaexplorer.data.sky.mapper.toDomain
import com.radustavila.nasaexplorer.data.sky.mapper.toDomainOrNull
import com.radustavila.nasaexplorer.data.sky.mapper.toEntityOrNull
import com.radustavila.nasaexplorer.data.sky.remote.SkyRemoteDataSource
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Orbital elements are offline-first (Room is what predictions read; a refresh only writes into
 * it), the live position is a pass-through: a dot that is minutes old is wrong, so it is never
 * cached.
 */
@Singleton
class SatelliteRepositoryImpl @Inject constructor(
    private val dao: OrbitalElementsDao,
    private val remoteDataSource: SkyRemoteDataSource,
    private val timeProvider: TimeProvider,
    @Dispatcher(AppDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
) : SatelliteRepository {

    override fun observeElements(noradId: Int): Flow<OrbitalElements?> =
        dao.observe(noradId).map { it?.toDomain() }.distinctUntilChanged().flowOn(ioDispatcher)

    override suspend fun refreshElements(): DataResult<Unit> =
        when (val result = remoteDataSource.fetchElements(OrbitalElements.ISS_NORAD_ID)) {
            is DataResult.Success -> {
                val entities = result.data.mapNotNull { it.toEntityOrNull(fetchedAt = timeProvider.now()) }
                if (entities.isEmpty()) DataResult.Failure(AppError.EmptyResponse) else store(entities)
            }

            is DataResult.Failure -> result
        }

    override suspend fun fetchLivePosition(noradId: Int): DataResult<SatellitePosition> =
        when (val result = remoteDataSource.fetchLivePosition(noradId)) {
            is DataResult.Success -> result.data.toDomainOrNull()?.let { DataResult.Success(it) }
                ?: DataResult.Failure(AppError.EmptyResponse)

            is DataResult.Failure -> result
        }

    private suspend fun store(entities: List<OrbitalElementsEntity>): DataResult<Unit> = withContext(ioDispatcher) {
        try {
            DataResult.Success(dao.upsert(entities))
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            DataResult.Failure(AppError.Database(throwable.message))
        }
    }
}
