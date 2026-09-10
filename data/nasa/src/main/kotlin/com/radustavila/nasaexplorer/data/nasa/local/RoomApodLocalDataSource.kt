package com.radustavila.nasaexplorer.data.nasa.local

import com.radustavila.nasaexplorer.core.common.dispatchers.AppDispatcher
import com.radustavila.nasaexplorer.core.common.dispatchers.Dispatcher
import com.radustavila.nasaexplorer.core.common.error.AppError
import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.database.dao.ApodDao
import com.radustavila.nasaexplorer.core.database.entity.ApodEntity
import com.radustavila.nasaexplorer.core.model.Apod
import com.radustavila.nasaexplorer.data.nasa.mapper.toDomain
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

@Singleton
class RoomApodLocalDataSource @Inject constructor(
    private val apodDao: ApodDao,
    @Dispatcher(AppDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
) : ApodLocalDataSource {

    override fun observeFeed(): Flow<List<Apod>> =
        apodDao.observeFeed().map { entities -> entities.toDomain() }.flowOn(ioDispatcher)

    override fun observeApod(date: LocalDate): Flow<Apod?> =
        apodDao.observeApod(date.toString())
            .map { entity -> entity?.toDomain() }
            .flowOn(ioDispatcher)

    override fun observeFavorites(): Flow<List<Apod>> =
        apodDao.observeFavorites().map { entities -> entities.toDomain() }.flowOn(ioDispatcher)

    override fun observeCount(): Flow<Int> = apodDao.observeCount().flowOn(ioDispatcher)

    override fun observeFavoritesCount(): Flow<Int> =
        apodDao.observeFavoritesCount().flowOn(ioDispatcher)

    override fun observeOldestDate(): Flow<LocalDate?> = apodDao.observeOldestDate()
        .map { raw -> raw?.let(LocalDate::parse) }
        .flowOn(ioDispatcher)

    override suspend fun upsertPreservingLocalState(entities: List<ApodEntity>): DataResult<Int> =
        runDatabaseCall { apodDao.upsertPreservingLocalState(entities) }

    override suspend fun setFavorite(date: LocalDate, isFavorite: Boolean): DataResult<Unit> =
        runDatabaseCall {
            val updatedRows = apodDao.setFavorite(date = date.toString(), isFavorite = isFavorite)
            check(updatedRows > 0) { "No cached APOD for $date" }
        }

    private suspend fun <T> runDatabaseCall(block: suspend () -> T): DataResult<T> =
        withContext(ioDispatcher) {
            try {
                DataResult.Success(block())
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                DataResult.Failure(AppError.Database(throwable.message))
            }
        }
}
