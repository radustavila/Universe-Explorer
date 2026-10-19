package com.radustavila.nasaexplorer.data.epic.remote

import com.radustavila.nasaexplorer.core.common.dispatchers.AppDispatcher
import com.radustavila.nasaexplorer.core.common.dispatchers.Dispatcher
import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.model.EpicCollection
import com.radustavila.nasaexplorer.core.network.http.safeNetworkCall
import com.radustavila.nasaexplorer.data.epic.remote.dto.EpicDateDto
import com.radustavila.nasaexplorer.data.epic.remote.dto.EpicImageDto
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/** Remote boundary of the EPIC data module: transport failures come back as [DataResult.Failure]. */
interface EpicRemoteDataSource {
    suspend fun fetchLatest(collection: EpicCollection): DataResult<List<EpicImageDto>>
    suspend fun fetchDay(collection: EpicCollection, date: LocalDate): DataResult<List<EpicImageDto>>
    suspend fun fetchAvailableDates(collection: EpicCollection): DataResult<List<EpicDateDto>>
}

@Singleton
class RetrofitEpicRemoteDataSource @Inject constructor(
    private val service: EpicService,
    @Dispatcher(AppDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
) : EpicRemoteDataSource {

    override suspend fun fetchLatest(collection: EpicCollection): DataResult<List<EpicImageDto>> =
        call { service.getLatest(collection.apiValue) }

    override suspend fun fetchDay(
        collection: EpicCollection,
        date: LocalDate,
    ): DataResult<List<EpicImageDto>> = call {
        // LocalDate.toString() is ISO-8601, exactly the yyyy-MM-dd the API expects.
        service.getDay(collection = collection.apiValue, date = date.toString())
    }

    override suspend fun fetchAvailableDates(collection: EpicCollection): DataResult<List<EpicDateDto>> =
        call { service.getAvailableDates(collection.apiValue) }

    private suspend fun <T> call(block: suspend () -> T): DataResult<T> =
        withContext(ioDispatcher) { safeNetworkCall(block) }
}
