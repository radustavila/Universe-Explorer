package com.radustavila.nasaexplorer.feature.favorites

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import java.time.LocalDate
import kotlinx.serialization.Serializable

@Serializable
data object FavoritesRoute

fun NavController.navigateToFavorites(navOptions: NavOptions? = null) =
    navigate(FavoritesRoute, navOptions)

fun NavGraphBuilder.favoritesScreen(onApodClick: (LocalDate) -> Unit) {
    composable<FavoritesRoute> {
        FavoritesScreen(onApodClick = onApodClick)
    }
}
