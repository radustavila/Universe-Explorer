package com.radustavila.nasaexplorer.data.epic.sync

import androidx.work.Data
import androidx.work.workDataOf
import com.radustavila.nasaexplorer.core.common.error.AppError

/**
 * Carries a sync failure's [AppError] through WorkManager's primitive-only output [Data], so the
 * Earth screen can say *why* the sync failed. Mirrors the APOD module's codec, which is internal to
 * `:data:nasa`.
 */
internal object EpicSyncErrorCodec {

    private const val KEY_ERROR_TYPE = "error_type"
    private const val KEY_HTTP_CODE = "http_code"
    private const val KEY_MESSAGE = "error_message"

    fun encode(error: AppError): Data = when (error) {
        AppError.NoConnection -> workDataOf(KEY_ERROR_TYPE to Type.NO_CONNECTION)
        AppError.Timeout -> workDataOf(KEY_ERROR_TYPE to Type.TIMEOUT)
        is AppError.Http -> workDataOf(
            KEY_ERROR_TYPE to Type.HTTP,
            KEY_HTTP_CODE to error.code,
            KEY_MESSAGE to error.message,
        )

        is AppError.RateLimited -> workDataOf(KEY_ERROR_TYPE to Type.RATE_LIMITED)
        is AppError.Serialization -> workDataOf(KEY_ERROR_TYPE to Type.SERIALIZATION, KEY_MESSAGE to error.message)
        is AppError.Database -> workDataOf(KEY_ERROR_TYPE to Type.DATABASE, KEY_MESSAGE to error.message)
        AppError.EmptyResponse -> workDataOf(KEY_ERROR_TYPE to Type.EMPTY)
        is AppError.Unknown -> workDataOf(KEY_ERROR_TYPE to Type.UNKNOWN, KEY_MESSAGE to error.message)
    }

    fun decode(data: Data): AppError? {
        val message = data.getString(KEY_MESSAGE)
        return when (data.getString(KEY_ERROR_TYPE)) {
            Type.NO_CONNECTION -> AppError.NoConnection
            Type.TIMEOUT -> AppError.Timeout
            Type.HTTP -> AppError.Http(code = data.getInt(KEY_HTTP_CODE, 0), message = message)
            Type.RATE_LIMITED -> AppError.RateLimited()
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
