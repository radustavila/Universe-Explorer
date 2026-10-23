package com.radustavila.nasaexplorer.data.webb.remote

import com.radustavila.nasaexplorer.core.common.dispatchers.AppDispatcher
import com.radustavila.nasaexplorer.core.common.dispatchers.Dispatcher
import com.radustavila.nasaexplorer.core.common.error.AppError
import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.common.result.flatMap
import com.radustavila.nasaexplorer.core.network.http.safeNetworkCall
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.ResponseBody

/** Remote boundary of the Webb module. Returns [DataResult] instead of throwing. */
interface WebbRemoteDataSource {

    /** Every weekly schedule file the index page links to, newest first; a page without any is a failure. */
    suspend fun fetchScheduleLinks(): DataResult<List<HttpUrl>>

    /** One week's prime visits; a file without any readable visit is a failure. */
    suspend fun fetchSchedule(url: HttpUrl): DataResult<List<ScheduledVisitDto>>
}

@Singleton
class RetrofitWebbRemoteDataSource @Inject constructor(
    private val service: StsciScheduleService,
    private val indexUrl: WebbScheduleIndexUrl,
    @Dispatcher(AppDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
) : WebbRemoteDataSource {

    override suspend fun fetchScheduleLinks(): DataResult<List<HttpUrl>> = withContext(ioDispatcher) {
        safeNetworkCall { service.fetch(indexUrl.url.toString()) }.flatMap { body ->
            read(body) { html -> WebbScheduleIndexParser.scheduleLinks(html, indexUrl.url) }
        }
    }

    override suspend fun fetchSchedule(url: HttpUrl): DataResult<List<ScheduledVisitDto>> = withContext(ioDispatcher) {
        safeNetworkCall { service.fetch(url.toString()) }.flatMap { body -> read(body, WebbScheduleParser::parse) }
    }

    /** The body as text through [parse]; nothing parsed is a format problem, like JSON that does not decode. */
    private fun <T> read(body: ResponseBody, parse: (String) -> List<T>): DataResult<List<T>> = try {
        val parsed = body.use { parse(it.string()) }
        if (parsed.isEmpty()) DataResult.Failure(AppError.Serialization(NOTHING_READ)) else DataResult.Success(parsed)
    } catch (_: IOException) {
        DataResult.Failure(AppError.NoConnection)
    }

    companion object {
        private const val NOTHING_READ = "No JWST schedule in the response"
    }
}
