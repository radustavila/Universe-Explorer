package com.radustavila.nasaexplorer.core.domain.repository

import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.model.Launch
import kotlinx.coroutines.flow.Flow

/**
 * Rocket launches from Launch Library 2. Like [ApodRepository], reads come from the local cache and
 * the network only ever writes into it.
 *
 * Every refresh is exactly one call against Launch Library 2's 15-an-hour budget, which the card,
 * the crew list and the Launches screen share. How often each may run is the callers' decision
 * (`SynchronizeUpcomingLaunchesUseCase`, `RefreshPastLaunchesUseCase`, `RemindOfLaunchesUseCase`).
 */
interface LaunchRepository {

    /** Cached upcoming launches, soonest first. Emits whenever the cache changes. */
    fun observeUpcomingLaunches(): Flow<List<Launch>>

    /** Cached past launches, most recent first. */
    fun observePastLaunches(): Flow<List<Launch>>

    /** One cached launch, upcoming or past; null when it is not cached. */
    fun observeLaunch(id: String): Flow<Launch?>

    /**
     * Replaces the cached upcoming launches with the current list (the next 30, in detail). On
     * failure the cached launches are kept.
     *
     * @return the number of upcoming launches now cached.
     */
    suspend fun refreshUpcomingLaunches(): DataResult<Int>

    /** Replaces the cached past launches with the latest ones (the last 20). */
    suspend fun refreshPastLaunches(): DataResult<Int>

    /**
     * Fetches one launch again and updates its cached row, keeping it in the list it is in. Only
     * used to re-check a launch shortly before its reminder.
     *
     * @return the launch as it is now.
     */
    suspend fun refreshLaunch(id: String): DataResult<Launch>
}
