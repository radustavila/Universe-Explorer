package com.radustavila.nasaexplorer.feature.search

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.radustavila.nasaexplorer.core.domain.search.SearchText
import com.radustavila.nasaexplorer.core.domain.search.UniversalSearchResults
import com.radustavila.nasaexplorer.core.domain.search.UniversalSearchUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveConnectivityUseCase
import com.radustavila.nasaexplorer.core.model.SearchSource
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn

/**
 * Universal search. The query lives in the [SavedStateHandle] (the route's `query` argument seeds
 * it), so an open search survives process death; results are recomputed from it.
 *
 * Typing waits [DEBOUNCE_MILLIS] for a pause before searching, and a newer query cancels a search
 * still running for an older one. Clearing the query answers at once.
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val universalSearch: UniversalSearchUseCase,
    observeConnectivity: ObserveConnectivityUseCase,
) : ViewModel() {

    private val query = savedStateHandle.getStateFlow(KEY_QUERY, "")
    private val expandedSource = savedStateHandle.getStateFlow<String?>(KEY_EXPANDED_SOURCE, null)
        .map { name -> SearchSource.entries.firstOrNull { it.name == name } }

    private val results: Flow<UniversalSearchResults?> = query
        .map { it.trim() }
        .distinctUntilChanged()
        .debounce { trimmed -> if (SearchText.terms(trimmed).isEmpty()) 0L else DEBOUNCE_MILLIS }
        .mapLatest { trimmed ->
            if (SearchText.terms(trimmed).isEmpty()) null else universalSearch(trimmed)
        }
        .onStart { emit(null) }

    val uiState: StateFlow<SearchUiState> = combine(
        query,
        results,
        expandedSource,
        observeConnectivity(),
    ) { query, results, expanded, isOnline ->
        SearchUiState(
            query = query,
            results = results,
            // A group that is no longer in the results cannot stay expanded.
            expandedSource = expanded?.takeIf { source -> results?.groups.orEmpty().any { it.source == source } },
            isOffline = !isOnline,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STATE_TIMEOUT_MILLIS),
        initialValue = SearchUiState(query = query.value),
    )

    fun onQueryChange(value: String) {
        savedStateHandle[KEY_QUERY] = value
        // A new query starts from the overview again.
        savedStateHandle[KEY_EXPANDED_SOURCE] = null
    }

    fun onClearQuery() = onQueryChange("")

    fun onSeeAll(source: SearchSource) {
        savedStateHandle[KEY_EXPANDED_SOURCE] = source.name
    }

    fun onShowAllGroups() {
        savedStateHandle[KEY_EXPANDED_SOURCE] = null
    }

    companion object {
        const val DEBOUNCE_MILLIS = 300L

        /** Matches [SearchRoute.query], so the route's argument seeds the field. */
        internal const val KEY_QUERY = "query"
        private const val KEY_EXPANDED_SOURCE = "expanded_source"
        private const val STATE_TIMEOUT_MILLIS = 5_000L
    }
}
