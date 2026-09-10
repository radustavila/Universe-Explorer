package com.radustavila.nasaexplorer.data.nasa.sync

import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.radustavila.nasaexplorer.core.domain.sync.SyncScheduler
import com.radustavila.nasaexplorer.core.model.SyncState
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * WorkManager-backed implementation of the domain's [SyncScheduler].
 *
 * Manual refresh and periodic background sync share the same worker, the same connectivity
 * constraint and the same exponential backoff. The UI only observes [syncState], so replacing
 * WorkManager with another strategy would not touch a single Composable.
 */
@Singleton
class WorkManagerSyncScheduler @Inject constructor(
    private val workManager: WorkManager,
) : SyncScheduler {

    override val syncState: Flow<SyncState> = combine(
        workManager.getWorkInfosForUniqueWorkFlow(ON_DEMAND_SYNC_WORK),
        workManager.getWorkInfosForUniqueWorkFlow(PERIODIC_SYNC_WORK),
    ) { onDemand, periodic -> (onDemand + periodic).toSyncState() }
        .distinctUntilChanged()

    override fun requestSync() {
        workManager.enqueueUniqueWork(
            ON_DEMAND_SYNC_WORK,
            // A sync already in flight is as good as the one being requested.
            ExistingWorkPolicy.KEEP,
            ApodSyncWorker.oneTimeRequest(),
        )
    }

    override fun ensurePeriodicSync() {
        workManager.enqueueUniquePeriodicWork(
            PERIODIC_SYNC_WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            ApodSyncWorker.periodicRequest(),
        )
    }

    companion object {
        const val ON_DEMAND_SYNC_WORK = "apod-sync-on-demand"
        const val PERIODIC_SYNC_WORK = "apod-sync-periodic"
    }
}

/**
 * Collapses the states of every sync work request into one observable state, favouring "something
 * is happening" over historical outcomes.
 */
internal fun List<WorkInfo>.toSyncState(): SyncState = when {
    isEmpty() -> SyncState.Idle
    any { it.state == WorkInfo.State.RUNNING } -> SyncState.Syncing
    any { it.state == WorkInfo.State.ENQUEUED && it.runAttemptCount == 0 } -> SyncState.Enqueued
    any { it.state == WorkInfo.State.ENQUEUED && it.runAttemptCount > 0 } -> SyncState.Syncing
    any { it.state == WorkInfo.State.FAILED } -> SyncState.Failed(
        error = firstOrNull { it.state == WorkInfo.State.FAILED }
            ?.outputData
            ?.let(SyncErrorCodec::decode),
    )

    any { it.state == WorkInfo.State.SUCCEEDED } -> SyncState.Succeeded
    else -> SyncState.Idle
}
