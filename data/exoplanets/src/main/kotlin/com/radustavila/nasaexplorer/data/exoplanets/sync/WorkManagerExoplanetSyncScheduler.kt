package com.radustavila.nasaexplorer.data.exoplanets.sync

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.radustavila.nasaexplorer.core.domain.sync.ExoplanetSyncScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * WorkManager-backed [ExoplanetSyncScheduler]: its own unique work, separate from the APOD sync,
 * so a slow archive never holds up the feed and vice versa.
 */
@Singleton
class WorkManagerExoplanetSyncScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) : ExoplanetSyncScheduler {

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
            // A pending refresh is as good as a new one, unless the new one must bypass the
            // weekly interval: then it takes the pending one's place.
            if (force) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
            ExoplanetSyncWorker.oneTimeRequest(force),
        )
    }

    override fun ensurePeriodicSync() {
        workManager.enqueueUniquePeriodicWork(
            PERIODIC_SYNC_WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            ExoplanetSyncWorker.periodicRequest(),
        )
    }

    companion object {
        const val ON_DEMAND_SYNC_WORK = "exoplanet-sync-on-demand"
        const val PERIODIC_SYNC_WORK = "exoplanet-sync-periodic"
    }
}
