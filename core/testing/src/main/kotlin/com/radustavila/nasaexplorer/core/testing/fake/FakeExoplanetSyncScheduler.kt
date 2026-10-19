package com.radustavila.nasaexplorer.core.testing.fake

import com.radustavila.nasaexplorer.core.domain.sync.ExoplanetSyncScheduler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeExoplanetSyncScheduler(isSyncing: Boolean = false) : ExoplanetSyncScheduler {

    private val syncing = MutableStateFlow(isSyncing)

    override val isSyncing: Flow<Boolean> = syncing

    /** The `force` flag of every request, in order. */
    val requests: MutableList<Boolean> = mutableListOf()
    var periodicSyncCallCount: Int = 0
        private set

    fun setSyncing(value: Boolean) {
        syncing.value = value
    }

    override fun requestSync(force: Boolean) {
        requests += force
    }

    override fun ensurePeriodicSync() {
        periodicSyncCallCount++
    }
}
