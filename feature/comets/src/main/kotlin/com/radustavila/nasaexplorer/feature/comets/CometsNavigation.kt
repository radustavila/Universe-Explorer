// Named after the feature, as every module's navigation file is, not after its route classes.
@file:Suppress("MatchingDeclarationName")

package com.radustavila.nasaexplorer.feature.comets

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

/** The comets near the Sun, those bright enough to look for first. */
@Serializable
data object CometsRoute

/** One comet by its JPL designation ("2026 A2"). */
@Serializable
data class CometDetailsRoute(val designation: String)

fun NavController.navigateToComets(navOptions: NavOptions? = null) = navigate(CometsRoute, navOptions)

fun NavController.navigateToCometDetails(designation: String, navOptions: NavOptions? = null) =
    navigate(CometDetailsRoute(designation), navOptions)

/**
 * The comets list, registered inside the Universe tab's graph. Without a saved place it offers
 * [onSetLocation], which `:app` points at Sky tonight's location picker.
 */
fun NavGraphBuilder.cometsScreen(
    onBackClick: () -> Unit,
    onCometClick: (designation: String) -> Unit,
    onSetLocation: () -> Unit,
) {
    composable<CometsRoute> {
        CometsScreen(onBackClick = onBackClick, onCometClick = onCometClick, onSetLocation = onSetLocation)
    }
}

fun NavGraphBuilder.cometDetailsScreen(onBackClick: () -> Unit) {
    composable<CometDetailsRoute> {
        CometDetailsScreen(onBackClick = onBackClick)
    }
}
