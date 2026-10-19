package com.radustavila.nasaexplorer.data.epic.local

import com.radustavila.nasaexplorer.core.common.dispatchers.AppDispatcher
import com.radustavila.nasaexplorer.core.common.dispatchers.Dispatcher
import com.radustavila.nasaexplorer.core.common.error.AppError
import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.database.dao.EpicDao
import com.radustavila.nasaexplorer.core.database.entity.EpicAvailableDateEntity
import com.radustavila.nasaexplorer.core.database.entity.EpicImageEntity
import com.radustavila.nasaexplorer.core.model.EpicCollection
import com.radustavila.nasaexplorer.core.model.EpicImage
import com.radustavila.nasaexplorer.data.epic.mapper.toDomain
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Local boundary of the EPIC data module, the source of truth the screens observe. Writes return
 * [DataResult] so SQLite failures surface as [AppError.Database] rather than exceptions.
 */
interface EpicLocalDataSource {
    fun observeLatestDay(collection: EpicCollection): Flow<List<EpicImage>>
    fun observeDay(collection: EpicCollection, date: LocalDate): Flow<List<EpicImage>>
    fun observeAvailableDates(collection: EpicCollection): Flow<List<LocalDate>>

    suspend fun latestDay(collection: EpicCollection): DataResult<List<EpicImage>>
    suspend fun isDateAvailable(collection: EpicCollection, date: LocalDate): DataResult<Boolean>

    /** Replaces each day present in [images] and drops days of that collection before [keepFrom]. */
    suspend fun replaceDays(
        collection: EpicCollection,
        images: List<EpicImageEntity>,
        keepFrom: LocalDate?,
    ): DataResult<Unit>

    suspend fun replaceAvailableDates(collection: EpicCollection, dates: List<LocalDate>): DataResult<Unit>
}

@Singleton
class RoomEpicLocalDataSource @Inject constructor(
    private val epicDao: EpicDao,
    @Dispatcher(AppDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
) : EpicLocalDataSource {

    override fun observeLatestDay(collection: EpicCollection): Flow<List<EpicImage>> =
        epicDao.observeLatestDay(collection.apiValue)
            .map { entities -> entities.toDomain() }
            .flowOn(ioDispatcher)

    override fun observeDay(collection: EpicCollection, date: LocalDate): Flow<List<EpicImage>> =
        epicDao.observeDay(collection = collection.apiValue, date = date.toString())
            .map { entities -> entities.toDomain() }
            .flowOn(ioDispatcher)

    override fun observeAvailableDates(collection: EpicCollection): Flow<List<LocalDate>> =
        epicDao.observeAvailableDates(collection.apiValue)
            .map { dates -> dates.map(LocalDate::parse) }
            .flowOn(ioDispatcher)

    override suspend fun latestDay(collection: EpicCollection): DataResult<List<EpicImage>> =
        runDatabaseCall { epicDao.latestDay(collection.apiValue).toDomain() }

    override suspend fun isDateAvailable(
        collection: EpicCollection,
        date: LocalDate,
    ): DataResult<Boolean> = runDatabaseCall {
        epicDao.isDateAvailable(collection = collection.apiValue, date = date.toString())
    }

    override suspend fun replaceDays(
        collection: EpicCollection,
        images: List<EpicImageEntity>,
        keepFrom: LocalDate?,
    ): DataResult<Unit> = runDatabaseCall {
        epicDao.replaceDays(
            collection = collection.apiValue,
            images = images,
            keepFrom = keepFrom?.toString(),
        )
    }

    override suspend fun replaceAvailableDates(
        collection: EpicCollection,
        dates: List<LocalDate>,
    ): DataResult<Unit> = runDatabaseCall {
        epicDao.replaceAvailableDates(
            collection = collection.apiValue,
            dates = dates.map { date -> EpicAvailableDateEntity(collection.apiValue, date.toString()) },
        )
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
