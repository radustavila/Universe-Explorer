package com.radustavila.nasaexplorer.core.cache

import java.time.Instant

/**
 * One cache that [CacheEvictionWorker] trims periodically, typically one Room table.
 *
 * A data module registers its evictor next to its other bindings:
 *
 * ```
 * @Binds @IntoSet
 * fun bindEpicCacheEvictor(impl: EpicCacheEvictor): CacheEvictor
 * ```
 *
 * Implementations own their retention rule ("rows older than N days") and must never delete a row
 * that is a favourite (see the `favorite` table), nor leave paging bookkeeping pointing at rows
 * they removed. The offline copies of favourite images under `filesDir/favorites` are not a cache
 * and are off limits: they are deleted only with their favourite.
 */
interface CacheEvictor {

    /** Short stable name, used in the worker's output and logs. */
    val name: String

    /**
     * Deletes whatever this cache no longer needs at [now].
     *
     * @return how many entries were removed.
     */
    suspend fun evict(now: Instant): Int
}
