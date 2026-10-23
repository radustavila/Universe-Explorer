// Named after the feature, as every module's navigation file is, not after its route classes.
@file:Suppress("MatchingDeclarationName")

package com.radustavila.nasaexplorer.feature.moonmap

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

/** The Moon map: LRO imagery, landing sites and today's terminator (F25). */
@Serializable
data object MoonMapRoute

fun NavController.navigateToMoonMap(navOptions: NavOptions? = null) = navigate(MoonMapRoute, navOptions)

/** The Moon map, registered inside the Universe tab's graph. */
fun NavGraphBuilder.moonMapScreen(onBackClick: () -> Unit) {
    composable<MoonMapRoute> {
        MoonMapRoute(onBackClick = onBackClick)
    }
}
