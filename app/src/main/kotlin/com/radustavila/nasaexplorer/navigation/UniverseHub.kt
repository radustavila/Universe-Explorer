package com.radustavila.nasaexplorer.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import com.radustavila.nasaexplorer.ui.hub.HubEntry
import com.radustavila.nasaexplorer.ui.hub.HubEntryCards

/**
 * What the Universe tab offers besides the gallery (F3), which is the tab's first screen:
 * exoplanets (F8), sky tonight (F5), Mars (F7). Each feature adds its [HubEntry] here and registers
 * its screens inside `navigation<UniverseGraph>` in `NasaNavHost`; the entries are listed above the
 * gallery's rails.
 */
@Suppress("UNUSED_PARAMETER", "UnusedParameter") // Entries navigate with it once features land.
internal fun universeHubEntries(navController: NavHostController): List<HubEntry> = listOf(
)

/** The gallery's header slot: the other Universe entries, or no header while there are none. */
internal fun universeHeader(navController: NavHostController): (@Composable () -> Unit)? {
    val entries = universeHubEntries(navController)
    if (entries.isEmpty()) return null
    return { HubEntryCards(entries) }
}
