package com.radustavila.nasaexplorer.data.smallbodies.local

import com.radustavila.nasaexplorer.core.common.dispatchers.AppDispatcher
import com.radustavila.nasaexplorer.core.common.dispatchers.Dispatcher
import com.radustavila.nasaexplorer.core.common.error.AppError
import com.radustavila.nasaexplorer.core.common.result.DataResult
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

/** Local boundary of the small-bodies module: the source of truth the screens observe. */
interface SmallBodiesLocalDataSource {
    fun observeCloseApproaches(): Flow<List<CloseApproachWithSentry>>
    fun observeCloseApproach(designation: String): Flow<CloseApproachWithSentry?>
    fun observeFireballs(): Flow<List<FireballEntity>>
    fun observeDetails(designation: String): Flow<SmallBodyDetailsEntity?>
    suspend fun replaceAll(
        approaches: List<CloseApproachEntity>,
        sentry: List<SentryObjectEntity>,
        fireballs: List<FireballEntity>,
    ): DataResult<Unit>
    suspend fun saveDetails(details: SmallBodyDetailsEntity): DataResult<Unit>
}

@Singleton
class RoomSmallBodiesLocalDataSource @Inject constructor(
    private val dao: SmallBodiesDao,
    @Dispatcher(AppDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
) : SmallBodiesLocalDataSource {

    override fun observeCloseApproaches(): Flow<List<CloseApproachWithSentry>> =
        dao.observeCloseApproaches().flowOn(ioDispatcher)

    override fun observeCloseApproach(designation: String): Flow<CloseApproachWithSentry?> =
        dao.observeCloseApproach(designation).flowOn(ioDispatcher)

    override fun observeFireballs(): Flow<List<FireballEntity>> = dao.observeFireballs().flowOn(ioDispatcher)

    override fun observeDetails(designation: String): Flow<SmallBodyDetailsEntity?> =
        dao.observeDetails(designation).flowOn(ioDispatcher)

    override suspend fun replaceAll(
        approaches: List<CloseApproachEntity>,
        sentry: List<SentryObjectEntity>,
        fireballs: List<FireballEntity>,
    ): DataResult<Unit> = databaseCall { dao.replaceAll(approaches, sentry, fireballs) }

    override suspend fun saveDetails(details: SmallBodyDetailsEntity): DataResult<Unit> =
        databaseCall { dao.upsertDetails(details) }

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
