package com.radustavila.nasaexplorer.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Today
import androidx.compose.ui.graphics.vector.ImageVector
import com.radustavila.nasaexplorer.R

/**
 * The five bottom-bar tabs, decided once for the whole roadmap:
 *
 * - **Today**: the APOD feed, with other features' cards above it (`TodayCards.kt`).
 * - **Universe**: gallery and search, exoplanets, sky tonight, Mars.
 * - **Earth**: Earth today, satellite map with events and aurora, asteroids.
 * - **Favourites**: saved items of every source.
 * - **More**: settings, Debug, the full launch list.
 *
 * [graph] is the tab's nested navigation graph (see `TopLevelRoutes.kt`). Feature modules never
 * edit this enum; they register screens inside a tab's graph in `NasaNavHost`.
 */
enum class TopLevelDestination(
    val graph: Any,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    @param:StringRes val labelRes: Int,
) {
    TODAY(
        graph = TodayGraph,
        selectedIcon = Icons.Filled.Today,
        unselectedIcon = Icons.Outlined.Today,
        labelRes = R.string.destination_today,
    ),
    UNIVERSE(
        graph = UniverseGraph,
        selectedIcon = Icons.Filled.AutoAwesome,
        unselectedIcon = Icons.Outlined.AutoAwesome,
        labelRes = R.string.destination_universe,
    ),
    EARTH(
        graph = EarthGraph,
        selectedIcon = Icons.Filled.Public,
        unselectedIcon = Icons.Outlined.Public,
        labelRes = R.string.destination_earth,
    ),
    FAVORITES(
        graph = FavoritesGraph,
        selectedIcon = Icons.Filled.Favorite,
        unselectedIcon = Icons.Outlined.FavoriteBorder,
        labelRes = R.string.destination_favorites,
    ),
    MORE(
        graph = MoreGraph,
        selectedIcon = Icons.Filled.MoreHoriz,
        unselectedIcon = Icons.Outlined.MoreHoriz,
        labelRes = R.string.destination_more,
    ),
}
