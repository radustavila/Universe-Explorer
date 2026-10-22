package com.radustavila.nasaexplorer.feature.universe

import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import com.radustavila.nasaexplorer.core.designsystem.adaptive.ListDetailHost
import com.radustavila.nasaexplorer.core.designsystem.adaptive.listOnly
import com.radustavila.nasaexplorer.feature.universe.details.NasaMediaDetailsScreen
import com.radustavila.nasaexplorer.feature.universe.gallery.GalleryScreen
import kotlinx.serialization.Serializable

/** The Universe tab's home: curated rails and search over the NASA Image and Video Library. */
@Serializable
data object GalleryRoute

/**
 * The gallery opened on a library search for [query], e.g. from universal search's "search the
 * library online". The argument shares the gallery's saved-state key, so it seeds the search field.
 */
@Serializable
data class GallerySearchRoute(val query: String)

/** One library item; the library's `nasa_id` is its identity (it may contain spaces). */
@Serializable
data class NasaMediaDetailsRoute(val nasaId: String)

fun NavController.navigateToNasaMedia(nasaId: String, navOptions: NavOptions? = null) =
    navigate(NasaMediaDetailsRoute(nasaId), navOptions)

/**
 * @param onMediaClick opens an item full screen.
 * @param header what else the Universe tab offers (exoplanets, sky tonight, Mars...), drawn above
 * the rails with its own side margins; null while there is nothing else.
 * @param listDetail places the gallery beside the item it opened, where the window allows.
 */
fun NavGraphBuilder.galleryScreen(
    onMediaClick: (nasaId: String) -> Unit,
    header: (@Composable () -> Unit)? = null,
    listDetail: ListDetailHost<String> = listOnly(),
) {
    composable<GalleryRoute> {
        listDetail(onMediaClick) { onItemClick ->
            GalleryScreen(onMediaClick = onItemClick, header = header)
        }
    }
}

fun NavController.navigateToGallerySearch(query: String, navOptions: NavOptions? = null) =
    navigate(GallerySearchRoute(query), navOptions)

/**
 * The gallery searching the library for a query handed in from elsewhere; see [GallerySearchRoute].
 * Laid out like [galleryScreen], without the tab's header.
 */
fun NavGraphBuilder.gallerySearchScreen(
    onMediaClick: (nasaId: String) -> Unit,
    listDetail: ListDetailHost<String> = listOnly(),
) {
    composable<GallerySearchRoute> {
        listDetail(onMediaClick) { onItemClick ->
            GalleryScreen(onMediaClick = onItemClick, header = null)
        }
    }
}

fun NavGraphBuilder.nasaMediaDetailsScreen(onBackClick: () -> Unit) {
    composable<NasaMediaDetailsRoute> {
        NasaMediaDetailsScreen(onBackClick = onBackClick)
    }
}
