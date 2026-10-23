package com.radustavila.nasaexplorer.data.news.download

import com.radustavila.nasaexplorer.core.common.dispatchers.AppDispatcher
import com.radustavila.nasaexplorer.core.common.dispatchers.Dispatcher
import com.radustavila.nasaexplorer.core.domain.repository.FavoritesRepository
import com.radustavila.nasaexplorer.core.domain.repository.PodcastRepository
import com.radustavila.nasaexplorer.core.domain.storage.CacheCleaner
import com.radustavila.nasaexplorer.core.domain.storage.StorageEntry
import com.radustavila.nasaexplorer.core.domain.storage.StorageKind
import com.radustavila.nasaexplorer.core.domain.storage.StorageUsageSource
import com.radustavila.nasaexplorer.core.model.FavoriteSource
import com.radustavila.nasaexplorer.data.news.local.PodcastDao
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Downloaded episodes in Settings: measured as their own kind, and deleted by "Clear cache" except
 * those of favourite episodes, which the user saved on purpose.
 */
class PodcastDownloadStorage @Inject constructor(
    private val files: PodcastFiles,
    private val dao: PodcastDao,
    private val repository: PodcastRepository,
    private val favoritesRepository: FavoritesRepository,
    @Dispatcher(AppDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
) : StorageUsageSource, CacheCleaner {

    override suspend fun measure(): List<StorageEntry> = withContext(ioDispatcher) {
        listOf(StorageEntry(StorageKind.PODCAST_DOWNLOADS, files.sizeInBytes()))
    }

    override suspend fun clear() {
        val favorites = favoritesRepository.observeFavoriteIds(FavoriteSource.NASA_PODCAST_EPISODE).first()
        withContext(ioDispatcher) { dao.downloads() }
            .filter { it.episodeId !in favorites }
            .forEach { repository.removeDownload(it.episodeId) }
    }
}
