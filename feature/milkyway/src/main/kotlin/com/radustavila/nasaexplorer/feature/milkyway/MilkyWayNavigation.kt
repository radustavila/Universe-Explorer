// Named after the feature, as every module's navigation file is, not after its route classes.
@file:Suppress("MatchingDeclarationName")

package com.radustavila.nasaexplorer.feature.milkyway

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

/** The Milky Way planner: the next 30 nights at the saved place. */
@Serializable
data object MilkyWayRoute

fun NavController.navigateToMilkyWay(navOptions: NavOptions? = null) = navigate(MilkyWayRoute, navOptions)

/**
 * The planner, registered inside the Universe tab's graph. Without a saved place it offers
 * [onSetLocation], which `:app` points at Sky tonight's location picker; [onShowOnSkyMap] opens the
 * sky map turned to the galactic centre (F17).
 */
fun NavGraphBuilder.milkyWayScreen(
    onBackClick: () -> Unit,
    onSetLocation: () -> Unit,
    onShowOnSkyMap: () -> Unit,
) {
    composable<MilkyWayRoute> {
        MilkyWayScreen(onBackClick = onBackClick, onSetLocation = onSetLocation, onShowOnSkyMap = onShowOnSkyMap)
    }
}
