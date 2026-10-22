package com.radustavila.nasaexplorer.feature.exoplanets

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import com.radustavila.nasaexplorer.core.designsystem.adaptive.ListDetailHost
import com.radustavila.nasaexplorer.core.designsystem.adaptive.listOnly
import com.radustavila.nasaexplorer.feature.exoplanets.details.ExoplanetDetailsScreen
import kotlinx.serialization.Serializable

@Serializable
data object ExoplanetsRoute

/** The archive's planet name is the planet's identity (also its favourite id). */
@Serializable
data class ExoplanetDetailsRoute(val name: String)

fun NavController.navigateToExoplanets(navOptions: NavOptions? = null) = navigate(ExoplanetsRoute, navOptions)

fun NavController.navigateToExoplanetDetails(name: String, navOptions: NavOptions? = null) =
    navigate(ExoplanetDetailsRoute(name), navOptions)

/**
 * The lists; registered inside the Universe tab's graph.
 *
 * @param onExoplanetClick opens a planet full screen.
 * @param listDetail places the lists beside the planet they opened, where the window allows.
 */
fun NavGraphBuilder.exoplanetsScreen(
    onBackClick: () -> Unit,
    onExoplanetClick: (String) -> Unit,
    listDetail: ListDetailHost<String> = listOnly(),
) {
    composable<ExoplanetsRoute> {
        listDetail(onExoplanetClick) { onItemClick ->
            ExoplanetsScreen(onBackClick = onBackClick, onExoplanetClick = onItemClick)
        }
    }
}

/** One planet; reached from the lists and from Favourites, so registered at the root. */
fun NavGraphBuilder.exoplanetDetailsScreen(onBackClick: () -> Unit) {
    composable<ExoplanetDetailsRoute> {
        ExoplanetDetailsScreen(onBackClick = onBackClick)
    }
}
