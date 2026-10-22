package com.radustavila.nasaexplorer.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.NightsStay
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Search
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.radustavila.nasaexplorer.R
import com.radustavila.nasaexplorer.feature.events.navigateToSkyEvents
import com.radustavila.nasaexplorer.feature.exoplanets.navigateToExoplanets
import com.radustavila.nasaexplorer.feature.mars.navigateToMarsPhoto
import com.radustavila.nasaexplorer.feature.mars.navigateToMarsPhotos
import com.radustavila.nasaexplorer.feature.mars.rail.MarsRail
import com.radustavila.nasaexplorer.feature.search.navigateToSearch
import com.radustavila.nasaexplorer.feature.sky.navigateToSkyTonight
import com.radustavila.nasaexplorer.ui.hub.HubEntry
import com.radustavila.nasaexplorer.ui.hub.HubEntryCards

/**
 * What the Universe tab offers besides the gallery (F3), which is the tab's first screen:
 * search over everything on the device, exoplanets (F8), sky tonight (F5), Mars (F7). Each feature adds its [HubEntry] here and registers
 * its screens inside `navigation<UniverseGraph>` in `NasaNavHost`; the entries are listed above the
 * gallery's rails.
 */
internal fun universeHubEntries(navController: NavHostController): List<HubEntry> = listOf(
    HubEntry(
        key = "search",
        icon = Icons.Outlined.Search,
        titleRes = R.string.universe_search_title,
        descriptionRes = R.string.universe_search_description,
        onClick = { navController.navigateToSearch() },
    ),
    HubEntry(
        key = "exoplanets",
        icon = Icons.Outlined.Public,
        titleRes = R.string.universe_exoplanets_title,
        descriptionRes = R.string.universe_exoplanets_description,
        onClick = { navController.navigateToExoplanets() },
    ),
    HubEntry(
        key = "mars",
        icon = Icons.Outlined.PhotoCamera,
        titleRes = R.string.universe_mars_title,
        descriptionRes = R.string.universe_mars_description,
        onClick = { navController.navigateToMarsPhotos() },
    ),
    HubEntry("sky", Icons.Outlined.NightsStay, R.string.universe_sky_title, R.string.universe_sky_description) {
        navController.navigateToSkyTonight()
    },
    HubEntry(
        "sky-events",
        Icons.Outlined.Event,
        R.string.universe_sky_events_title,
        R.string.universe_sky_events_description,
    ) {
        navController.navigateToSkyEvents()
    }, // F12
)

/**
 * The gallery's header slot: the other Universe entries, then the rails that are not the library's
 * (Mars). The header draws its own side margins, so rails can scroll edge to edge.
 */
internal fun universeHeader(navController: NavHostController): @Composable () -> Unit {
    val entries = universeHubEntries(navController)
    return {
        Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
            if (entries.isNotEmpty()) HubEntryCards(entries, Modifier.padding(horizontal = 16.dp))
            MarsRail(
                onPhotoClick = navController::navigateToMarsPhoto,
                onSeeAllClick = navController::navigateToMarsPhotos,
            )
        }
    }
}
