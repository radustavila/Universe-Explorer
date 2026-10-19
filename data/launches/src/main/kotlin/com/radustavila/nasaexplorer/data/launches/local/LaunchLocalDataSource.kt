package com.radustavila.nasaexplorer.data.launches.local

import com.radustavila.nasaexplorer.core.common.dispatchers.AppDispatcher
import com.radustavila.nasaexplorer.core.common.dispatchers.Dispatcher
import com.radustavila.nasaexplorer.core.common.error.AppError
import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.model.Launch
import com.radustavila.nasaexplorer.data.launches.mapper.toDomain
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** Local boundary of the launches module: the source of truth the card observes. */
interface LaunchLocalDataSource {
    fun observeUpcoming(): Flow<List<Launch>>
    suspend fun replaceAll(launches: List<LaunchEntity>): DataResult<Unit>
}

@Singleton
class RoomLaunchLocalDataSource @Inject constructor(
    private val launchDao: LaunchDao,
    @Dispatcher(AppDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
) : LaunchLocalDataSource {

    override fun observeUpcoming(): Flow<List<Launch>> =
        launchDao.observeUpcoming().map { entities -> entities.map(LaunchEntity::toDomain) }
            .flowOn(ioDispatcher)

    override suspend fun replaceAll(launches: List<LaunchEntity>): DataResult<Unit> =
        withContext(ioDispatcher) {
            try {
                DataResult.Success(launchDao.replaceAll(launches))
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                DataResult.Failure(AppError.Database(throwable.message))
            }
        }
}
