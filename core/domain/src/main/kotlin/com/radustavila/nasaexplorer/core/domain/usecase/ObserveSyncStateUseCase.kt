package com.radustavila.nasaexplorer.core.domain.usecase

import com.radustavila.nasaexplorer.core.domain.sync.SyncScheduler
import com.radustavila.nasaexplorer.core.model.SyncState
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class ObserveSyncStateUseCase @Inject constructor(
    private val syncScheduler: SyncScheduler,
) {
    operator fun invoke(): Flow<SyncState> = syncScheduler.syncState
}
