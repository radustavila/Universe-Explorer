package com.radustavila.nasaexplorer.core.testing.fake

import com.radustavila.nasaexplorer.core.domain.sync.MarsSyncScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class FakeMarsSyncScheduler : MarsSyncScheduler {

    private val syncing = MutableStateFlow(false)

    override val isSyncing: StateFlow<Boolean> = syncing

    /** The `force` flag of every [requestSync], in order. */
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
