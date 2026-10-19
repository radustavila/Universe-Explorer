package com.radustavila.nasaexplorer.feature.favorites

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import com.radustavila.nasaexplorer.core.model.FavoriteItem
import kotlinx.serialization.Serializable

@Serializable
data object FavoritesRoute

fun NavController.navigateToFavorites(navOptions: NavOptions? = null) =
    navigate(FavoritesRoute, navOptions)

/**
 * Favourites come from every source, and this module cannot know their screens: [onFavoriteClick]
 * is resolved by `:app`, which routes on [FavoriteItem.source].
 */
fun NavGraphBuilder.favoritesScreen(onFavoriteClick: (FavoriteItem) -> Unit) {
    composable<FavoritesRoute> {
        FavoritesScreen(onFavoriteClick = onFavoriteClick)
    }
}
