package com.radustavila.nasaexplorer.core.common.error

/**
 * Every failure the application can recognise, expressed in application terms.
 *
 * Infrastructure exceptions (`IOException`, `HttpException`, `SerializationException`,
 * `SQLiteException`, ...) are translated into these types at the edge of the data layer, so that
 * neither the domain nor the presentation layer ever sees a raw exception.
 */
sealed interface AppError {

    /** Device has no usable network connection. */
    data object NoConnection : AppError

    /** The request was sent but the server did not answer in time. */
    data object Timeout : AppError

    /** Server answered with a non-2xx status code. */
    data class Http(val code: Int, val message: String? = null) : AppError

    /**
     * A host's quota is used up: it answered HTTP 429, or the app refused the call locally because
     * the host is still cooling down (see `RateLimitInterceptor`). [retryAfterSeconds] is when it
     * accepts calls again, when known.
     */
    data class RateLimited(val retryAfterSeconds: Long? = null) : AppError

    /** The payload could not be decoded into the expected model. */
    data class Serialization(val message: String? = null) : AppError

    /** Local persistence failed. */
    data class Database(val message: String? = null) : AppError

    /** The call succeeded but there was nothing to show. */
    data object EmptyResponse : AppError

    /** Anything that could not be classified. */
    data class Unknown(val message: String? = null) : AppError

    /**
     * Whether retrying the same operation later has a realistic chance of succeeding. Used by the
     * synchronisation worker to decide between `Result.retry()` and `Result.failure()`.
     */
    val isRetryable: Boolean
        get() = when (this) {
            NoConnection, Timeout -> true
            is RateLimited -> true
            is Http -> code >= 500 || code == 408 || code == 429
            is Database, is Serialization, EmptyResponse, is Unknown -> false
        }
}
