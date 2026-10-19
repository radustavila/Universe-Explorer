package com.radustavila.nasaexplorer.data.smallbodies.sync

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.radustavila.nasaexplorer.core.domain.sync.SmallBodiesSyncScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

/** WorkManager-backed [SmallBodiesSyncScheduler]: its own unique work, apart from every other sync. */
@Singleton
class WorkManagerSmallBodiesSyncScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) : SmallBodiesSyncScheduler {

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
            if (force) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
            SmallBodiesSyncWorker.oneTimeRequest(force),
        )
    }

    override fun ensurePeriodicSync() {
        workManager.enqueueUniquePeriodicWork(
            PERIODIC_SYNC_WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            SmallBodiesSyncWorker.periodicRequest(),
        )
    }

    companion object {
        const val ON_DEMAND_SYNC_WORK = "small-bodies-sync-on-demand"
        const val PERIODIC_SYNC_WORK = "small-bodies-sync-periodic"
    }
}
