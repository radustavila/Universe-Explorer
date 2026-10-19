package com.radustavila.nasaexplorer.data.smallbodies.remote

import com.radustavila.nasaexplorer.core.common.dispatchers.AppDispatcher
import com.radustavila.nasaexplorer.core.common.dispatchers.Dispatcher
import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.network.http.safeNetworkCall
import com.radustavila.nasaexplorer.data.smallbodies.remote.dto.JplTableDto
import com.radustavila.nasaexplorer.data.smallbodies.remote.dto.SbdbDto
import com.radustavila.nasaexplorer.data.smallbodies.remote.dto.SentryListDto
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/** Remote boundary of the small-bodies module. Returns [DataResult] instead of throwing. */
interface SmallBodiesRemoteDataSource {
    suspend fun fetchCloseApproaches(): DataResult<JplTableDto>
    suspend fun fetchSentryList(): DataResult<SentryListDto>
    suspend fun fetchFireballs(): DataResult<JplTableDto>
    suspend fun fetchSmallBody(designation: String): DataResult<SbdbDto>
}

@Singleton
class RetrofitSmallBodiesRemoteDataSource @Inject constructor(
    private val service: JplSsdService,
    @Dispatcher(AppDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
) : SmallBodiesRemoteDataSource {

    override suspend fun fetchCloseApproaches(): DataResult<JplTableDto> = call { service.getCloseApproaches() }

    override suspend fun fetchSentryList(): DataResult<SentryListDto> = call { service.getSentryList() }

    override suspend fun fetchFireballs(): DataResult<JplTableDto> = call { service.getFireballs() }

    override suspend fun fetchSmallBody(designation: String): DataResult<SbdbDto> =
        call { service.getSmallBody(designation) }

    private suspend fun <T> call(block: suspend () -> T): DataResult<T> =
        withContext(ioDispatcher) { safeNetworkCall(block) }
}
