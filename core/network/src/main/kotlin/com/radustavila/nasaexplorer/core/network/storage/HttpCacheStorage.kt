package com.radustavila.nasaexplorer.core.network.storage

import com.radustavila.nasaexplorer.core.common.dispatchers.AppDispatcher
import com.radustavila.nasaexplorer.core.common.dispatchers.Dispatcher
import com.radustavila.nasaexplorer.core.domain.storage.CacheCleaner
import com.radustavila.nasaexplorer.core.domain.storage.StorageEntry
import com.radustavila.nasaexplorer.core.domain.storage.StorageKind
import com.radustavila.nasaexplorer.core.domain.storage.StorageUsageSource
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

/** The shared client's HTTP cache: measured for Settings and emptied by "Clear cache". */
class HttpCacheStorage @Inject constructor(
    private val client: dagger.Lazy<OkHttpClient>,
    @Dispatcher(AppDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
) : StorageUsageSource, CacheCleaner {

    override suspend fun measure(): List<StorageEntry> = withContext(ioDispatcher) {
        listOf(StorageEntry(StorageKind.HTTP_CACHE, client.get().cache?.size() ?: 0L))
    }

    override suspend fun clear() = withContext(ioDispatcher) {
        client.get().cache?.evictAll()
        Unit
    }
}
