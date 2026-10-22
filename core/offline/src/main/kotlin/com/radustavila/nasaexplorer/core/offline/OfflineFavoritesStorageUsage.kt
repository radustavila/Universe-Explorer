package com.radustavila.nasaexplorer.core.offline

import com.radustavila.nasaexplorer.core.domain.offline.OfflineFavoritesStorage
import com.radustavila.nasaexplorer.core.domain.storage.StorageEntry
import com.radustavila.nasaexplorer.core.domain.storage.StorageKind
import com.radustavila.nasaexplorer.core.domain.storage.StorageUsageSource
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/**
 * The offline copies of favourite images, measured for Settings. Deliberately not a `CacheCleaner`:
 * "Clear cache" never deletes them; they go with their favourite.
 */
class OfflineFavoritesStorageUsage @Inject constructor(
    private val storage: OfflineFavoritesStorage,
) : StorageUsageSource {

    override suspend fun measure(): List<StorageEntry> =
        listOf(StorageEntry(StorageKind.OFFLINE_FAVORITES, storage.observeSizeBytes().first()))
}
