package com.radustavila.nasaexplorer.core.domain.sync

import kotlinx.coroutines.flow.Flow

/**
 * When space weather is refreshed: the Kp index every 30 minutes in the background, but only while
 * aurora alerts are on (it exists to drive them), and on request from the space weather screen, which
 * also wants the aurora map. Requests share the background job's connectivity constraint and backoff.
 */
interface SpaceWeatherSyncScheduler {

    /** Whether a refresh is running right now. */
    val isSyncing: Flow<Boolean>

    /**
     * @param force refresh even if the data is fresh (pull-to-refresh).
     * @param includeAuroraMap also download the aurora grid, which only the screen needs.
     */
    fun requestSync(force: Boolean = false, includeAuroraMap: Boolean = true)

    /** Makes sure the 30-minute refresh is registered. Idempotent. */
    fun ensurePeriodicSync()

    /** Stops the 30-minute refresh, if registered. Idempotent. */
    fun cancelPeriodicSync()
}
