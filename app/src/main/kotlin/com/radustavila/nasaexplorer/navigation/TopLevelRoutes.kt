package com.radustavila.nasaexplorer.navigation

import kotlinx.serialization.Serializable

/*
 * One nested navigation graph per bottom-bar tab. Everything registered inside a tab's graph keeps
 * that tab selected and the bar visible; screens registered at the root (details) hide the bar.
 */

@Serializable
data object TodayGraph

@Serializable
data object UniverseGraph

@Serializable
data object EarthGraph

@Serializable
data object FavoritesGraph

@Serializable
data object MoreGraph

/*
 * Start screen of a tab that has no feature of its own: a hub listing what the tab offers (see
 * `MoreHub.kt`). A feature that becomes a tab's first screen replaces its hub as the graph's
 * `startDestination` in `NasaNavHost`, as the gallery did for Universe and Earth today for Earth.
 */

@Serializable
data object MoreHomeRoute
