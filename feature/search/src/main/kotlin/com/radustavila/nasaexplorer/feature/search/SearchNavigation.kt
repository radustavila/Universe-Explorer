package com.radustavila.nasaexplorer.feature.search

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import androidx.navigation.navDeepLink
import com.radustavila.nasaexplorer.core.model.SearchDeepLink
import com.radustavila.nasaexplorer.core.model.SearchTarget
import kotlinx.serialization.Serializable

/** Universal search over everything on the device; [query] pre-fills the field. */
@Serializable
data class SearchRoute(val query: String = "")

fun NavController.navigateToSearch(query: String = "", navOptions: NavOptions? = null) =
    navigate(SearchRoute(query), navOptions)

/**
 * Also reachable as `nasaexplorer://search` (see [SearchDeepLink]), which the launcher's Search
 * shortcut opens.
 *
 * @param onResultClick opens a result in its feature's screen; `:app` resolves the [SearchTarget],
 * since this module knows none of them.
 * @param onSearchOnline searches the whole NASA Image and Video Library for the query, through the
 * gallery.
 */
fun NavGraphBuilder.searchScreen(
    onBackClick: () -> Unit,
    onResultClick: (SearchTarget) -> Unit,
    onSearchOnline: (query: String) -> Unit,
) {
    composable<SearchRoute>(
        deepLinks = listOf(navDeepLink<SearchRoute>(basePath = SearchDeepLink.URI)),
    ) {
        SearchScreen(
            onBackClick = onBackClick,
            onResultClick = onResultClick,
            onSearchOnline = onSearchOnline,
        )
    }
}
