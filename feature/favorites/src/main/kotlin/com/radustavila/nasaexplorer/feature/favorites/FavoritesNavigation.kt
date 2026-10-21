package com.radustavila.nasaexplorer.feature.favorites

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import com.radustavila.nasaexplorer.core.designsystem.adaptive.ListDetailHost
import com.radustavila.nasaexplorer.core.designsystem.adaptive.listOnly
import com.radustavila.nasaexplorer.core.model.FavoriteItem
import kotlinx.serialization.Serializable

@Serializable
data object FavoritesRoute

fun NavController.navigateToFavorites(navOptions: NavOptions? = null) =
    navigate(FavoritesRoute, navOptions)

/**
 * Favourites come from every source, and this module cannot know their screens: [onFavoriteClick]
 * (full screen) and [listDetail] (beside the list, where the window allows) are resolved by
 * `:app`, which routes on [FavoriteItem.source].
 */
fun NavGraphBuilder.favoritesScreen(
    onFavoriteClick: (FavoriteItem) -> Unit,
    listDetail: ListDetailHost<FavoriteItem> = listOnly(),
) {
    composable<FavoritesRoute> {
        listDetail(onFavoriteClick) { onItemClick ->
            FavoritesScreen(onFavoriteClick = onItemClick)
        }
    }
}
