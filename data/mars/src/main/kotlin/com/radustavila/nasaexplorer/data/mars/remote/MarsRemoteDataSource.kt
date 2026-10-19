package com.radustavila.nasaexplorer.data.mars.remote

import com.radustavila.nasaexplorer.core.common.dispatchers.AppDispatcher
import com.radustavila.nasaexplorer.core.common.dispatchers.Dispatcher
import com.radustavila.nasaexplorer.core.common.error.AppError
import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.network.http.toAppError
import com.radustavila.nasaexplorer.data.mars.remote.dto.CuriosityPageDto
import com.radustavila.nasaexplorer.data.mars.remote.dto.PerseverancePageDto
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import retrofit2.HttpException

/** Remote boundary of the Mars module: every call returns a [DataResult], never throws. */
interface MarsRemoteDataSource {
    suspend fun fetchCuriosity(): DataResult<CuriosityPageDto>
    suspend fun fetchPerseverance(): DataResult<PerseverancePageDto>

    companion object {
        /** Images asked for per rover: about one sol's downlink, thumbnails included. */
        const val PAGE_SIZE = 50
    }
}

class RetrofitMarsRemoteDataSource @Inject constructor(
    private val service: MarsRawImagesService,
    @Dispatcher(AppDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
) : MarsRemoteDataSource {

    override suspend fun fetchCuriosity(): DataResult<CuriosityPageDto> =
        call { service.curiosity(perPage = MarsRemoteDataSource.PAGE_SIZE) }

    override suspend fun fetchPerseverance(): DataResult<PerseverancePageDto> =
        call { service.perseverance(count = MarsRemoteDataSource.PAGE_SIZE) }

    private suspend fun <T> call(block: suspend () -> T): DataResult<T> = withContext(ioDispatcher) {
        try {
            DataResult.Success(block())
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            DataResult.Failure(throwable.toMarsAppError())
        }
    }

    /**
     * mars.nasa.gov has no quota: its 403 is the CDN's answer when the feed's origin did not
     * respond, so it is a server failure, not the rate limit the shared mapping assumes (which
     * would hold off every Mars refresh for a day).
     */
    private fun Throwable.toMarsAppError(): AppError =
        if (this is HttpException && code() == HTTP_FORBIDDEN) {
            AppError.Http(code = HTTP_FORBIDDEN, message = "Raw image feed unavailable")
        } else {
            toAppError()
        }

    private companion object {
        const val HTTP_FORBIDDEN = 403
    }
}
