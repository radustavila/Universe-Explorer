package com.radustavila.nasaexplorer.core.domain.usecase

import com.radustavila.nasaexplorer.core.domain.sync.EpicSyncScheduler
import com.radustavila.nasaexplorer.core.model.SyncState
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class ObserveEpicSyncStateUseCase @Inject constructor(
    private val epicSyncScheduler: EpicSyncScheduler,
) {
    operator fun invoke(): Flow<SyncState> = epicSyncScheduler.syncState
}
