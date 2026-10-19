package com.radustavila.nasaexplorer.core.testing.fake

import com.radustavila.nasaexplorer.core.domain.sync.EpicSyncScheduler
import com.radustavila.nasaexplorer.core.model.SyncState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeEpicSyncScheduler(initialState: SyncState = SyncState.Idle) : EpicSyncScheduler {

    private val state = MutableStateFlow(initialState)

    override val syncState: Flow<SyncState> = state

    var requestSyncCallCount: Int = 0
        private set
    var periodicSyncCallCount: Int = 0
        private set

    fun setState(syncState: SyncState) {
        state.value = syncState
    }

    override fun requestSync() {
        requestSyncCallCount++
    }

    override fun ensurePeriodicSync() {
        periodicSyncCallCount++
    }
}
