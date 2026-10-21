package com.radustavila.nasaexplorer.core.offline

import com.radustavila.nasaexplorer.core.common.dispatchers.AppDispatcher
import com.radustavila.nasaexplorer.core.common.dispatchers.Dispatcher
import com.radustavila.nasaexplorer.core.database.dao.FavoriteDao
import com.radustavila.nasaexplorer.core.domain.offline.OfflineFavoritesStorage
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn

/**
 * The size of every offline copy, as recorded with it on the favourite's row: an observable query
 * rather than a walk of the directory, so a settings screen can show it live at no cost.
 */
class RoomOfflineFavoritesStorage @Inject constructor(
    private val favoriteDao: FavoriteDao,
    @Dispatcher(AppDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
) : OfflineFavoritesStorage {

    override fun observeSizeBytes(): Flow<Long> =
        favoriteDao.observeLocalImageBytes().distinctUntilChanged().flowOn(ioDispatcher)
}
