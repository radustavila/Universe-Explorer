package com.radustavila.nasaexplorer.core.cache

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Registers the daily [CacheEvictionWorker]. Idempotent, so it is safe on every app start. */
@Singleton
class CacheEvictionScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun ensurePeriodicEviction() {
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_EVICTION_WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            CacheEvictionWorker.periodicRequest(),
        )
    }

    companion object {
        const val PERIODIC_EVICTION_WORK = "cache-eviction-periodic"
    }
}
