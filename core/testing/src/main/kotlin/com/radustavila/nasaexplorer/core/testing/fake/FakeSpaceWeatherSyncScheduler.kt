package com.radustavila.nasaexplorer.core.testing.fake

import com.radustavila.nasaexplorer.core.domain.sync.SpaceWeatherSyncScheduler
import kotlinx.coroutines.flow.MutableStateFlow

class FakeSpaceWeatherSyncScheduler : SpaceWeatherSyncScheduler {

    override val isSyncing = MutableStateFlow(false)

    /** Every request, as (force, includeAuroraMap). */
    var requests: List<Pair<Boolean, Boolean>> = emptyList()
        private set

    var periodicSyncRegistrations = 0
        private set

    var periodicSyncCancellations = 0
        private set

    /** Whether the periodic refresh is registered: set by [ensurePeriodicSync], cleared by [cancelPeriodicSync]. */
    var isPeriodicSyncScheduled = false

    override fun requestSync(force: Boolean, includeAuroraMap: Boolean) {
        requests = requests + (force to includeAuroraMap)
    }

    override fun ensurePeriodicSync() {
        periodicSyncRegistrations++
        isPeriodicSyncScheduled = true
    }

    override fun cancelPeriodicSync() {
        periodicSyncCancellations++
        isPeriodicSyncScheduled = false
    }
}
