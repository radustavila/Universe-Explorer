// Named after the feature, as every module's navigation file is, not after its route classes.
@file:Suppress("MatchingDeclarationName")

package com.radustavila.nasaexplorer.feature.webb

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

/** Webb's scheduled week, as a timeline. */
@Serializable
data object WebbRoute

fun NavController.navigateToWebb(navOptions: NavOptions? = null) = navigate(WebbRoute, navOptions)

/**
 * The week's schedule, registered inside the Universe tab's graph. [onTargetClick] gets a target's
 * name to search for; `:app` points it at the gallery's search.
 */
fun NavGraphBuilder.webbScreen(
    onBackClick: () -> Unit,
    onTargetClick: (query: String) -> Unit,
) {
    composable<WebbRoute> {
        WebbScreen(onBackClick = onBackClick, onTargetClick = onTargetClick)
    }
}
