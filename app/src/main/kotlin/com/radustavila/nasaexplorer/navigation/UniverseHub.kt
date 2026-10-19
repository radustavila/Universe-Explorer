package com.radustavila.nasaexplorer.navigation

import androidx.navigation.NavHostController
import com.radustavila.nasaexplorer.ui.hub.HubEntry

/**
 * What the Universe tab offers until one feature becomes its first screen: gallery and search
 * (F3), exoplanets (F8), sky tonight (F5), Mars (F7). Each feature adds its [HubEntry] here and
 * registers its screens inside `navigation<UniverseGraph>` in `NasaNavHost`.
 */
@Suppress("UNUSED_PARAMETER", "UnusedParameter") // Entries navigate with it once features land.
internal fun universeHubEntries(navController: NavHostController): List<HubEntry> = listOf(
)
