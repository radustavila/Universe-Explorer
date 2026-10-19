package com.radustavila.nasaexplorer.data.epic.sync

import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.radustavila.nasaexplorer.core.domain.sync.EpicSyncScheduler
import com.radustavila.nasaexplorer.core.model.SyncState
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * WorkManager-backed [EpicSyncScheduler]: its own unique on-demand and periodic work, so EPIC and
 * APOD syncs never replace or wait for each other.
 */
@Singleton
class WorkManagerEpicSyncScheduler @Inject constructor(
    private val workManager: WorkManager,
) : EpicSyncScheduler {

    override val syncState: Flow<SyncState> = combine(
        workManager.getWorkInfosForUniqueWorkFlow(ON_DEMAND_SYNC_WORK),
        workManager.getWorkInfosForUniqueWorkFlow(PERIODIC_SYNC_WORK),
    ) { onDemand, periodic -> combineEpicSyncStates(onDemand, periodic) }
        .distinctUntilChanged()

    override fun requestSync() {
        workManager.enqueueUniqueWork(
            ON_DEMAND_SYNC_WORK,
            ExistingWorkPolicy.KEEP,
            EpicSyncWorker.oneTimeRequest(),
        )
    }

    override fun ensurePeriodicSync() {
        workManager.enqueueUniquePeriodicWork(
            PERIODIC_SYNC_WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            EpicSyncWorker.periodicRequest(),
        )
    }

    companion object {
        const val ON_DEMAND_SYNC_WORK = "epic-sync-on-demand"
        const val PERIODIC_SYNC_WORK = "epic-sync-periodic"
    }
}

/**
 * Same folding rules as the APOD scheduler: a periodic request resting in ENQUEUED between runs is
 * not progress, and "something is happening" wins over past outcomes.
 */
internal fun combineEpicSyncStates(onDemand: List<WorkInfo>, periodic: List<WorkInfo>): SyncState {
    val infos = onDemand + periodic.filterNot { it.state == WorkInfo.State.ENQUEUED }
    return when {
        infos.isEmpty() -> SyncState.Idle
        infos.any { it.state == WorkInfo.State.RUNNING } -> SyncState.Syncing
        infos.any { it.state == WorkInfo.State.ENQUEUED && it.runAttemptCount == 0 } -> SyncState.Enqueued
        infos.any { it.state == WorkInfo.State.ENQUEUED && it.runAttemptCount > 0 } -> SyncState.Syncing
        infos.any { it.state == WorkInfo.State.FAILED } -> SyncState.Failed(
            error = infos.first { it.state == WorkInfo.State.FAILED }.outputData.let(EpicSyncErrorCodec::decode),
        )

        infos.any { it.state == WorkInfo.State.SUCCEEDED } -> SyncState.Succeeded
        else -> SyncState.Idle
    }
}
