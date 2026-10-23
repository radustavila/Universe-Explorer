package com.radustavila.nasaexplorer.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.BlurOn
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Newspaper
import androidx.compose.material.icons.outlined.NightsStay
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SettingsInputAntenna
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.radustavila.nasaexplorer.R
import com.radustavila.nasaexplorer.feature.comets.navigateToComets
import com.radustavila.nasaexplorer.feature.dsn.navigateToDsn
import com.radustavila.nasaexplorer.feature.events.navigateToSkyEvents
import com.radustavila.nasaexplorer.feature.exoplanets.navigateToExoplanets
import com.radustavila.nasaexplorer.feature.mars.navigateToMarsPhoto
import com.radustavila.nasaexplorer.feature.mars.navigateToMarsPhotos
import com.radustavila.nasaexplorer.feature.mars.rail.MarsRail
import com.radustavila.nasaexplorer.feature.milkyway.navigateToMilkyWay
import com.radustavila.nasaexplorer.feature.news.navigateToNews
import com.radustavila.nasaexplorer.feature.search.navigateToSearch
import com.radustavila.nasaexplorer.feature.sky.navigateToSkyTonight
import com.radustavila.nasaexplorer.feature.skymap.navigateToSkyMap
import com.radustavila.nasaexplorer.feature.universe.navigateToGallerySearch
import com.radustavila.nasaexplorer.feature.webb.WebbNowCard
import com.radustavila.nasaexplorer.feature.webb.navigateToWebb
import com.radustavila.nasaexplorer.ui.hub.HubEntry
import com.radustavila.nasaexplorer.ui.hub.HubEntryCards

/**
 * What the Universe tab offers besides the gallery (F3), which is the tab's first screen:
 * search over everything on the device, exoplanets (F8), sky tonight (F5), Mars (F7), NASA news (F14). Each feature adds its [HubEntry] here and registers
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
        key = "dsn",
        icon = Icons.Outlined.SettingsInputAntenna,
        titleRes = R.string.universe_dsn_title,
        descriptionRes = R.string.universe_dsn_description,
        onClick = { navController.navigateToDsn() },
    ), // F13
    HubEntry(
        "sky-events",
        Icons.Outlined.Event,
        R.string.universe_sky_events_title,
        R.string.universe_sky_events_description,
    ) {
        navController.navigateToSkyEvents()
    }, // F12
    HubEntry("news", Icons.Outlined.Newspaper, R.string.universe_news_title, R.string.universe_news_description) {
        navController.navigateToNews()
    },
) + observingHubEntries(navController)

/** The Universe entries for going out to look: the sky map (F17), comets (F18), the Milky Way (F20). */
private fun observingHubEntries(navController: NavHostController): List<HubEntry> = listOf(
    HubEntry(
        key = "skymap",
        icon = Icons.Outlined.Explore,
        titleRes = R.string.universe_skymap_title,
        descriptionRes = R.string.universe_skymap_description,
        onClick = { navController.navigateToSkyMap() },
    ), // F17
    HubEntry(
        key = "comets",
        icon = Icons.Outlined.AutoAwesome,
        titleRes = R.string.universe_comets_title,
        descriptionRes = R.string.universe_comets_description,
        onClick = { navController.navigateToComets() },
    ), // F18
    HubEntry(
        key = "milky-way",
        icon = Icons.Outlined.BlurOn,
        titleRes = R.string.universe_milky_way_title,
        descriptionRes = R.string.universe_milky_way_description,
        onClick = { navController.navigateToMilkyWay() },
    ), // F20
)

/**
 * The gallery's header slot: the "Webb right now" card (F24), the other Universe entries, then the
 * rails that are not the library's (Mars). The header draws its own side margins, so rails can
 * scroll edge to edge.
 */
internal fun universeHeader(navController: NavHostController): @Composable () -> Unit {
    val entries = universeHubEntries(navController)
    return {
        Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
            // Live, so above the entries rather than below the ten of them.
            WebbNowCard(
                onOpen = { navController.navigateToWebb() },
                onTargetClick = { query -> navController.navigateToGallerySearch(query) },
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            if (entries.isNotEmpty()) HubEntryCards(entries, Modifier.padding(horizontal = 16.dp))
            MarsRail(
                onPhotoClick = navController::navigateToMarsPhoto,
                onSeeAllClick = navController::navigateToMarsPhotos,
            )
        }
    }
}
