// Named for the screen, like every feature's navigation file.
@file:Suppress("MatchingDeclarationName")

package com.radustavila.nasaexplorer.feature.skymap.log

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

/** The observing log's Messier and Caldwell checklists (F23). */
@Serializable
data object ObservingLogRoute

fun NavController.navigateToObservingLog(navOptions: NavOptions? = null) = navigate(ObservingLogRoute, navOptions)

/** The observing log; [onShowOnSkyMap] opens the sky map turned to an object. */
fun NavGraphBuilder.observingLogScreen(onBackClick: () -> Unit, onShowOnSkyMap: (objectId: String) -> Unit) {
    composable<ObservingLogRoute> {
        ObservingLogScreen(onBackClick = onBackClick, onShowOnSkyMap = onShowOnSkyMap)
    }
}
