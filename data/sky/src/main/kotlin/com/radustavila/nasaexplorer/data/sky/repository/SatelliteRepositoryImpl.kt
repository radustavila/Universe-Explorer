package com.radustavila.nasaexplorer.data.sky.repository

import com.radustavila.nasaexplorer.core.common.dispatchers.AppDispatcher
import com.radustavila.nasaexplorer.core.common.dispatchers.Dispatcher
import com.radustavila.nasaexplorer.core.common.error.AppError
import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.common.result.map
import com.radustavila.nasaexplorer.core.common.time.TimeProvider
import com.radustavila.nasaexplorer.core.domain.sky.SatelliteRepository
import com.radustavila.nasaexplorer.core.model.OrbitalElements
import com.radustavila.nasaexplorer.core.model.SatellitePosition
import com.radustavila.nasaexplorer.core.model.SatelliteSet
import com.radustavila.nasaexplorer.data.sky.local.OrbitalElementsDao
import com.radustavila.nasaexplorer.data.sky.mapper.toDomain
import com.radustavila.nasaexplorer.data.sky.mapper.toDomainOrNull
import com.radustavila.nasaexplorer.data.sky.mapper.toEntityOrNull
import com.radustavila.nasaexplorer.data.sky.remote.SkyRemoteDataSource
import com.radustavila.nasaexplorer.data.sky.remote.dto.GpElementsDto
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
 *
 * A named set (the ISS, Hubble, Tiangong) is one object asked for by catalogue number; a group set
 * is a CelesTrak group whose members are recorded, so the next refresh replaces them all.
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
        when (val result = refreshSet(SatelliteSet.ISS)) {
            is DataResult.Success -> DataResult.Success(Unit)
            is DataResult.Failure -> result
        }

    override fun observeSet(set: SatelliteSet): Flow<List<OrbitalElements>> {
        val rows = set.noradId?.let { noradId -> dao.observe(noradId).map(::listOfNotNull) }
            ?: dao.observeSet(set.name)
        return rows.map { entities -> entities.map { it.toDomain() } }.distinctUntilChanged().flowOn(ioDispatcher)
    }

    override suspend fun refreshSet(set: SatelliteSet): DataResult<Int> {
        val noradId = set.noradId
        val group = CELESTRAK_GROUPS[set]
        val response = when {
            noradId != null -> remoteDataSource.fetchElements(noradId)
            group != null -> remoteDataSource.fetchGroup(group)
            else -> error("No CelesTrak query for $set")
        }
        return when (response) {
            is DataResult.Success -> {
                val fetchedAt = timeProvider.now()
                val entities = response.data.filter { it.belongsTo(set) }.mapNotNull { it.toEntityOrNull(fetchedAt) }
                when {
                    noradId != null && entities.isEmpty() -> DataResult.Failure(AppError.EmptyResponse)
                    noradId != null -> store { dao.upsert(entities) }.map { entities.size }
                    // A month without a Starlink launch is possible: an empty set is an answer.
                    entities.isEmpty() && set != SatelliteSet.STARLINK_TRAINS ->
                        DataResult.Failure(AppError.EmptyResponse)
                    else -> store { dao.replaceSet(set.name, entities, keep = NAMED_NORAD_IDS) }.map { entities.size }
                }
            }

            is DataResult.Failure -> response
        }
    }

    override suspend fun fetchLivePosition(noradId: Int): DataResult<SatellitePosition> =
        when (val result = remoteDataSource.fetchLivePosition(noradId)) {
            is DataResult.Success -> result.data.toDomainOrNull()?.let { DataResult.Success(it) }
                ?: DataResult.Failure(AppError.EmptyResponse)

            is DataResult.Failure -> result
        }

    private suspend fun store(write: suspend () -> Unit): DataResult<Unit> = withContext(ioDispatcher) {
        try {
            DataResult.Success(write())
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            DataResult.Failure(AppError.Database(throwable.message))
        }
    }

    /** `last-30-days` holds every recent launch; only the Starlink satellites are kept. */
    private fun GpElementsDto.belongsTo(set: SatelliteSet): Boolean = when (set) {
        SatelliteSet.STARLINK_TRAINS -> objectName?.trim()?.startsWith(STARLINK_NAME_PREFIX, ignoreCase = true) == true
        else -> set.noradId == null || noradCatId == set.noradId
    }

    private companion object {
        val CELESTRAK_GROUPS = mapOf(
            SatelliteSet.STARLINK_TRAINS to "last-30-days",
            SatelliteSet.BRIGHT_SATELLITES to "visual",
        )
        val NAMED_NORAD_IDS: List<Int> = SatelliteSet.entries.mapNotNull { it.noradId }
        const val STARLINK_NAME_PREFIX = "STARLINK"
    }
}
