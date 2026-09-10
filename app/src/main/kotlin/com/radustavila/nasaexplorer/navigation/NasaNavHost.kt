package com.radustavila.nasaexplorer.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import com.radustavila.nasaexplorer.feature.debug.debugScreen
import com.radustavila.nasaexplorer.feature.details.detailsScreen
import com.radustavila.nasaexplorer.feature.details.navigateToDetails
import com.radustavila.nasaexplorer.feature.favorites.favoritesScreen
import com.radustavila.nasaexplorer.feature.feed.FeedRoute
import com.radustavila.nasaexplorer.feature.feed.feedScreen

@Composable
fun NasaNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = FeedRoute,
        modifier = modifier,
    ) {
        feedScreen(onApodClick = navController::navigateToDetails)
        favoritesScreen(onApodClick = navController::navigateToDetails)
        debugScreen()
        detailsScreen(onBackClick = navController::popBackStack)
    }
}
