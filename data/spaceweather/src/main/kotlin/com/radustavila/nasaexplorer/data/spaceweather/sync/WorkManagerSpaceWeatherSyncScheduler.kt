package com.radustavila.nasaexplorer.data.spaceweather.sync

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.radustavila.nasaexplorer.core.domain.sync.SpaceWeatherSyncScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * WorkManager-backed [SpaceWeatherSyncScheduler]: its own unique work, apart from the APOD sync, so
 * the 30-minute Kp refresh never queues behind the feed and vice versa.
 */
@Singleton
class WorkManagerSpaceWeatherSyncScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) : SpaceWeatherSyncScheduler {

    private val workManager: WorkManager get() = WorkManager.getInstance(context)

    override val isSyncing: Flow<Boolean> by lazy {
        combine(
            workManager.getWorkInfosForUniqueWorkFlow(ON_DEMAND_SYNC_WORK),
            workManager.getWorkInfosForUniqueWorkFlow(PERIODIC_SYNC_WORK),
        ) { onDemand, periodic -> (onDemand + periodic).any { it.state == WorkInfo.State.RUNNING } }
            .distinctUntilChanged()
    }

    override fun requestSync(force: Boolean, includeAuroraMap: Boolean) {
        workManager.enqueueUniqueWork(
            ON_DEMAND_SYNC_WORK,
            // A pending refresh is as good as a new one, unless the new one must bypass the interval.
            if (force) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
            SpaceWeatherSyncWorker.oneTimeRequest(force, includeAuroraMap),
        )
    }

    override fun ensurePeriodicSync() {
        workManager.enqueueUniquePeriodicWork(
            PERIODIC_SYNC_WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            SpaceWeatherSyncWorker.periodicRequest(),
        )
    }

    override fun cancelPeriodicSync() {
        workManager.cancelUniqueWork(PERIODIC_SYNC_WORK)
    }

    companion object {
        const val ON_DEMAND_SYNC_WORK = "space-weather-sync-on-demand"
        const val PERIODIC_SYNC_WORK = "space-weather-sync-periodic"
    }
}
