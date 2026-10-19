package com.radustavila.nasaexplorer.feature.universe

import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import com.radustavila.nasaexplorer.feature.universe.details.NasaMediaDetailsScreen
import com.radustavila.nasaexplorer.feature.universe.gallery.GalleryScreen
import kotlinx.serialization.Serializable

/** The Universe tab's home: curated rails and search over the NASA Image and Video Library. */
@Serializable
data object GalleryRoute

/** One library item; the library's `nasa_id` is its identity (it may contain spaces). */
@Serializable
data class NasaMediaDetailsRoute(val nasaId: String)

fun NavController.navigateToNasaMedia(nasaId: String, navOptions: NavOptions? = null) =
    navigate(NasaMediaDetailsRoute(nasaId), navOptions)

/**
 * @param header what else the Universe tab offers (exoplanets, sky tonight, Mars...), drawn above
 * the rails; null while there is nothing else.
 */
fun NavGraphBuilder.galleryScreen(
    onMediaClick: (nasaId: String) -> Unit,
    header: (@Composable () -> Unit)? = null,
) {
    composable<GalleryRoute> {
        GalleryScreen(onMediaClick = onMediaClick, header = header)
    }
}

fun NavGraphBuilder.nasaMediaDetailsScreen(onBackClick: () -> Unit) {
    composable<NasaMediaDetailsRoute> {
        NasaMediaDetailsScreen(onBackClick = onBackClick)
    }
}
