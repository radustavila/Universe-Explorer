// Named for the feature, like every other feature's navigation file.
@file:Suppress("MatchingDeclarationName")

package com.radustavila.nasaexplorer.feature.skymap

import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

/** The sky map; [showGalacticCentre] opens it turned to the Milky Way's core, selected (F20). */
@Serializable
data class SkyMapRoute(val showGalacticCentre: Boolean = false)

fun NavController.navigateToSkyMap(showGalacticCentre: Boolean = false, navOptions: NavOptions? = null) =
    navigate(SkyMapRoute(showGalacticCentre), navOptions)

/**
 * The sky map; registered inside the Universe tab's graph. [locationSection] is shown when no place
 * is saved yet (Sky tonight's location picker); [onCometClick] opens a comet's details (F18).
 */
fun NavGraphBuilder.skyMapScreen(
    onBackClick: () -> Unit,
    onCometClick: (designation: String) -> Unit,
    locationSection: @Composable () -> Unit,
) {
    composable<SkyMapRoute> {
        SkyMapScreen(onBackClick = onBackClick, onCometClick = onCometClick, locationSection = locationSection)
    }
}
