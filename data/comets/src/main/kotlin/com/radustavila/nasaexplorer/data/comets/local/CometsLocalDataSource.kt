package com.radustavila.nasaexplorer.data.comets.local

import com.radustavila.nasaexplorer.core.common.dispatchers.AppDispatcher
import com.radustavila.nasaexplorer.core.common.dispatchers.Dispatcher
import com.radustavila.nasaexplorer.core.common.error.AppError
import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.model.Comet
import com.radustavila.nasaexplorer.data.comets.mapper.toDomain
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** Local boundary of the comets module: the source of truth the screens observe. */
internal interface CometsLocalDataSource {
    fun observeComets(): Flow<List<Comet>>
    fun observeComet(designation: String): Flow<Comet?>
    suspend fun storeRefresh(
        comets: List<CometEntity>,
        ephemerides: Map<String, List<CometEphemerisEntity>>,
    ): DataResult<Unit>
}

@Singleton
internal class RoomCometsLocalDataSource @Inject constructor(
    private val dao: CometsDao,
    @Dispatcher(AppDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
) : CometsLocalDataSource {

    override fun observeComets(): Flow<List<Comet>> =
        dao.observeComets().map { rows -> rows.map { it.toDomain() } }.flowOn(ioDispatcher)

    override fun observeComet(designation: String): Flow<Comet?> =
        dao.observeComet(designation).map { it?.toDomain() }.flowOn(ioDispatcher)

    @Suppress("TooGenericExceptionCaught") // Any SQLite failure becomes AppError.Database.
    override suspend fun storeRefresh(
        comets: List<CometEntity>,
        ephemerides: Map<String, List<CometEphemerisEntity>>,
    ): DataResult<Unit> = withContext(ioDispatcher) {
        try {
            DataResult.Success(dao.storeRefresh(comets, ephemerides))
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            DataResult.Failure(AppError.Database(throwable.message))
        }
    }
}
