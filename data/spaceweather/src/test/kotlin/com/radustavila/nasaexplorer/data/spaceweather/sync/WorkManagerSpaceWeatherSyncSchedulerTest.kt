package com.radustavila.nasaexplorer.data.spaceweather.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WorkManagerSpaceWeatherSyncSchedulerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val scheduler = WorkManagerSpaceWeatherSyncScheduler(context)

    @Before
    fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setExecutor(SynchronousExecutor()).build(),
        )
    }

    @Test
    fun `an on-demand request registers no periodic job`() {
        scheduler.requestSync(includeAuroraMap = true)

        assertEquals(emptyList<WorkInfo.State>(), periodicStates())
    }

    @Test
    fun `cancelling stops a registered periodic job`() {
        scheduler.ensurePeriodicSync()
        assertEquals(listOf(WorkInfo.State.ENQUEUED), periodicStates())

        scheduler.cancelPeriodicSync()

        assertEquals(listOf(WorkInfo.State.CANCELLED), periodicStates())
    }

    @Test
    fun `cancelling without a periodic job is harmless`() {
        scheduler.cancelPeriodicSync()

        assertEquals(emptyList<WorkInfo.State>(), periodicStates())
    }

    private fun periodicStates(): List<WorkInfo.State> =
        WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork(WorkManagerSpaceWeatherSyncScheduler.PERIODIC_SYNC_WORK)
            .get()
            .map { it.state }
}
