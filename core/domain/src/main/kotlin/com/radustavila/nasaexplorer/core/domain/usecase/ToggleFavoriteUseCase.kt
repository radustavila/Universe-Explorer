package com.radustavila.nasaexplorer.core.domain.usecase

import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.domain.repository.ApodRepository
import com.radustavila.nasaexplorer.core.model.Apod
import javax.inject.Inject

/**
 * Favourites are purely local state: toggling never touches the network, and the write goes straight
 * to the database so the Flow-driven UI updates itself.
 */
class ToggleFavoriteUseCase @Inject constructor(
    private val apodRepository: ApodRepository,
) {
    suspend operator fun invoke(apod: Apod): DataResult<Unit> =
        apodRepository.setFavorite(date = apod.date, isFavorite = !apod.isFavorite)
}
