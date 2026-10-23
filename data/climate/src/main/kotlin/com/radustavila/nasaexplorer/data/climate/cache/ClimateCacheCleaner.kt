package com.radustavila.nasaexplorer.data.climate.cache

import com.radustavila.nasaexplorer.core.common.dispatchers.AppDispatcher
import com.radustavila.nasaexplorer.core.common.dispatchers.Dispatcher
import com.radustavila.nasaexplorer.core.domain.storage.CacheCleaner
import com.radustavila.nasaexplorer.data.climate.local.ClimateDao
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * "Clear cache" for the climate records: they hold no favourites or user data, so the table is
 * emptied; the next visit downloads the files again (Clear cache also makes every source due). No
 * evictor is needed: each refresh replaces an indicator's values.
 */
internal class ClimateCacheCleaner @Inject constructor(
    private val dao: ClimateDao,
    @Dispatcher(AppDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
) : CacheCleaner {

    override suspend fun clear() {
        withContext(ioDispatcher) { dao.clearAll() }
    }
}
