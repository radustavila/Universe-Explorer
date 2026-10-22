package com.radustavila.nasaexplorer.data.forecast.alerts

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.WorkManager
import com.radustavila.nasaexplorer.core.domain.stargazing.StargazingAlertScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** WorkManager-backed [StargazingAlertScheduler]: one unique periodic job, kept while it exists. */
@Singleton
internal class WorkManagerStargazingAlertScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) : StargazingAlertScheduler {

    private val workManager: WorkManager get() = WorkManager.getInstance(context)

    override fun ensurePeriodicCheck() {
        workManager.enqueueUniquePeriodicWork(
            PERIODIC_CHECK_WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            StargazingAlertWorker.periodicRequest(),
        )
    }

    override fun cancelPeriodicCheck() {
        workManager.cancelUniqueWork(PERIODIC_CHECK_WORK)
    }

    companion object {
        const val PERIODIC_CHECK_WORK = "stargazing-alert-periodic"
    }
}
