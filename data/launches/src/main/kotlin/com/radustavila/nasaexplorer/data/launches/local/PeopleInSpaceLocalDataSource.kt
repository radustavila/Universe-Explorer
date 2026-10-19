package com.radustavila.nasaexplorer.data.launches.local

import com.radustavila.nasaexplorer.core.common.dispatchers.AppDispatcher
import com.radustavila.nasaexplorer.core.common.dispatchers.Dispatcher
import com.radustavila.nasaexplorer.core.common.error.AppError
import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.model.PersonInSpace
import com.radustavila.nasaexplorer.data.launches.mapper.toDomain
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

interface PeopleInSpaceLocalDataSource {
    fun observeAll(): Flow<List<PersonInSpace>>
    suspend fun replaceAll(people: List<PersonInSpaceEntity>): DataResult<Unit>
}

@Singleton
class RoomPeopleInSpaceLocalDataSource @Inject constructor(
    private val dao: PersonInSpaceDao,
    @Dispatcher(AppDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
) : PeopleInSpaceLocalDataSource {

    override fun observeAll(): Flow<List<PersonInSpace>> =
        dao.observeAll().map { entities -> entities.map(PersonInSpaceEntity::toDomain) }.flowOn(ioDispatcher)

    override suspend fun replaceAll(people: List<PersonInSpaceEntity>): DataResult<Unit> =
        withContext(ioDispatcher) {
            try {
                DataResult.Success(dao.replaceAll(people))
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                DataResult.Failure(AppError.Database(throwable.message))
            }
        }
}
