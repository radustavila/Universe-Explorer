package com.radustavila.nasaexplorer.core.testing.fake

import com.radustavila.nasaexplorer.core.domain.sync.SmallBodiesSyncScheduler
import kotlinx.coroutines.flow.MutableStateFlow

class FakeSmallBodiesSyncScheduler : SmallBodiesSyncScheduler {

    override val isSyncing = MutableStateFlow(false)

    /** The `force` flag of every request. */
    var requests: List<Boolean> = emptyList()
        private set

    var periodicSyncRegistrations = 0
        private set

    override fun requestSync(force: Boolean) {
        requests = requests + force
    }

    override fun ensurePeriodicSync() {
        periodicSyncRegistrations++
    }
}
