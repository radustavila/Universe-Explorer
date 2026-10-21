package com.radustavila.nasaexplorer.feature.universe.gallery

import com.radustavila.nasaexplorer.core.designsystem.error.UiError
import com.radustavila.nasaexplorer.core.model.DataFreshness
import com.radustavila.nasaexplorer.core.model.GalleryRail
import com.radustavila.nasaexplorer.core.model.NasaMedia
import com.radustavila.nasaexplorer.core.model.NasaMediaType

data class GalleryUiState(
    val query: String = "",
    val filters: SearchFilters = SearchFilters(),
    val rails: List<RailUi> = emptyList(),
    val isRefreshing: Boolean = false,
    val isOffline: Boolean = false,
    /** Why the last rail refresh failed; shown over cached rails, or instead of missing ones. */
    val refreshError: UiError? = null,
    val search: SearchUiState = SearchUiState.Idle,
    /** When the library's rails last synced, for the "Updated …" line above them. */
    val freshness: DataFreshness = DataFreshness(),
) {
    val isSearching: Boolean get() = query.isNotBlank()

    val railsStatus: RailsStatus
        get() = when {
            rails.isNotEmpty() -> RailsStatus.Content
            isRefreshing -> RailsStatus.Loading
            isOffline -> RailsStatus.Offline
            refreshError != null -> RailsStatus.Failed(refreshError)
            else -> RailsStatus.Loading
        }
}

data class RailUi(val rail: GalleryRail, val items: List<NasaMedia>)

sealed interface RailsStatus {
    data object Loading : RailsStatus
    data object Content : RailsStatus

    /** Nothing cached yet, and no connection to fetch it with. */
    data object Offline : RailsStatus
    data class Failed(val error: UiError) : RailsStatus
}

data class SearchFilters(
    val mediaTypes: Set<NasaMediaType> = NasaMediaType.entries.toSet(),
    val yearStart: Int? = null,
    val yearEnd: Int? = null,
)

sealed interface SearchUiState {
    /** No query typed. */
    data object Idle : SearchUiState

    data object Loading : SearchUiState

    data class Results(
        val items: List<NasaMedia>,
        val totalHits: Int,
        val canLoadMore: Boolean,
        val isAppending: Boolean = false,
        val appendError: UiError? = null,
    ) : SearchUiState

    data object Empty : SearchUiState

    /** Search is online-only; shown instead of an error while offline. */
    data object NeedsConnection : SearchUiState

    data class Failed(val error: UiError) : SearchUiState
}
