package com.radustavila.nasaexplorer.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.radustavila.nasaexplorer.feature.earthevents.RightNowOnEarthCard
import com.radustavila.nasaexplorer.feature.earthevents.navigateToEarthEvents
import com.radustavila.nasaexplorer.feature.launches.NextLaunchesCard

/**
 * The cards on the Today tab, above the APOD feed, top to bottom. The registry for every feature
 * that contributes one: add a single line here, calling a composable your feature module exposes.
 *
 * ```
 * { EarthTodayCard(onClick = navController::navigateToEarthToday) },   // F4
 * { NextLaunchesCard() },                                              // F2
 * ```
 *
 * Cards render their own empty/offline state (or nothing at all), and must not scroll vertically:
 * they sit inside the feed's list.
 */
internal fun todayCards(navController: NavHostController): List<@Composable () -> Unit> = listOf(
    { RightNowOnEarthCard(onClick = { navController.navigateToEarthEvents() }) }, // F9
    { NextLaunchesCard() }, // F2
)

/** The feed's header slot: the cards in a column, or no header at all while there are none. */
internal fun todayHeader(navController: NavHostController): (@Composable () -> Unit)? {
    val cards = todayCards(navController)
    if (cards.isEmpty()) return null
    return {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            cards.forEach { card -> card() }
        }
    }
}
