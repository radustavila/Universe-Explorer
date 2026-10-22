package com.radustavila.nasaexplorer.data.spaceweather.sun

import com.radustavila.nasaexplorer.core.cache.CacheEvictor
import com.radustavila.nasaexplorer.core.domain.storage.CacheCleaner
import com.radustavila.nasaexplorer.core.domain.storage.StorageEntry
import com.radustavila.nasaexplorer.core.domain.storage.StorageKind
import com.radustavila.nasaexplorer.core.domain.storage.StorageUsageSource
import java.time.Duration
import java.time.Instant
import javax.inject.Inject

/**
 * Drops pictures of channels nobody has looked at for [RETENTION]: SDO has newer ones by then. The
 * newest picture stays whatever its age, so the card is never empty offline.
 */
class SunImageCacheEvictor @Inject constructor(
    private val files: SunImageFiles,
) : CacheEvictor {

    override val name: String = "sun_images"

    override suspend fun evict(now: Instant): Int = files.evict(now, RETENTION)

    companion object {
        val RETENTION: Duration = Duration.ofDays(2)
    }
}

/** The SDO pictures count as downloaded images in Settings, and "Clear cache" deletes them all. */
class SunImageStorage @Inject constructor(
    private val files: SunImageFiles,
) : StorageUsageSource, CacheCleaner {

    override suspend fun measure(): List<StorageEntry> =
        listOf(StorageEntry(StorageKind.IMAGE_CACHE, files.sizeInBytes()))

    override suspend fun clear() = files.clear()
}
