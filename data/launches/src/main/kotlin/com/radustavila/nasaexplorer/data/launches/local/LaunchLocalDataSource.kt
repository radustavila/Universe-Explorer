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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** Local boundary of the launches module: the source of truth the card and the screen observe. */
interface LaunchLocalDataSource {
    fun observeUpcoming(): Flow<List<Launch>>
    fun observePast(): Flow<List<Launch>>
    fun observeLaunch(id: String): Flow<Launch?>

    /** Replaces the launches of [timeline] (`LaunchEntity.TIMELINE_*`). */
    suspend fun replaceTimeline(timeline: String, launches: List<LaunchEntity>): DataResult<Unit>

    /** Updates one launch, keeping the list it is in; a launch not cached yet joins [fallbackTimeline]. */
    suspend fun updateLaunch(launch: LaunchEntity, fallbackTimeline: String): DataResult<Launch>
}

@Singleton
class RoomLaunchLocalDataSource @Inject constructor(
    private val launchDao: LaunchDao,
    @Dispatcher(AppDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
) : LaunchLocalDataSource {

    override fun observeUpcoming(): Flow<List<Launch>> =
        launchDao.observeUpcoming().map { entities -> entities.map(LaunchEntity::toDomain) }
            .flowOn(ioDispatcher)

    override fun observePast(): Flow<List<Launch>> =
        launchDao.observePast().map { entities -> entities.map(LaunchEntity::toDomain) }
            .flowOn(ioDispatcher)

    override fun observeLaunch(id: String): Flow<Launch?> =
        launchDao.observeById(id).distinctUntilChanged().map { it?.toDomain() }.flowOn(ioDispatcher)

    override suspend fun replaceTimeline(timeline: String, launches: List<LaunchEntity>): DataResult<Unit> =
        databaseCall { launchDao.replaceTimeline(timeline, launches.map { it.copy(timeline = timeline) }) }

    override suspend fun updateLaunch(launch: LaunchEntity, fallbackTimeline: String): DataResult<Launch> =
        databaseCall {
            val kept = launch.copy(timeline = launchDao.timelineOf(launch.id) ?: fallbackTimeline)
            launchDao.insertAll(listOf(kept))
            kept.toDomain()
        }

    private suspend fun <T> databaseCall(block: suspend () -> T): DataResult<T> = withContext(ioDispatcher) {
        try {
            DataResult.Success(block())
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            DataResult.Failure(AppError.Database(throwable.message))
        }
    }
}
