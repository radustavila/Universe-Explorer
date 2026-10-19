package com.radustavila.nasaexplorer.data.nasa.sync

import androidx.work.Data
import androidx.work.workDataOf
import com.radustavila.nasaexplorer.core.common.error.AppError

/**
 * WorkManager can only carry primitives between a worker and its observers, so the failure reason is
 * encoded into output [Data] and decoded back into an [AppError]. Without this the UI would only
 * know "sync failed" and could not say why.
 */
internal object SyncErrorCodec {

    private const val KEY_ERROR_TYPE = "error_type"
    private const val KEY_HTTP_CODE = "http_code"
    private const val KEY_MESSAGE = "error_message"
    private const val KEY_RETRY_AFTER_SECONDS = "retry_after_seconds"
    private const val NO_RETRY_AFTER = -1L

    fun encode(error: AppError): Data = when (error) {
        AppError.NoConnection -> workDataOf(KEY_ERROR_TYPE to Type.NO_CONNECTION)
        AppError.Timeout -> workDataOf(KEY_ERROR_TYPE to Type.TIMEOUT)
        is AppError.Http -> workDataOf(
            KEY_ERROR_TYPE to Type.HTTP,
            KEY_HTTP_CODE to error.code,
            KEY_MESSAGE to error.message,
        )

        is AppError.RateLimited -> workDataOf(
            KEY_ERROR_TYPE to Type.RATE_LIMITED,
            KEY_RETRY_AFTER_SECONDS to (error.retryAfterSeconds ?: NO_RETRY_AFTER),
        )
        is AppError.Serialization -> workDataOf(
            KEY_ERROR_TYPE to Type.SERIALIZATION,
            KEY_MESSAGE to error.message,
        )

        is AppError.Database -> workDataOf(
            KEY_ERROR_TYPE to Type.DATABASE,
            KEY_MESSAGE to error.message,
        )

        AppError.EmptyResponse -> workDataOf(KEY_ERROR_TYPE to Type.EMPTY)
        is AppError.Unknown -> workDataOf(
            KEY_ERROR_TYPE to Type.UNKNOWN,
            KEY_MESSAGE to error.message,
        )
    }

    fun decode(data: Data): AppError? {
        val message = data.getString(KEY_MESSAGE)
        return when (data.getString(KEY_ERROR_TYPE)) {
            Type.NO_CONNECTION -> AppError.NoConnection
            Type.TIMEOUT -> AppError.Timeout
            Type.HTTP -> AppError.Http(code = data.getInt(KEY_HTTP_CODE, 0), message = message)
            Type.RATE_LIMITED -> AppError.RateLimited(
                retryAfterSeconds = data.getLong(KEY_RETRY_AFTER_SECONDS, NO_RETRY_AFTER)
                    .takeIf { it != NO_RETRY_AFTER },
            )
            Type.SERIALIZATION -> AppError.Serialization(message)
            Type.DATABASE -> AppError.Database(message)
            Type.EMPTY -> AppError.EmptyResponse
            Type.UNKNOWN -> AppError.Unknown(message)
            else -> null
        }
    }

    private object Type {
        const val NO_CONNECTION = "no_connection"
        const val TIMEOUT = "timeout"
        const val HTTP = "http"
        const val RATE_LIMITED = "rate_limited"
        const val SERIALIZATION = "serialization"
        const val DATABASE = "database"
        const val EMPTY = "empty"
        const val UNKNOWN = "unknown"
    }
}
