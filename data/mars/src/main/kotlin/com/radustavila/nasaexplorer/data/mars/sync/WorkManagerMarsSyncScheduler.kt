package com.radustavila.nasaexplorer.data.mars.sync

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.radustavila.nasaexplorer.core.domain.sync.MarsSyncScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * WorkManager-backed [MarsSyncScheduler]: unique work of its own, separate from the APOD sync, so
 * Perseverance's slow feed never holds up the feed and vice versa.
 */
@Singleton
class WorkManagerMarsSyncScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) : MarsSyncScheduler {

    private val workManager: WorkManager get() = WorkManager.getInstance(context)

    override val isSyncing: Flow<Boolean> by lazy {
        combine(
            workManager.getWorkInfosForUniqueWorkFlow(ON_DEMAND_SYNC_WORK),
            workManager.getWorkInfosForUniqueWorkFlow(PERIODIC_SYNC_WORK),
        ) { onDemand, periodic -> (onDemand + periodic).any { it.state == WorkInfo.State.RUNNING } }
            .distinctUntilChanged()
    }

    override fun requestSync(force: Boolean) {
        workManager.enqueueUniqueWork(
            ON_DEMAND_SYNC_WORK,
            // A pending refresh is as good as a new one, unless the new one must bypass the daily
            // interval: then it takes the pending one's place.
            if (force) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
            MarsSyncWorker.oneTimeRequest(force),
        )
    }

    override fun ensurePeriodicSync() {
        workManager.enqueueUniquePeriodicWork(
            PERIODIC_SYNC_WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            MarsSyncWorker.periodicRequest(),
        )
    }

    companion object {
        const val ON_DEMAND_SYNC_WORK = "mars-sync-on-demand"
        const val PERIODIC_SYNC_WORK = "mars-sync-periodic"
    }
}
