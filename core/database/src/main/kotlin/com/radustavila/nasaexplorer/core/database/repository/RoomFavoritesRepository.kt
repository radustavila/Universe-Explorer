package com.radustavila.nasaexplorer.core.database.repository

import com.radustavila.nasaexplorer.core.common.dispatchers.AppDispatcher
import com.radustavila.nasaexplorer.core.common.dispatchers.Dispatcher
import com.radustavila.nasaexplorer.core.common.error.AppError
import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.database.dao.FavoriteDao
import com.radustavila.nasaexplorer.core.database.entity.FavoriteEntity
import com.radustavila.nasaexplorer.core.domain.offline.OfflineFavoriteImages
import com.radustavila.nasaexplorer.core.domain.repository.FavoritesRepository
import com.radustavila.nasaexplorer.core.model.FavoriteItem
import com.radustavila.nasaexplorer.core.model.FavoriteSource
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Room-backed favourites of every source. Lives next to the database rather than in a data module
 * because it belongs to no single API: every feature saves through it.
 *
 * Saving queues an offline copy of the snapshot image and removing deletes it (see
 * [OfflineFavoriteImages]), so no screen has to remember to.
 */
@Singleton
class RoomFavoritesRepository @Inject constructor(
    private val favoriteDao: FavoriteDao,
    private val offlineImages: OfflineFavoriteImages,
    @Dispatcher(AppDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
) : FavoritesRepository {

    override fun observeFavorites(): Flow<List<FavoriteItem>> = favoriteDao.observeAll()
        // A row from a source this build does not know (e.g. after a downgrade) is skipped, not fatal.
        .map { entities -> entities.mapNotNull(FavoriteEntity::toDomainOrNull) }
        .flowOn(ioDispatcher)

    override fun observeFavoriteIds(source: FavoriteSource): Flow<Set<String>> =
        favoriteDao.observeIds(source.key)
            .map(List<String>::toSet)
            .distinctUntilChanged()
            .flowOn(ioDispatcher)

    override fun observeIsFavorite(source: FavoriteSource, itemId: String): Flow<Boolean> =
        favoriteDao.observeIsFavorite(source.key, itemId).distinctUntilChanged().flowOn(ioDispatcher)

    override fun observeCount(): Flow<Int> = favoriteDao.observeCount().flowOn(ioDispatcher)

    override suspend fun save(item: FavoriteItem): DataResult<Unit> = runDatabaseCall {
        favoriteDao.save(
            FavoriteEntity(
                source = item.source.key,
                itemId = item.itemId,
                title = item.title,
                imageUrl = item.imageUrl,
                savedAtEpochMillis = item.savedAt.toEpochMilli(),
            ),
        )
    }.also { result ->
        if (result is DataResult.Success && item.imageUrl != null) {
            offlineImages.requestDownload(item.source, item.itemId)
        }
    }

    override suspend fun remove(source: FavoriteSource, itemId: String): DataResult<Unit> =
        runDatabaseCall { favoriteDao.delete(source.key, itemId) }.also { result ->
            if (result is DataResult.Success) offlineImages.discard(source, itemId)
        }

    private suspend fun runDatabaseCall(block: suspend () -> Unit): DataResult<Unit> =
        withContext(ioDispatcher) {
            try {
                block()
                DataResult.Success(Unit)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                DataResult.Failure(AppError.Database(throwable.message))
            }
        }
}

private fun FavoriteEntity.toDomainOrNull(): FavoriteItem? = FavoriteSource.fromKey(source)?.let { known ->
    FavoriteItem(
        source = known,
        itemId = itemId,
        title = title,
        imageUrl = imageUrl,
        savedAt = Instant.ofEpochMilli(savedAtEpochMillis),
    )
}
