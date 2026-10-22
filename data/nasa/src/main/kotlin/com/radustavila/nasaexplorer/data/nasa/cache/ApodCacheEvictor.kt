package com.radustavila.nasaexplorer.data.nasa.cache

import com.radustavila.nasaexplorer.core.cache.CacheEvictor
import com.radustavila.nasaexplorer.core.common.dispatchers.AppDispatcher
import com.radustavila.nasaexplorer.core.common.dispatchers.Dispatcher
import com.radustavila.nasaexplorer.core.database.dao.ApodDao
import com.radustavila.nasaexplorer.core.domain.repository.SyncMetadataRepository
import com.radustavila.nasaexplorer.core.model.FavoriteSource
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Trims the APOD cache, which otherwise grows with every window the user pages through.
 *
 * - Feed rows are kept for [FEED_RETENTION_DAYS] of publication dates below the newest cached entry
 *   (not below today: a device that has been offline for months keeps its last quarter of the feed
 *   rather than losing everything). Trimming by date keeps the feed window contiguous, and the
 *   paging cursor is moved up to the new edge so "load more" re-fetches what was removed.
 * - Days opened on their own from the calendar are kept until they have not been refreshed for
 *   [ONE_OFF_RETENTION].
 * - Favourites are never deleted (their full entry stays readable offline); one older than the
 *   feed edge leaves the feed window instead of dangling below it.
 */
class ApodCacheEvictor @Inject constructor(
    private val apodDao: ApodDao,
    private val syncMetadataRepository: SyncMetadataRepository,
    @Dispatcher(AppDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
) : CacheEvictor {

    override val name: String = "apod"

    override suspend fun evict(now: Instant): Int = withContext(ioDispatcher) {
        val newest = apodDao.newestDate()?.let(LocalDate::parse)
            ?: return@withContext 0
        // A newest date past today (clock skew, time zones) must not drag the edge forward.
        val feedEdge = minOf(newest, now.atZone(ZoneOffset.UTC).toLocalDate())
            .minusDays(FEED_RETENTION_DAYS)

        // Cursor first: if the process dies between the two, "load more" re-fetches rows that are
        // still there, rather than skipping the ones that are gone.
        val oldestRequested = syncMetadataRepository.oldestRequestedDate.first()
        if (oldestRequested != null && oldestRequested.isBefore(feedEdge)) {
            syncMetadataRepository.setOldestRequestedDate(feedEdge)
        }

        apodDao.evict(
            cutoffDate = feedEdge.toString(),
            staleBeforeMillis = now.minus(ONE_OFF_RETENTION).toEpochMilli(),
            favoriteSource = FavoriteSource.APOD.key,
        )
    }

    companion object {
        const val FEED_RETENTION_DAYS = 90L
        val ONE_OFF_RETENTION: Duration = Duration.ofDays(30)
    }
}
