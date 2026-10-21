package com.radustavila.nasaexplorer.feature.earthmap

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import androidx.navigation.navDeepLink
import com.radustavila.nasaexplorer.core.model.EarthMapDeepLink
import kotlinx.serialization.Serializable

@Serializable
data object EarthMapRoute

fun NavController.navigateToEarthMap(navOptions: NavOptions? = null) = navigate(EarthMapRoute, navOptions)

/** The launcher's Earth map shortcut opens this screen through [EarthMapDeepLink]. */
fun NavGraphBuilder.earthMapScreen(onBackClick: () -> Unit) {
    composable<EarthMapRoute>(
        deepLinks = listOf(navDeepLink { uriPattern = EarthMapDeepLink.URI }),
    ) {
        EarthMapRoute(onBackClick = onBackClick)
    }
}
