package com.radustavila.nasaexplorer.core.network.http

import com.radustavila.nasaexplorer.core.common.error.AppError
import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.network.ratelimit.RateLimitedException
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import retrofit2.HttpException

/**
 * Single translation point from transport exceptions to [AppError]. Nothing above the data layer
 * ever sees an `IOException` or an `HttpException`.
 */
fun Throwable.toAppError(): AppError = when (this) {
    // Before the IOException branch: a locally refused call is a quota problem, not a lost connection.
    is RateLimitedException -> AppError.RateLimited(retryAfterSeconds = retryAfterSeconds)
    is UnknownHostException, is ConnectException -> AppError.NoConnection
    is SocketTimeoutException -> AppError.Timeout
    is HttpException -> toAppError()
    is SerializationException -> AppError.Serialization(message)
    is IOException -> AppError.NoConnection
    else -> AppError.Unknown(message)
}

private fun HttpException.toAppError(): AppError = when (code()) {
    HTTP_TOO_MANY_REQUESTS -> AppError.RateLimited(
        retryAfterSeconds = response()?.headers()?.get("Retry-After")?.toLongOrNull(),
    )

    HTTP_FORBIDDEN -> AppError.RateLimited()
    else -> AppError.Http(code = code(), message = message())
}

/**
 * Runs a network call and normalises both failure modes: thrown transport exceptions and
 * non-2xx responses.
 *
 * [CancellationException] is rethrown so structured concurrency keeps working.
 */
suspend fun <T> safeNetworkCall(block: suspend () -> T): DataResult<T> = try {
    DataResult.Success(block())
} catch (cancellation: CancellationException) {
    throw cancellation
} catch (throwable: Throwable) {
    DataResult.Failure(throwable.toAppError())
}

private const val HTTP_TOO_MANY_REQUESTS = 429
private const val HTTP_FORBIDDEN = 403
