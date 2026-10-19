package com.radustavila.nasaexplorer.core.domain.usecase

import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.domain.repository.FavoritesRepository
import com.radustavila.nasaexplorer.core.model.EpicCollection
import com.radustavila.nasaexplorer.core.model.EpicImage
import com.radustavila.nasaexplorer.core.model.FavoriteSource
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
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
 * Favourites tab's size (the rotation thumbnails are too small), and a title that says when the
 * photo was taken; the Favourites tab already labels the source.
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
                title = titleOf(image),
                imageUrl = image.imageUrl,
            )
        }
    }

    private fun titleOf(image: EpicImage): String {
        val taken = TITLE_TIME_FORMAT.format(image.capturedAt.atOffset(ZoneOffset.UTC))
        return when (image.collection) {
            EpicCollection.NATURAL -> "Earth, $taken UTC"
            EpicCollection.ENHANCED -> "Earth (enhanced), $taken UTC"
        }
    }

    private companion object {
        val TITLE_TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.ENGLISH)
    }
}
