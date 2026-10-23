package com.radustavila.nasaexplorer.data.climate.remote

import com.radustavila.nasaexplorer.core.common.dispatchers.AppDispatcher
import com.radustavila.nasaexplorer.core.common.dispatchers.Dispatcher
import com.radustavila.nasaexplorer.core.common.error.AppError
import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.common.result.flatMap
import com.radustavila.nasaexplorer.core.model.ClimateIndicator
import com.radustavila.nasaexplorer.core.model.ClimatePoint
import com.radustavila.nasaexplorer.core.network.http.safeNetworkCall
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.ResponseBody
import retrofit2.http.GET
import retrofit2.http.Url

/**
 * The four public climate files (no key), each read whole by full URL: they are small (13–40 KB)
 * plain CSV on four hosts.
 */
interface ClimateFileService {
    @GET
    suspend fun fetch(@Url url: String): ResponseBody
}

/** Where each indicator's file lives; the app's are `BuildConfig` fields, tests point at a local server. */
data class ClimateSourceUrls(val urls: Map<ClimateIndicator, HttpUrl>) {
    init {
        require(urls.keys == ClimateIndicator.entries.toSet()) { "Every indicator needs a URL" }
    }

    operator fun get(indicator: ClimateIndicator): HttpUrl = urls.getValue(indicator)
}

/** Remote boundary of the climate module. Returns [DataResult] instead of throwing. */
interface ClimateRemoteDataSource {

    /** Every usable value of [indicator]'s file, oldest first; a file without any is a failure. */
    suspend fun fetch(indicator: ClimateIndicator): DataResult<List<ClimatePoint>>
}

@Singleton
class RetrofitClimateRemoteDataSource @Inject constructor(
    private val service: ClimateFileService,
    private val urls: ClimateSourceUrls,
    @Dispatcher(AppDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
) : ClimateRemoteDataSource {

    override suspend fun fetch(indicator: ClimateIndicator): DataResult<List<ClimatePoint>> =
        withContext(ioDispatcher) {
            safeNetworkCall { service.fetch(urls[indicator].toString()) }.flatMap { body -> read(indicator, body) }
        }

    /** Nothing parsed is a format problem, like JSON that does not decode. */
    private fun read(indicator: ClimateIndicator, body: ResponseBody): DataResult<List<ClimatePoint>> = try {
        val points = body.use { ClimateFileParser.parse(indicator, it.string()) }
        if (points.isEmpty()) DataResult.Failure(AppError.Serialization(NOTHING_READ)) else DataResult.Success(points)
    } catch (_: IOException) {
        DataResult.Failure(AppError.NoConnection)
    }

    companion object {
        private const val NOTHING_READ = "No climate values in the response"
    }
}
