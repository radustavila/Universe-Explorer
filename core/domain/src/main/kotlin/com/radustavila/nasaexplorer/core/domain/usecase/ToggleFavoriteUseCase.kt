package com.radustavila.nasaexplorer.core.domain.usecase

import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.common.time.TimeProvider
import com.radustavila.nasaexplorer.core.domain.repository.FavoritesRepository
import com.radustavila.nasaexplorer.core.model.Apod
import com.radustavila.nasaexplorer.core.model.FavoriteItem
import com.radustavila.nasaexplorer.core.model.FavoriteSource
import javax.inject.Inject

/**
 * Favourites are purely local state: toggling never touches the network, and the write goes straight
 * to the database so the Flow-driven UI updates itself.
 *
 * An APOD favourite is saved under [FavoriteSource.APOD] with its date as the id, and a snapshot of
 * its title and preview image for the Favourites tab.
 */
class ToggleFavoriteUseCase @Inject constructor(
    private val favoritesRepository: FavoritesRepository,
    private val timeProvider: TimeProvider,
) {
    suspend operator fun invoke(apod: Apod): DataResult<Unit> = if (apod.isFavorite) {
        favoritesRepository.remove(FavoriteSource.APOD, apod.date.toString())
    } else {
        favoritesRepository.save(
            FavoriteItem(
                source = FavoriteSource.APOD,
                itemId = apod.date.toString(),
                title = apod.title,
                imageUrl = apod.previewImageUrl,
                savedAt = timeProvider.now(),
            ),
        )
    }
}
