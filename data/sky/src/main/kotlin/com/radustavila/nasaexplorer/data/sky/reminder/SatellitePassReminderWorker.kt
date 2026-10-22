package com.radustavila.nasaexplorer.data.sky.reminder

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.radustavila.nasaexplorer.core.common.time.TimeProvider
import com.radustavila.nasaexplorer.core.domain.sky.SatellitePassReminderScheduler
import com.radustavila.nasaexplorer.core.domain.usecase.RemindOfSatellitePassUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/** Like [IssPassReminderWorker], for Hubble, Tiangong and Starlink trains. */
@HiltWorker
class SatellitePassReminderWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParameters: WorkerParameters,
    private val remindOfSatellitePass: RemindOfSatellitePassUseCase,
) : CoroutineWorker(appContext, workerParameters) {

    override suspend fun doWork(): Result {
        remindOfSatellitePass()
        return Result.success()
    }
}

/** One unique delayed job, apart from the ISS's so neither replaces the other. */
@Singleton
class WorkManagerSatellitePassReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val timeProvider: TimeProvider,
) : SatellitePassReminderScheduler {

    override fun scheduleAt(at: Instant) {
        val delay = Duration.between(timeProvider.now(), at).coerceAtLeast(Duration.ZERO)
        val request = OneTimeWorkRequestBuilder<SatellitePassReminderWorker>()
            .setInitialDelay(delay)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(UNIQUE_WORK, ExistingWorkPolicy.REPLACE, request)
    }

    override fun cancel() {
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK)
    }

    companion object {
        const val UNIQUE_WORK = "satellite-pass-reminder"
    }
}
