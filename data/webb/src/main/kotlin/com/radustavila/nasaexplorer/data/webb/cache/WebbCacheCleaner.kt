package com.radustavila.nasaexplorer.data.webb.cache

import com.radustavila.nasaexplorer.core.common.dispatchers.AppDispatcher
import com.radustavila.nasaexplorer.core.common.dispatchers.Dispatcher
import com.radustavila.nasaexplorer.core.domain.storage.CacheCleaner
import com.radustavila.nasaexplorer.data.webb.local.WebbDao
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * "Clear cache" for the Webb schedule: it holds no favourites or user data, so the table is emptied;
 * the next visit downloads it again (Clear cache also makes every source due). No evictor is
 * needed: each refresh replaces the whole week.
 */
internal class WebbCacheCleaner @Inject constructor(
    private val dao: WebbDao,
    @Dispatcher(AppDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
) : CacheCleaner {

    override suspend fun clear() {
        withContext(ioDispatcher) { dao.clearAll() }
    }
}
