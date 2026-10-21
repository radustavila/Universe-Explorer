package com.radustavila.nasaexplorer.core.designsystem.error

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.res.stringResource
import com.radustavila.nasaexplorer.core.common.error.AppError
import com.radustavila.nasaexplorer.core.designsystem.R

/**
 * Presentation-ready error: a string resource plus its formatting arguments, and whether offering a
 * retry makes sense. ViewModels map [AppError] to this; Composables only resolve it.
 *
 * [formatArgs] holds plain values (codes, names) that are never mutated after mapping.
 */
@Immutable
data class UiError(
    @param:StringRes val messageRes: Int,
    val formatArgs: List<Any> = emptyList(),
    val isRetryable: Boolean = true,
)

fun AppError.toUiError(): UiError = when (this) {
    AppError.NoConnection -> UiError(R.string.error_no_connection)
    AppError.Timeout -> UiError(R.string.error_timeout)
    is AppError.Http -> UiError(R.string.error_http, listOf(code))
    is AppError.RateLimited -> UiError(R.string.error_rate_limited)
    is AppError.Serialization -> UiError(R.string.error_serialization, isRetryable = false)
    is AppError.Database -> UiError(R.string.error_database, isRetryable = false)
    AppError.EmptyResponse -> UiError(R.string.error_empty, isRetryable = false)
    is AppError.Unknown -> UiError(R.string.error_unknown)
}

@Composable
fun UiError.asText(): String = when {
    formatArgs.isEmpty() -> stringResource(messageRes)
    else -> stringResource(messageRes, *formatArgs.toTypedArray())
}
