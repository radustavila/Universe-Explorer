package com.radustavila.nasaexplorer.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.ui.graphics.vector.ImageVector
import com.radustavila.nasaexplorer.R
import com.radustavila.nasaexplorer.feature.debug.DebugRoute
import com.radustavila.nasaexplorer.feature.favorites.FavoritesRoute
import com.radustavila.nasaexplorer.feature.feed.FeedRoute
import kotlin.reflect.KClass

/**
 * The bottom-bar destinations. Routes are the types declared by the feature modules, so `:app`
 * wires features together without knowing anything about their internals.
 */
enum class TopLevelDestination(
    val route: KClass<*>,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    @param:StringRes val labelRes: Int,
) {
    FEED(
        route = FeedRoute::class,
        selectedIcon = Icons.Filled.Home,
        unselectedIcon = Icons.Outlined.Home,
        labelRes = R.string.destination_feed,
    ),
    FAVORITES(
        route = FavoritesRoute::class,
        selectedIcon = Icons.Filled.Favorite,
        unselectedIcon = Icons.Outlined.FavoriteBorder,
        labelRes = R.string.destination_favorites,
    ),
    DEBUG(
        route = DebugRoute::class,
        selectedIcon = Icons.Outlined.BugReport,
        unselectedIcon = Icons.Outlined.BugReport,
        labelRes = R.string.destination_debug,
    ),
}
