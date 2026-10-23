// Named for the feature, like every other feature's navigation file.
@file:Suppress("MatchingDeclarationName")

package com.radustavila.nasaexplorer.feature.skymap

import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

/**
 * The sky map; [showGalacticCentre] opens it turned to the Milky Way's core, selected (F20), and
 * [deepSkyId] turned to that deep-sky object (F23, OpenNGC's name, "NGC0224").
 */
@Serializable
data class SkyMapRoute(val showGalacticCentre: Boolean = false, val deepSkyId: String? = null)

fun NavController.navigateToSkyMap(
    showGalacticCentre: Boolean = false,
    deepSkyId: String? = null,
    navOptions: NavOptions? = null,
) = navigate(SkyMapRoute(showGalacticCentre, deepSkyId), navOptions)

/**
 * The sky map; registered inside the Universe tab's graph. [locationSection] is shown when no place
 * is saved yet (Sky tonight's location picker); [onCometClick] opens a comet's details (F18),
 * [onObservingLogClick] the observing log (F23).
 */
fun NavGraphBuilder.skyMapScreen(
    onBackClick: () -> Unit,
    onCometClick: (designation: String) -> Unit,
    locationSection: @Composable () -> Unit,
    onObservingLogClick: () -> Unit = {},
) {
    composable<SkyMapRoute> {
        SkyMapScreen(
            onBackClick = onBackClick,
            onCometClick = onCometClick,
            locationSection = locationSection,
            onObservingLogClick = onObservingLogClick,
        )
    }
}
