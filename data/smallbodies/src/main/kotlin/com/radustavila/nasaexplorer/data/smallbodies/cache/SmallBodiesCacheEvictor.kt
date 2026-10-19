package com.radustavila.nasaexplorer.data.smallbodies.cache

import com.radustavila.nasaexplorer.core.cache.CacheEvictor
import com.radustavila.nasaexplorer.core.common.dispatchers.AppDispatcher
import com.radustavila.nasaexplorer.core.common.dispatchers.Dispatcher
import com.radustavila.nasaexplorer.data.smallbodies.local.SmallBodiesDao
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * Each refresh replaces the lists, so this only matters on a device that stopped refreshing:
 * approaches more than [PASSED_RETENTION] in the past go, and looked-up details older than
 * [DETAILS_RETENTION] go unless their object is still in the list. Nothing here can be a favourite.
 */
class SmallBodiesCacheEvictor @Inject constructor(
    private val dao: SmallBodiesDao,
    @Dispatcher(AppDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
) : CacheEvictor {

    override val name: String = "small_bodies"

    override suspend fun evict(now: Instant): Int = withContext(ioDispatcher) {
        dao.deleteCloseApproachesBefore(now.minus(PASSED_RETENTION).toEpochMilli()) +
            dao.deleteDetailsFetchedBefore(now.minus(DETAILS_RETENTION).toEpochMilli())
    }

    companion object {
        val PASSED_RETENTION: Duration = Duration.ofDays(7)
        val DETAILS_RETENTION: Duration = Duration.ofDays(30)
    }
}
