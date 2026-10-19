package com.radustavila.nasaexplorer.feature.universe.gallery

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.radustavila.nasaexplorer.core.common.error.AppError
import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.designsystem.error.UiError
import com.radustavila.nasaexplorer.core.designsystem.error.toUiError
import com.radustavila.nasaexplorer.core.domain.sync.SourceRefreshResult
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveConnectivityUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveGalleryRailsUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.RefreshGalleryUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.SearchNasaMediaUseCase
import com.radustavila.nasaexplorer.core.model.NasaMedia
import com.radustavila.nasaexplorer.core.model.NasaMediaQuery
import com.radustavila.nasaexplorer.core.model.NasaMediaType
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The Universe tab's home. Rails come from the local cache (refreshed at most daily, through
 * [RefreshGalleryUseCase]); search runs against the library as the user types.
 *
 * The query and filters live in [SavedStateHandle], so rotation and process death keep them; the
 * results are cheap to recompute from the repository's page cache.
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class GalleryViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    observeRails: ObserveGalleryRailsUseCase,
    observeConnectivity: ObserveConnectivityUseCase,
    private val refreshGallery: RefreshGalleryUseCase,
    private val searchMedia: SearchNasaMediaUseCase,
) : ViewModel() {

    private val query = savedStateHandle.getStateFlow(KEY_QUERY, "")
    private val filters = combine(
        savedStateHandle.getStateFlow(KEY_MEDIA_TYPES, NasaMediaType.entries.joinToString(",") { it.apiValue }),
        savedStateHandle.getStateFlow(KEY_YEAR_START, NO_YEAR),
        savedStateHandle.getStateFlow(KEY_YEAR_END, NO_YEAR),
    ) { types, start, end ->
        SearchFilters(
            mediaTypes = types.split(',').mapNotNull(NasaMediaType::fromApiValue).toSet()
                .ifEmpty { NasaMediaType.entries.toSet() },
            yearStart = start.takeIf { it != NO_YEAR },
            yearEnd = end.takeIf { it != NO_YEAR },
        )
    }.distinctUntilChanged()

    private val isOnline = observeConnectivity()
    private val rails = observeRails()
    private val refreshState = MutableStateFlow(RefreshState())
    private val searchState = MutableStateFlow<SearchUiState>(SearchUiState.Idle)

    private var activeQuery: NasaMediaQuery? = null
    private var nextPage: Int? = null
    private var appendJob: Job? = null

    val uiState: StateFlow<GalleryUiState> = combine(
        combine(query, filters, ::Pair),
        rails,
        isOnline,
        refreshState,
        searchState,
    ) { (query, filters), rails, online, refresh, search ->
        GalleryUiState(
            query = query,
            filters = filters,
            rails = rails.map { (rail, items) -> RailUi(rail, items) },
            isRefreshing = refresh.isRefreshing,
            isOffline = !online,
            refreshError = refresh.error,
            search = if (query.isBlank()) SearchUiState.Idle else search,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STATE_TIMEOUT_MILLIS),
        initialValue = GalleryUiState(query = query.value),
    )

    init {
        viewModelScope.launch {
            // An empty cache is always due, whatever the bookkeeping says (e.g. a rebuilt database).
            refresh(force = rails.first().isEmpty())
        }
        viewModelScope.launch {
            // Back online after a failed refresh: try again rather than waiting for a pull.
            isOnline.distinctUntilChanged().drop(1).filter { it }.collect {
                if (refreshState.value.error != null) refresh(force = false)
            }
        }
        viewModelScope.launch { searchAsTheUserTypes() }
    }

    fun onQueryChange(value: String) {
        savedStateHandle[KEY_QUERY] = value
    }

    fun onClearQuery() {
        savedStateHandle[KEY_QUERY] = ""
    }

    fun onToggleMediaType(type: NasaMediaType) {
        val current = uiState.value.filters.mediaTypes
        val updated = if (type in current) current - type else current + type
        // At least one type must stay selected; an empty filter would mean "everything" anyway.
        if (updated.isEmpty()) return
        savedStateHandle[KEY_MEDIA_TYPES] = NasaMediaType.entries.filter { it in updated }
            .joinToString(",") { it.apiValue }
    }

    fun onYearRangeChange(start: Int?, end: Int?) {
        savedStateHandle[KEY_YEAR_START] = start ?: NO_YEAR
        savedStateHandle[KEY_YEAR_END] = end ?: NO_YEAR
    }

    fun onRefresh() {
        viewModelScope.launch { refresh(force = true) }
    }

    fun onRefreshErrorShown() {
        refreshState.update { it.copy(error = null) }
    }

    fun onRetrySearch() {
        val query = activeQuery ?: return
        viewModelScope.launch { runSearch(query) }
    }

    /** Called when the results grid nears its end. */
    fun onLoadMore() {
        val query = activeQuery ?: return
        val page = nextPage ?: return
        val current = searchState.value as? SearchUiState.Results ?: return
        if (current.isAppending || appendJob?.isActive == true) return

        searchState.value = current.copy(isAppending = true, appendError = null)
        appendJob = viewModelScope.launch {
            val result = searchMedia(query, page)
            if (activeQuery != query) return@launch
            val latest = searchState.value as? SearchUiState.Results ?: return@launch
            searchState.value = when (result) {
                is DataResult.Success -> {
                    nextPage = result.data.nextPage
                    latest.copy(
                        items = (latest.items + result.data.items).distinctBy(NasaMedia::nasaId),
                        canLoadMore = result.data.nextPage != null,
                        isAppending = false,
                    )
                }

                is DataResult.Failure -> latest.copy(isAppending = false, appendError = result.error.toUiError())
            }
        }
    }

    private suspend fun refresh(force: Boolean) {
        if (refreshState.value.isRefreshing) return
        refreshState.update { it.copy(isRefreshing = true) }
        val result = refreshGallery(force)
        refreshState.update {
            RefreshState(
                isRefreshing = false,
                error = ((result as? SourceRefreshResult.Completed)?.result as? DataResult.Failure)
                    ?.error?.toUiError(),
            )
        }
    }

    /**
     * Waits for a pause in typing, then searches; a newer query cancels the one in flight. Coming
     * back online repeats the current query, so "needs connection" clears by itself.
     */
    private suspend fun searchAsTheUserTypes() {
        combine(query, filters, isOnline) { text, filters, online -> SearchRequest(text.trim(), filters, online) }
            .distinctUntilChanged()
            .debounce { request -> if (request.text.isEmpty()) 0L else SEARCH_DEBOUNCE_MILLIS }
            .mapLatest { request ->
                if (request.text.isEmpty()) {
                    activeQuery = null
                    nextPage = null
                    appendJob?.cancel()
                    searchState.value = SearchUiState.Idle
                } else {
                    runSearch(
                        NasaMediaQuery(
                            text = request.text,
                            mediaTypes = request.filters.mediaTypes,
                            yearStart = request.filters.yearStart,
                            yearEnd = request.filters.yearEnd,
                        ),
                    )
                }
            }
            .collect {}
    }

    private suspend fun runSearch(query: NasaMediaQuery) {
        appendJob?.cancel()
        activeQuery = query
        nextPage = null
        searchState.value = SearchUiState.Loading

        val result = searchMedia(query)
        if (activeQuery != query) return
        searchState.value = when (result) {
            is DataResult.Success -> {
                nextPage = result.data.nextPage
                if (result.data.items.isEmpty()) {
                    SearchUiState.Empty
                } else {
                    SearchUiState.Results(
                        items = result.data.items,
                        totalHits = result.data.totalHits,
                        canLoadMore = result.data.nextPage != null,
                    )
                }
            }

            is DataResult.Failure -> when (result.error) {
                AppError.NoConnection -> SearchUiState.NeedsConnection
                else -> SearchUiState.Failed(result.error.toUiError())
            }
        }
    }

    private data class RefreshState(
        val isRefreshing: Boolean = false,
        val error: UiError? = null,
    )

    private data class SearchRequest(
        val text: String,
        val filters: SearchFilters,
        val isOnline: Boolean,
    )

    private companion object {
        const val STATE_TIMEOUT_MILLIS = 5_000L
        const val SEARCH_DEBOUNCE_MILLIS = 450L
        const val NO_YEAR = -1
        const val KEY_QUERY = "query"
        const val KEY_MEDIA_TYPES = "media_types"
        const val KEY_YEAR_START = "year_start"
        const val KEY_YEAR_END = "year_end"
    }
}
