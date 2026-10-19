package com.radustavila.nasaexplorer.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.SatelliteAlt
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.radustavila.nasaexplorer.R
import com.radustavila.nasaexplorer.feature.earthevents.navigateToEarthEvents
import com.radustavila.nasaexplorer.feature.earthmap.navigateToEarthMap
import com.radustavila.nasaexplorer.feature.spaceweather.navigateToNeighbourhood
import com.radustavila.nasaexplorer.feature.spaceweather.navigateToSpaceWeather
import com.radustavila.nasaexplorer.ui.hub.HubEntry
import com.radustavila.nasaexplorer.ui.hub.HubEntryCards

/**
 * What the Earth tab offers besides Earth today (F4), which is the tab's first screen: the
 * satellite map (F10), events (F9), aurora and space weather, asteroids (F6). Each feature adds its
 * [HubEntry] here and registers its screens inside `navigation<EarthGraph>` in `NasaNavHost`; the
 * entries are listed below the rotation.
 */
internal fun earthHubEntries(navController: NavHostController): List<HubEntry> = listOf(
    HubEntry(
        key = "satellite-map",
        icon = Icons.Outlined.SatelliteAlt,
        titleRes = R.string.earth_satellite_map_title,
        descriptionRes = R.string.earth_satellite_map_description,
        onClick = { navController.navigateToEarthMap() },
    ),
    HubEntry(
        key = "earth-events",
        icon = Icons.Outlined.Public,
        titleRes = R.string.earth_live_events_title,
        descriptionRes = R.string.earth_live_events_description,
        onClick = { navController.navigateToEarthEvents() },
    ),
    HubEntry(
        key = "space-weather",
        icon = Icons.Outlined.AutoAwesome,
        titleRes = R.string.earth_space_weather_title,
        descriptionRes = R.string.earth_space_weather_description,
        onClick = { navController.navigateToSpaceWeather() },
    ),
    HubEntry(
        key = "neighbourhood",
        icon = Icons.Outlined.Explore,
        titleRes = R.string.earth_neighbourhood_title,
        descriptionRes = R.string.earth_neighbourhood_description,
        onClick = { navController.navigateToNeighbourhood() },
    ),
)

/** Earth today's footer slot: the tab's other entries, or nothing while there are none. */
internal fun earthFooter(navController: NavHostController): (@Composable () -> Unit)? {
    val entries = earthHubEntries(navController)
    if (entries.isEmpty()) return null
    return { HubEntryCards(entries, Modifier.padding(horizontal = 16.dp)) }
}
