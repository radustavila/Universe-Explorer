package com.radustavila.nasaexplorer.feature.earthmap

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

@Serializable
data object EarthMapRoute

fun NavController.navigateToEarthMap(navOptions: NavOptions? = null) = navigate(EarthMapRoute, navOptions)

fun NavGraphBuilder.earthMapScreen(onBackClick: () -> Unit) {
    composable<EarthMapRoute> {
        EarthMapRoute(onBackClick = onBackClick)
    }
}
