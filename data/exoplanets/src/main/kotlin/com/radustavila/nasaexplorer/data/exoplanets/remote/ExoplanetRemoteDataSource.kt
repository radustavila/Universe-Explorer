package com.radustavila.nasaexplorer.data.exoplanets.remote

import com.radustavila.nasaexplorer.core.common.dispatchers.AppDispatcher
import com.radustavila.nasaexplorer.core.common.dispatchers.Dispatcher
import com.radustavila.nasaexplorer.core.common.error.AppError
import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.common.result.flatMap
import com.radustavila.nasaexplorer.core.network.http.safeNetworkCall
import com.radustavila.nasaexplorer.data.exoplanets.remote.dto.ExoplanetDto
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/** Remote boundary of the exoplanets module. Returns [DataResult] instead of throwing. */
interface ExoplanetRemoteDataSource {
    suspend fun fetchCatalog(newestSinceYear: Int): DataResult<List<ExoplanetDto>>
    suspend fun fetchConfirmedCount(): DataResult<Int>
}

@Singleton
class RetrofitExoplanetRemoteDataSource @Inject constructor(
    private val service: ExoplanetArchiveService,
    @Dispatcher(AppDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
) : ExoplanetRemoteDataSource {

    override suspend fun fetchCatalog(newestSinceYear: Int): DataResult<List<ExoplanetDto>> =
        withContext(ioDispatcher) {
            safeNetworkCall {
                service.queryPlanets(ExoplanetQueries.encode(ExoplanetQueries.catalog(newestSinceYear)))
            }
        }

    override suspend fun fetchConfirmedCount(): DataResult<Int> = withContext(ioDispatcher) {
        safeNetworkCall { service.queryCount(ExoplanetQueries.encode(ExoplanetQueries.CONFIRMED_COUNT)) }
            .flatMap { rows ->
                rows.firstOrNull()?.count?.let { DataResult.Success(it) }
                    ?: DataResult.Failure(AppError.EmptyResponse)
            }
    }
}
