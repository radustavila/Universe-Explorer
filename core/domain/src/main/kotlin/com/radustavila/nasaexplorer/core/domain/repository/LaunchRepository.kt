package com.radustavila.nasaexplorer.core.domain.repository

import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.model.Launch
import kotlinx.coroutines.flow.Flow

/**
 * Upcoming rocket launches. Like [ApodRepository], reads come from the local cache and the network
 * only ever writes into it.
 */
interface LaunchRepository {

    /** Cached upcoming launches, soonest first. Emits whenever the cache changes. */
    fun observeUpcomingLaunches(): Flow<List<Launch>>

    /**
     * Replaces the cache with the current list of upcoming launches, unless the cache was refreshed
     * too recently. Launch Library 2 allows only a handful of anonymous calls per hour, so this makes
     * at most one network call and is a no-op inside the minimum refresh interval.
     *
     * On failure the cached launches are kept.
     */
    suspend fun refreshUpcomingLaunches(): DataResult<LaunchRefreshOutcome>
}

enum class LaunchRefreshOutcome {
    /** The network was called and the cache now holds its answer. */
    REFRESHED,

    /** The cache is still fresh enough; no network call was made. */
    SKIPPED,
}
