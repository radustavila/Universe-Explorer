package com.radustavila.nasaexplorer.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SatelliteAlt
import androidx.navigation.NavHostController
import com.radustavila.nasaexplorer.R
import com.radustavila.nasaexplorer.feature.earthmap.navigateToEarthMap
import com.radustavila.nasaexplorer.ui.hub.HubEntry

/**
 * What the Earth tab offers until one feature becomes its first screen: Earth today (F4), the
 * satellite map with events and aurora (F10, F9, F6), asteroids (F6). Each feature adds its
 * [HubEntry] here and registers its screens inside `navigation<EarthGraph>` in `NasaNavHost`.
 */
internal fun earthHubEntries(navController: NavHostController): List<HubEntry> = listOf(
    HubEntry(
        key = "satellite-map",
        icon = Icons.Outlined.SatelliteAlt,
        titleRes = R.string.earth_satellite_map_title,
        descriptionRes = R.string.earth_satellite_map_description,
        onClick = { navController.navigateToEarthMap() },
    ),
)
