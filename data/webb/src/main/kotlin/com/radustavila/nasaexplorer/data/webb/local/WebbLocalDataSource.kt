package com.radustavila.nasaexplorer.data.webb.local

import com.radustavila.nasaexplorer.core.common.dispatchers.AppDispatcher
import com.radustavila.nasaexplorer.core.common.dispatchers.Dispatcher
import com.radustavila.nasaexplorer.core.common.error.AppError
import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.model.WebbSchedule
import com.radustavila.nasaexplorer.data.webb.mapper.toSchedule
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** Local boundary of the Webb module: the source of truth the card and the schedule screen observe. */
internal interface WebbLocalDataSource {
    fun observeSchedule(): Flow<WebbSchedule?>
    suspend fun replaceSchedule(visits: List<WebbVisitEntity>): DataResult<Unit>
}

@Singleton
internal class RoomWebbLocalDataSource @Inject constructor(
    private val dao: WebbDao,
    @Dispatcher(AppDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
) : WebbLocalDataSource {

    override fun observeSchedule(): Flow<WebbSchedule?> =
        dao.observeVisits().map { rows -> rows.toSchedule() }.flowOn(ioDispatcher)

    @Suppress("TooGenericExceptionCaught") // Any SQLite failure becomes AppError.Database.
    override suspend fun replaceSchedule(visits: List<WebbVisitEntity>): DataResult<Unit> = withContext(ioDispatcher) {
        try {
            DataResult.Success(dao.replaceSchedule(visits))
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            DataResult.Failure(AppError.Database(throwable.message))
        }
    }
}
