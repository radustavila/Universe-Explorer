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
     * Replaces the cache with the current list of upcoming launches in exactly one network call. How
     * often that may happen is the caller's decision (see `SynchronizeUpcomingLaunchesUseCase`).
     *
     * On failure the cached launches are kept.
     *
     * @return the number of launches now cached.
     */
    suspend fun refreshUpcomingLaunches(): DataResult<Int>
}
