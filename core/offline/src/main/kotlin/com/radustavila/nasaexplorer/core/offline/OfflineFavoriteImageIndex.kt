package com.radustavila.nasaexplorer.core.offline

import com.radustavila.nasaexplorer.core.common.dispatchers.AppDispatcher
import com.radustavila.nasaexplorer.core.common.dispatchers.ApplicationScope
import com.radustavila.nasaexplorer.core.common.dispatchers.Dispatcher
import com.radustavila.nasaexplorer.core.database.dao.FavoriteDao
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Remote image URL -> URI of its offline copy, for every favourite that has one. Kept in memory and
 * up to date with the `favorite` table, so the image loader can look up every request it sees.
 */
@Singleton
class OfflineFavoriteImageIndex @Inject constructor(
    favoriteDao: FavoriteDao,
    files: FavoriteImageFiles,
    @ApplicationScope applicationScope: CoroutineScope,
    @Dispatcher(AppDispatcher.IO) ioDispatcher: CoroutineDispatcher,
) {

    private val uris: StateFlow<Map<String, String>?> = favoriteDao.observeLocalImages()
        .map { images -> images.associate { it.imageUrl to files.uriOf(it.localImagePath) } }
        // Without the index images still load from the network and the image cache.
        .catch { emit(emptyMap()) }
        .flowOn(ioDispatcher)
        .stateIn(applicationScope, SharingStarted.Eagerly, initialValue = null)

    /** The `file://` URI of the offline copy of [imageUrl], or `null` without one. */
    suspend fun offlineUriFor(imageUrl: String): String? =
        (uris.value ?: uris.filterNotNull().first())[imageUrl]
}
