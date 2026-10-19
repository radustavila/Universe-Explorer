package com.radustavila.nasaexplorer.core.domain.sync

import kotlinx.coroutines.flow.Flow

/** When asteroid close approaches and fireballs are refreshed: daily, and on request. */
interface SmallBodiesSyncScheduler {

    /** Whether a refresh is running right now. */
    val isSyncing: Flow<Boolean>

    /** @param force refresh even if the lists were refreshed today (pull-to-refresh). */
    fun requestSync(force: Boolean = false)

    /** Makes sure the daily refresh is registered. Idempotent. */
    fun ensurePeriodicSync()
}
