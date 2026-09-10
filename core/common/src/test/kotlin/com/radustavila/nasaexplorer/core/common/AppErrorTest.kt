package com.radustavila.nasaexplorer.core.common

import com.radustavila.nasaexplorer.core.common.error.AppError
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `isRetryable` decides whether the sync worker burns a retry, so it is a business rule rather than
 * a property accessor.
 */
class AppErrorTest {

    @Test
    fun `transient failures are retryable`() {
        assertTrue(AppError.NoConnection.isRetryable)
        assertTrue(AppError.Timeout.isRetryable)
        assertTrue(AppError.RateLimited().isRetryable)
        assertTrue(AppError.Http(code = 503).isRetryable)
        assertTrue(AppError.Http(code = 429).isRetryable)
    }

    @Test
    fun `permanent failures are not retryable`() {
        assertFalse(AppError.Http(code = 404).isRetryable)
        assertFalse(AppError.Http(code = 400).isRetryable)
        assertFalse(AppError.Serialization("boom").isRetryable)
        assertFalse(AppError.Database("boom").isRetryable)
        assertFalse(AppError.EmptyResponse.isRetryable)
        assertFalse(AppError.Unknown().isRetryable)
    }
}
