package com.radustavila.nasaexplorer.core.network.http

import com.radustavila.nasaexplorer.core.common.error.AppError
import com.radustavila.nasaexplorer.core.common.result.DataResult
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerializationException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

class NetworkErrorMapperTest {

    @Test
    fun `transport failures become application errors`() {
        assertEquals(AppError.NoConnection, UnknownHostException().toAppError())
        assertEquals(AppError.Timeout, SocketTimeoutException().toAppError())
        assertEquals(AppError.NoConnection, IOException("socket closed").toAppError())
    }

    @Test
    fun `http failures keep their status code`() {
        assertEquals(
            AppError.Http(code = 500, message = "Response.error()"),
            httpException(500).toAppError(),
        )
    }

    @Test
    fun `quota responses become rate limiting`() {
        assertTrue(httpException(429).toAppError() is AppError.RateLimited)
        assertTrue(httpException(403).toAppError() is AppError.RateLimited)
    }

    @Test
    fun `decoding failures become serialization errors`() {
        assertTrue(SerializationException("unexpected token").toAppError() is AppError.Serialization)
    }

    @Test
    fun `safeNetworkCall wraps success and failure`() = runTest {
        assertEquals(DataResult.Success(42), safeNetworkCall { 42 })
        assertEquals(
            DataResult.Failure(AppError.NoConnection),
            safeNetworkCall<Int> { throw UnknownHostException() },
        )
    }

    @Test(expected = CancellationException::class)
    fun `safeNetworkCall never swallows cancellation`() = runTest {
        safeNetworkCall<Int> { throw CancellationException("cancelled") }
    }

    private fun httpException(code: Int) = HttpException(
        Response.error<Any>(code, "".toResponseBody("application/json".toMediaType())),
    )
}
