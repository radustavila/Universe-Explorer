package com.radustavila.nasaexplorer.core.common.result

import com.radustavila.nasaexplorer.core.common.error.AppError

/**
 * Result type used for every one-shot operation that crosses a layer boundary.
 *
 * Kotlin's own [kotlin.Result] is deliberately not used: it carries a [Throwable], which would leak
 * infrastructure details upwards. [DataResult] carries an [AppError] instead.
 */
sealed interface DataResult<out T> {

    data class Success<out T>(val data: T) : DataResult<T>

    data class Failure(val error: AppError) : DataResult<Nothing>

    val isSuccess: Boolean get() = this is Success
}

inline fun <T, R> DataResult<T>.map(transform: (T) -> R): DataResult<R> = when (this) {
    is DataResult.Success -> DataResult.Success(transform(data))
    is DataResult.Failure -> this
}

inline fun <T, R> DataResult<T>.flatMap(transform: (T) -> DataResult<R>): DataResult<R> =
    when (this) {
        is DataResult.Success -> transform(data)
        is DataResult.Failure -> this
    }

inline fun <T> DataResult<T>.onSuccess(action: (T) -> Unit): DataResult<T> = apply {
    if (this is DataResult.Success) action(data)
}

inline fun <T> DataResult<T>.onFailure(action: (AppError) -> Unit): DataResult<T> = apply {
    if (this is DataResult.Failure) action(error)
}

fun <T> DataResult<T>.getOrNull(): T? = (this as? DataResult.Success)?.data

fun <T> DataResult<T>.errorOrNull(): AppError? = (this as? DataResult.Failure)?.error

fun <T> T.asSuccess(): DataResult<T> = DataResult.Success(this)

fun AppError.asFailure(): DataResult<Nothing> = DataResult.Failure(this)
