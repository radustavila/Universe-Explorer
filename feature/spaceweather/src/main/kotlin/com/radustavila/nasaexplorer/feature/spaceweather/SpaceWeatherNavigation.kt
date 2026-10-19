package com.radustavila.nasaexplorer.feature.spaceweather

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import androidx.navigation.navDeepLink
import com.radustavila.nasaexplorer.core.model.SpaceWeatherDeepLink
import com.radustavila.nasaexplorer.feature.spaceweather.neighbourhood.NeighbourhoodScreen
import com.radustavila.nasaexplorer.feature.spaceweather.smallbody.SmallBodyScreen
import kotlinx.serialization.Serializable

@Serializable
data object SpaceWeatherRoute

/** Earth's neighbourhood: asteroids passing close and recent fireballs. */
@Serializable
data object NeighbourhoodRoute

/** One asteroid's details, keyed by its JPL designation. */
@Serializable
data class SmallBodyRoute(val designation: String)

fun NavController.navigateToSpaceWeather(navOptions: NavOptions? = null) = navigate(SpaceWeatherRoute, navOptions)

fun NavController.navigateToNeighbourhood(navOptions: NavOptions? = null) = navigate(NeighbourhoodRoute, navOptions)

fun NavController.navigateToSmallBody(designation: String, navOptions: NavOptions? = null) =
    navigate(SmallBodyRoute(designation), navOptions)

/** The aurora alert notification opens this screen through [SpaceWeatherDeepLink]. */
fun NavGraphBuilder.spaceWeatherScreen(onBackClick: () -> Unit) {
    composable<SpaceWeatherRoute>(
        deepLinks = listOf(navDeepLink { uriPattern = SpaceWeatherDeepLink.URI }),
    ) {
        SpaceWeatherScreen(onBackClick = onBackClick)
    }
}

fun NavGraphBuilder.neighbourhoodScreen(onBackClick: () -> Unit, onApproachClick: (String) -> Unit) {
    composable<NeighbourhoodRoute> {
        NeighbourhoodScreen(onBackClick = onBackClick, onApproachClick = onApproachClick)
    }
}

fun NavGraphBuilder.smallBodyScreen(onBackClick: () -> Unit) {
    composable<SmallBodyRoute> {
        SmallBodyScreen(onBackClick = onBackClick)
    }
}
