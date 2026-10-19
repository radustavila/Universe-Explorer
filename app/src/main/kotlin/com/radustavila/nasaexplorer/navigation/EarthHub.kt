package com.radustavila.nasaexplorer.navigation

import androidx.navigation.NavHostController
import com.radustavila.nasaexplorer.ui.hub.HubEntry

/**
 * What the Earth tab offers until one feature becomes its first screen: Earth today (F4), the
 * satellite map with events and aurora (F10, F9, F6), asteroids (F6). Each feature adds its
 * [HubEntry] here and registers its screens inside `navigation<EarthGraph>` in `NasaNavHost`.
 */
@Suppress("UNUSED_PARAMETER", "UnusedParameter") // Entries navigate with it once features land.
internal fun earthHubEntries(navController: NavHostController): List<HubEntry> = listOf(
)
