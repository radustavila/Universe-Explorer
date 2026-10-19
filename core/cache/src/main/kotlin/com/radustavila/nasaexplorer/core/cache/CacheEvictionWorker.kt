package com.radustavila.nasaexplorer.core.cache

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.radustavila.nasaexplorer.core.common.time.TimeProvider
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.Duration
import kotlinx.coroutines.CancellationException

/**
 * Periodic cache maintenance: runs every registered [CacheEvictor].
 *
 * Evictors are independent: one that fails does not stop the others. The run reports failure when
 * any of them failed (the next period simply tries again) and records how many entries each one
 * removed in its output data under `evicted_<name>`.
 *
 * Images are not handled here: Coil's disk cache is an LRU bounded by size (see
 * `ImageLoaderModule`), so it trims itself.
 */
@HiltWorker
class CacheEvictionWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParameters: WorkerParameters,
    private val evictors: Set<@JvmSuppressWildcards CacheEvictor>,
    private val timeProvider: TimeProvider,
) : CoroutineWorker(appContext, workerParameters) {

    override suspend fun doWork(): Result {
        val now = timeProvider.now()
        val evicted = mutableMapOf<String, Any?>()
        val failed = mutableListOf<String>()

        evictors.sortedBy(CacheEvictor::name).forEach { evictor ->
            try {
                evicted[KEY_EVICTED_PREFIX + evictor.name] = evictor.evict(now)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                failed += evictor.name
            }
        }

        val output = workDataOf(
            *evicted.toList().toTypedArray(),
            KEY_FAILED to failed.toTypedArray(),
        )
        return if (failed.isEmpty()) Result.success(output) else Result.failure(output)
    }

    companion object {
        const val KEY_EVICTED_PREFIX = "evicted_"
        const val KEY_FAILED = "failed"

        private val INTERVAL: Duration = Duration.ofDays(1)

        fun periodicRequest() = PeriodicWorkRequestBuilder<CacheEvictionWorker>(INTERVAL)
            // Not right at app start, where the first sync is busy writing.
            .setInitialDelay(Duration.ofHours(1))
            .setConstraints(
                Constraints.Builder()
                    .setRequiresBatteryNotLow(true)
                    .build(),
            )
            .build()
    }
}
