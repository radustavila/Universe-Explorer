package com.radustavila.nasaexplorer.feature.earthevents

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import androidx.navigation.navDeepLink
import com.radustavila.nasaexplorer.core.model.EarthEventsDeepLink
import kotlinx.serialization.Serializable

/**
 * The Live Earth events map. With [earthquake] (a USGS event id) it opens on that quake, its sheet
 * open.
 */
@Serializable
data class EarthEventsRoute(val earthquake: String? = null)

fun NavController.navigateToEarthEvents(earthquakeId: String? = null, navOptions: NavOptions? = null) =
    navigate(EarthEventsRoute(earthquake = earthquakeId), navOptions)

/** The strong-quake alert opens a quake through [EarthEventsDeepLink]. */
fun NavGraphBuilder.earthEventsScreen(onBackClick: () -> Unit) {
    composable<EarthEventsRoute>(
        deepLinks = listOf(
            navDeepLink {
                uriPattern = "${EarthEventsDeepLink.URI}?${EarthEventsDeepLink.EARTHQUAKE_ARGUMENT}={earthquake}"
            },
        ),
    ) {
        EarthEventsRoute(onBackClick = onBackClick)
    }
}
