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

    override fun requestSync(force: Boolean, includeAuroraMap: Boolean) {
        requests = requests + (force to includeAuroraMap)
    }

    override fun ensurePeriodicSync() {
        periodicSyncRegistrations++
    }
}
