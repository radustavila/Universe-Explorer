package com.radustavila.nasaexplorer.feature.search

import androidx.compose.runtime.Immutable
import com.radustavila.nasaexplorer.core.domain.search.SearchResultGroup
import com.radustavila.nasaexplorer.core.domain.search.SearchText
import com.radustavila.nasaexplorer.core.domain.search.UniversalSearchResults
import com.radustavila.nasaexplorer.core.model.SearchSource

/**
 * @property query what the field shows, as typed.
 * @property results the last completed search; kept while the next one is pending so the list does
 * not flash empty between keystrokes. Null while the query has nothing to search for.
 * @property expandedSource the group whose "see all" is open; the other groups are hidden meanwhile.
 */
@Immutable
data class SearchUiState(
    val query: String = "",
    val results: UniversalSearchResults? = null,
    val expandedSource: SearchSource? = null,
    val isOffline: Boolean = false,
) {
    val terms: List<String> get() = SearchText.terms(query)

    val hasQuery: Boolean get() = terms.isNotEmpty()

    /** Typing is ahead of the results on screen (the debounce or the search itself is pending). */
    val isSearching: Boolean get() = hasQuery && results?.query != query.trim()

    val hasNoResults: Boolean get() = hasQuery && !isSearching && results?.isEmpty == true

    /** The groups to draw: all of them, or only the expanded one. */
    val visibleGroups: List<SearchResultGroup>
        get() {
            val groups = results?.groups.orEmpty()
            return expandedSource?.let { source -> groups.filter { it.source == source } } ?: groups
        }
}
