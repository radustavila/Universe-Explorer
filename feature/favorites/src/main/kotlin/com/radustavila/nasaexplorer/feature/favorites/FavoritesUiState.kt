package com.radustavila.nasaexplorer.feature.favorites

import com.radustavila.nasaexplorer.core.designsystem.error.UiError
import com.radustavila.nasaexplorer.core.model.FavoriteItem

/**
 * Favourites are local-only state, so this screen has no offline or refreshing dimension: it is
 * either loading from the database, empty, or showing content.
 */
data class FavoritesUiState(
    val items: List<FavoriteItem> = emptyList(),
    val isLoading: Boolean = true,
    val error: UiError? = null,
) {
    val isEmpty: Boolean get() = items.isEmpty() && !isLoading
}
