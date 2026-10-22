// Named for the feature, like every other feature's navigation file.
@file:Suppress("MatchingDeclarationName")

package com.radustavila.nasaexplorer.feature.skymap

import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

@Serializable
data object SkyMapRoute

fun NavController.navigateToSkyMap(navOptions: NavOptions? = null) = navigate(SkyMapRoute, navOptions)

/**
 * The sky map; registered inside the Universe tab's graph. [locationSection] is shown when no place
 * is saved yet (Sky tonight's location picker).
 */
fun NavGraphBuilder.skyMapScreen(onBackClick: () -> Unit, locationSection: @Composable () -> Unit) {
    composable<SkyMapRoute> {
        SkyMapScreen(onBackClick = onBackClick, locationSection = locationSection)
    }
}
