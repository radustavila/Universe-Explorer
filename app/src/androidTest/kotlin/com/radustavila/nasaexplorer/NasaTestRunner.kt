package com.radustavila.nasaexplorer

import android.app.Application
import android.app.job.JobScheduler
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.test.runner.AndroidJUnitRunner
import androidx.work.Configuration
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import dagger.hilt.android.testing.HiltTestApplication

/**
 * Swaps [NasaExplorerApplication] for Hilt's test application so instrumentation tests get a
 * component they can override bindings in.
 *
 * The test runs in the app's own process, so JobScheduler may start WorkManager's
 * `SystemJobService` there for jobs the installed app scheduled earlier (periodic syncs). The
 * manifest removes WorkManager's default initializer and [HiltTestApplication] is no
 * `Configuration.Provider`, so that service would crash the process ("WorkManager is not
 * initialized properly") and the run would report no tests. The runner drops those leftover jobs
 * and gives the process a test WorkManager (in-memory database, nothing scheduled with the system).
 */
class NasaTestRunner : AndroidJUnitRunner() {

    override fun newApplication(
        classLoader: ClassLoader?,
        className: String?,
        context: Context?,
    ): Application = super.newApplication(classLoader, HiltTestApplication::class.java.name, context)

    override fun callApplicationOnCreate(app: Application) {
        val leftoverJobs = app.getSystemService(JobScheduler::class.java).cancelEverything()
        if (leftoverJobs > 0) Log.i(TAG, "Cancelled $leftoverJobs job(s) the installed app had scheduled")
        WorkManagerTestInitHelper.initializeTestWorkManager(
            app,
            Configuration.Builder()
                .setMinimumLoggingLevel(Log.DEBUG)
                .setExecutor(SynchronousExecutor())
                .build(),
        )
        super.callApplicationOnCreate(app)
    }

    /** WorkManager schedules in its own JobScheduler namespace from API 34; plain cancelAll() misses it. */
    private fun JobScheduler.cancelEverything(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            pendingJobsInAllNamespaces.values.sumOf { it.size }.also { cancelInAllNamespaces() }
        } else {
            allPendingJobs.size.also { cancelAll() }
        }

    private companion object {
        const val TAG = "NasaTestRunner"
    }
}
