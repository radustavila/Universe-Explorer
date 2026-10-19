package com.radustavila.nasaexplorer.feature.earthevents

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

@Serializable
data object EarthEventsRoute

fun NavController.navigateToEarthEvents(navOptions: NavOptions? = null) = navigate(EarthEventsRoute, navOptions)

fun NavGraphBuilder.earthEventsScreen(onBackClick: () -> Unit) {
    composable<EarthEventsRoute> {
        EarthEventsRoute(onBackClick = onBackClick)
    }
}
