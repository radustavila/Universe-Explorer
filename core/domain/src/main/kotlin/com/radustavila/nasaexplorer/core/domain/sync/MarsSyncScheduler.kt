package com.radustavila.nasaexplorer.core.domain.sync

import kotlinx.coroutines.flow.Flow

/**
 * When the rover photos are refreshed: daily in the background, and on request (the Mars rail
 * appearing, pull-to-refresh). Requests go through the same background job, so a slow rover feed
 * never runs on a screen's scope; the job skips the download while the cache is fresh unless
 * [requestSync] is forced.
 */
interface MarsSyncScheduler {

    /** Whether a refresh is running right now. */
    val isSyncing: Flow<Boolean>

    /** @param force refresh even if the photos were refreshed recently (pull-to-refresh). */
    fun requestSync(force: Boolean = false)

    /** Makes sure the daily refresh is registered. Idempotent. */
    fun ensurePeriodicSync()
}
