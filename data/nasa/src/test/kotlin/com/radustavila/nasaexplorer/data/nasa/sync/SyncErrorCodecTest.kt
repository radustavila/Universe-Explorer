package com.radustavila.nasaexplorer.data.nasa.sync

import com.radustavila.nasaexplorer.core.common.error.AppError
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SyncErrorCodecTest {

    @Test
    fun `every error survives the round trip through WorkManager output data`() {
        val errors = listOf(
            AppError.NoConnection,
            AppError.Timeout,
            AppError.Http(code = 503, message = "Service Unavailable"),
            AppError.RateLimited(),
            AppError.RateLimited(retryAfterSeconds = 120),
            AppError.Serialization("bad json"),
            AppError.Database("disk full"),
            AppError.EmptyResponse,
            AppError.Unknown("boom"),
        )

        errors.forEach { error ->
            assertEquals(error, SyncErrorCodec.decode(SyncErrorCodec.encode(error)))
        }
    }
}
