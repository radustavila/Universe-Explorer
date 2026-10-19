package com.radustavila.nasaexplorer.data.mars.local

import com.radustavila.nasaexplorer.core.common.dispatchers.AppDispatcher
import com.radustavila.nasaexplorer.core.common.dispatchers.Dispatcher
import com.radustavila.nasaexplorer.core.common.error.AppError
import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.model.MarsPhoto
import com.radustavila.nasaexplorer.core.model.MarsRover
import com.radustavila.nasaexplorer.data.mars.mapper.toDomain
import com.radustavila.nasaexplorer.data.mars.mapper.toEntity
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** Local boundary of the Mars module. */
interface MarsLocalDataSource {
    /** @param limit at most this many; negative for all. */
    fun observeRover(rover: MarsRover, limit: Int = -1): Flow<List<MarsPhoto>>
    fun observePhoto(id: String): Flow<MarsPhoto?>
    suspend fun store(photos: List<MarsPhoto>, at: Instant): DataResult<Unit>
}

class RoomMarsLocalDataSource @Inject constructor(
    private val dao: MarsPhotoDao,
    @Dispatcher(AppDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
) : MarsLocalDataSource {

    override fun observeRover(rover: MarsRover, limit: Int): Flow<List<MarsPhoto>> =
        dao.observeRover(rover.key, limit)
            .map { rows -> rows.mapNotNull { it.toDomain() } }
            .flowOn(ioDispatcher)

    override fun observePhoto(id: String): Flow<MarsPhoto?> =
        dao.observePhoto(id).map { it?.toDomain() }.flowOn(ioDispatcher)

    override suspend fun store(photos: List<MarsPhoto>, at: Instant): DataResult<Unit> =
        withContext(ioDispatcher) {
            try {
                DataResult.Success(dao.upsert(photos.map { it.toEntity(at) }))
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                DataResult.Failure(AppError.Database(throwable.message))
            }
        }
}
