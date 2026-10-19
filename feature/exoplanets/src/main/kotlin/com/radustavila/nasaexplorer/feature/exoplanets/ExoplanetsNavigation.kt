package com.radustavila.nasaexplorer.feature.exoplanets

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
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

/** The lists; registered inside the Universe tab's graph. */
fun NavGraphBuilder.exoplanetsScreen(
    onBackClick: () -> Unit,
    onExoplanetClick: (String) -> Unit,
) {
    composable<ExoplanetsRoute> {
        ExoplanetsScreen(onBackClick = onBackClick, onExoplanetClick = onExoplanetClick)
    }
}

/** One planet; reached from the lists and from Favourites, so registered at the root. */
fun NavGraphBuilder.exoplanetDetailsScreen(onBackClick: () -> Unit) {
    composable<ExoplanetDetailsRoute> {
        ExoplanetDetailsScreen(onBackClick = onBackClick)
    }
}
