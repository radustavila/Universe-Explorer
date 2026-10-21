package com.radustavila.nasaexplorer.core.offline

import androidx.work.ExistingWorkPolicy
import androidx.work.WorkManager
import com.radustavila.nasaexplorer.core.common.dispatchers.AppDispatcher
import com.radustavila.nasaexplorer.core.common.dispatchers.Dispatcher
import com.radustavila.nasaexplorer.core.database.dao.FavoriteDao
import com.radustavila.nasaexplorer.core.domain.offline.OfflineFavoriteImages
import com.radustavila.nasaexplorer.core.model.FavoriteSource
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * [OfflineFavoriteImages] on WorkManager: one unique [FavoriteImageDownloadWorker] per favourite.
 *
 * [WorkManager] is looked up on first use rather than when the favourites repository is created, so
 * opening the app does not initialise it any earlier than it already does.
 */
@Singleton
class WorkManagerOfflineFavoriteImages @Inject constructor(
    private val workManager: dagger.Lazy<WorkManager>,
    private val favoriteDao: FavoriteDao,
    private val files: FavoriteImageFiles,
    @Dispatcher(AppDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
) : OfflineFavoriteImages {

    override fun requestDownload(source: FavoriteSource, itemId: String) {
        // Replace: a save that changed the snapshot image must not wait behind a download of the old one.
        enqueue(source, itemId, ExistingWorkPolicy.REPLACE)
    }

    override suspend fun discard(source: FavoriteSource, itemId: String) {
        workManager.get().cancelUniqueWork(FavoriteImageDownloadWorker.uniqueWorkName(source, itemId))
        withContext(ioDispatcher) { files.deleteAll(source, itemId) }
    }

    override suspend fun backfillOnce() {
        val missing = withContext(ioDispatcher) {
            if (files.backfillMarker.exists()) return@withContext null
            // The marker is missing on a fresh install and after a restore from backup: the
            // database comes back with its recorded copies, the files under filesDir do not.
            favoriteDao.findWithoutLocalImage() + favoriteDao.findWithLocalImage().filter { favorite ->
                favorite.localImagePath?.let { files.resolve(it).isFile } != true
            }
        } ?: return

        missing.forEach { favorite ->
            val source = FavoriteSource.fromKey(favorite.source) ?: return@forEach
            // Keep: a download the user already triggered stays as it is.
            enqueue(source, favorite.itemId, ExistingWorkPolicy.KEEP)
        }
        withContext(ioDispatcher) {
            files.root.mkdirs()
            files.backfillMarker.createNewFile()
        }
    }

    private fun enqueue(source: FavoriteSource, itemId: String, policy: ExistingWorkPolicy) {
        workManager.get().enqueueUniqueWork(
            FavoriteImageDownloadWorker.uniqueWorkName(source, itemId),
            policy,
            FavoriteImageDownloadWorker.request(source, itemId),
        )
    }
}
