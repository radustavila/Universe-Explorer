package com.radustavila.nasaexplorer.core.domain.usecase

import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.common.time.TimeProvider
import com.radustavila.nasaexplorer.core.domain.offline.OfflineFavoritesStorage
import com.radustavila.nasaexplorer.core.domain.repository.FavoritesRepository
import com.radustavila.nasaexplorer.core.model.FavoriteItem
import com.radustavila.nasaexplorer.core.model.FavoriteSource
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

/** Every favourite of every source, most recently saved first. */
class ObserveFavoritesUseCase @Inject constructor(
    private val favoritesRepository: FavoritesRepository,
) {
    operator fun invoke(): Flow<List<FavoriteItem>> = favoritesRepository.observeFavorites()
}

/** Drives a heart button on any screen. */
class ObserveIsFavoriteUseCase @Inject constructor(
    private val favoritesRepository: FavoritesRepository,
) {
    operator fun invoke(source: FavoriteSource, itemId: String): Flow<Boolean> =
        favoritesRepository.observeIsFavorite(source, itemId)
}

/**
 * Saves an item of any source with a snapshot of what the Favourites tab needs to render it
 * offline. Saving an already saved item refreshes the snapshot.
 */
class SaveFavoriteUseCase @Inject constructor(
    private val favoritesRepository: FavoritesRepository,
    private val timeProvider: TimeProvider,
) {
    suspend operator fun invoke(
        source: FavoriteSource,
        itemId: String,
        title: String,
        imageUrl: String?,
    ): DataResult<Unit> = favoritesRepository.save(
        FavoriteItem(
            source = source,
            itemId = itemId,
            title = title,
            imageUrl = imageUrl,
            savedAt = timeProvider.now(),
        ),
    )
}

class RemoveFavoriteUseCase @Inject constructor(
    private val favoritesRepository: FavoritesRepository,
) {
    suspend operator fun invoke(source: FavoriteSource, itemId: String): DataResult<Unit> =
        favoritesRepository.remove(source, itemId)
}

/** Bytes taken by the offline copies of favourite images. */
class ObserveOfflineFavoritesSizeUseCase @Inject constructor(
    private val storage: OfflineFavoritesStorage,
) {
    operator fun invoke(): Flow<Long> = storage.observeSizeBytes()
}
