package com.radustavila.nasaexplorer.core.domain.sync

import com.radustavila.nasaexplorer.core.common.result.DataResult
import java.time.Instant

/** Outcome of [com.radustavila.nasaexplorer.core.domain.usecase.RefreshSourceUseCase]. */
sealed interface SourceRefreshResult<out T> {

    /** The refresh ran; its outcome has been recorded. */
    data class Completed<T>(val result: DataResult<T>) : SourceRefreshResult<T>

    /** The source was not due (its interval or a rate-limit cooldown); nothing was called. */
    data class Skipped(val nextAllowedRefreshAt: Instant) : SourceRefreshResult<Nothing>
}
