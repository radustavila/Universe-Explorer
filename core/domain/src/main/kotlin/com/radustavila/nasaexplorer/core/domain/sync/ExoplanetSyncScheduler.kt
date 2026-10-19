package com.radustavila.nasaexplorer.core.domain.sync

import kotlinx.coroutines.flow.Flow

/**
 * When the exoplanet catalogue is refreshed: weekly in the background, and on request (opening the
 * screen, pull-to-refresh). Requests go through the same background job, so they share its
 * connectivity constraint and backoff; the job skips the download while the catalogue is fresh
 * unless [requestSync] is forced.
 */
interface ExoplanetSyncScheduler {

    /** Whether a refresh is running right now. */
    val isSyncing: Flow<Boolean>

    /** @param force refresh even if the catalogue was refreshed recently (pull-to-refresh). */
    fun requestSync(force: Boolean = false)

    /** Makes sure the weekly refresh is registered. Idempotent. */
    fun ensurePeriodicSync()
}
