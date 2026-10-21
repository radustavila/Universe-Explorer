package com.radustavila.nasaexplorer.core.domain.usecase

import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.domain.repository.FavoritesRepository
import com.radustavila.nasaexplorer.core.model.EpicImage
import com.radustavila.nasaexplorer.core.model.FavoriteSource
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

/** Favourite ids ([com.radustavila.nasaexplorer.core.model.EpicPhotoRef.favoriteId]) of every saved EPIC photo. */
class ObserveEpicFavoriteIdsUseCase @Inject constructor(
    private val favoritesRepository: FavoritesRepository,
) {
    operator fun invoke(): Flow<Set<String>> = favoritesRepository.observeFavoriteIds(FavoriteSource.EPIC)
}

/**
 * Saves or removes one EPIC photo. The snapshot keeps the full JPEG, which reads well at the
 * Favourites tab's size (the rotation thumbnails are too small), and, as its title, when the photo
 * was taken as an ISO-8601 instant ([EpicImage.favoriteTitle]): raw data, which the Favourites tab
 * words in the user's language, time zone and clock format. The collection is in the item id.
 */
class ToggleEpicFavoriteUseCase @Inject constructor(
    private val saveFavorite: SaveFavoriteUseCase,
    private val removeFavorite: RemoveFavoriteUseCase,
) {
    suspend operator fun invoke(image: EpicImage, isFavorite: Boolean): DataResult<Unit> {
        val id = image.ref.favoriteId
        return if (isFavorite) {
            removeFavorite(FavoriteSource.EPIC, id)
        } else {
            saveFavorite(
                source = FavoriteSource.EPIC,
                itemId = id,
                title = EpicImage.favoriteTitle(image.capturedAt),
                imageUrl = image.imageUrl,
            )
        }
    }
}
