package com.radustavila.nasaexplorer.data.dsn.local

import com.radustavila.nasaexplorer.core.common.dispatchers.AppDispatcher
import com.radustavila.nasaexplorer.core.common.dispatchers.Dispatcher
import com.radustavila.nasaexplorer.core.common.error.AppError
import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.model.DeepSpaceProbe
import com.radustavila.nasaexplorer.core.model.DsnSnapshot
import com.radustavila.nasaexplorer.data.dsn.mapper.DsnConfigRows
import com.radustavila.nasaexplorer.data.dsn.mapper.DsnLiveRows
import com.radustavila.nasaexplorer.data.dsn.mapper.toDomain
import com.radustavila.nasaexplorer.data.dsn.mapper.toSnapshotOrNull
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** Local boundary of the DSN module: the source of truth the screens observe. */
internal interface DsnLocalDataSource {
    fun observeSnapshot(): Flow<DsnSnapshot?>

    /** Stored distances of the spacecraft in [ids], farthest first. */
    fun observeProbes(ids: Set<Int>): Flow<List<DeepSpaceProbe>>
    suspend fun storeLive(rows: DsnLiveRows): DataResult<Unit>
    suspend fun storeConfig(rows: DsnConfigRows): DataResult<Unit>
    suspend fun storeDistances(rows: List<ProbeDistanceEntity>): DataResult<Unit>
}

@Singleton
internal class RoomDsnLocalDataSource @Inject constructor(
    private val dao: DsnDao,
    @Dispatcher(AppDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
) : DsnLocalDataSource {

    override fun observeSnapshot(): Flow<DsnSnapshot?> = combine(
        dao.observeLive(),
        dao.observeSpacecraft(),
        dao.observeDishInfo(),
        dao.observeProbeDistances(),
    ) { live, spacecraft, dishes, distances ->
        live.toSnapshotOrNull(
            names = spacecraft.associate { it.code to it.name },
            diameters = dishes.associate { it.dishName to it.diameterMeters },
            probes = distances.associateBy { it.horizonsId },
        )
    }.flowOn(ioDispatcher)

    override fun observeProbes(ids: Set<Int>): Flow<List<DeepSpaceProbe>> = dao.observeProbeDistances()
        .map { rows -> rows.filter { it.horizonsId in ids }.sortedByDescending { it.distanceAu }.map { it.toDomain() } }
        .flowOn(ioDispatcher)

    override suspend fun storeLive(rows: DsnLiveRows): DataResult<Unit> = write {
        dao.replaceLive(rows.dishes, rows.links, rows.signals)
    }

    override suspend fun storeConfig(rows: DsnConfigRows): DataResult<Unit> = write {
        dao.storeConfig(rows.spacecraft, rows.dishes)
    }

    override suspend fun storeDistances(rows: List<ProbeDistanceEntity>): DataResult<Unit> = write {
        dao.upsertProbeDistances(rows)
    }

    @Suppress("TooGenericExceptionCaught") // Any SQLite failure becomes AppError.Database.
    private suspend fun write(block: suspend () -> Unit): DataResult<Unit> = withContext(ioDispatcher) {
        try {
            DataResult.Success(block())
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            DataResult.Failure(AppError.Database(throwable.message))
        }
    }
}
