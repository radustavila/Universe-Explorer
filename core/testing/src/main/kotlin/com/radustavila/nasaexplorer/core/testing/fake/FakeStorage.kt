package com.radustavila.nasaexplorer.core.testing.fake

import com.radustavila.nasaexplorer.core.domain.storage.CacheCleaner
import com.radustavila.nasaexplorer.core.domain.storage.StorageEntry
import com.radustavila.nasaexplorer.core.domain.storage.StorageKind
import com.radustavila.nasaexplorer.core.domain.storage.StorageUsageSource

/**
 * A cache that is both measured and cleared: [clear] drops its size to zero, as a real cache would.
 * Set [failure] to make both calls throw.
 */
class FakeCacheStorage(
    private val kind: StorageKind,
    var bytes: Long,
    /** Part that "Clear cache" may not delete, e.g. favourites in a feed's database. */
    private val keptBytes: Long = 0,
) : StorageUsageSource, CacheCleaner {

    var failure: Exception? = null
    var clearCount = 0
        private set

    override suspend fun measure(): List<StorageEntry> {
        failure?.let { throw it }
        return listOf(StorageEntry(kind, bytes))
    }

    override suspend fun clear() {
        failure?.let { throw it }
        clearCount++
        bytes = keptBytes
    }
}
