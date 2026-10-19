package com.radustavila.nasaexplorer.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import com.radustavila.nasaexplorer.R
import com.radustavila.nasaexplorer.feature.debug.debugScreen
import com.radustavila.nasaexplorer.feature.details.detailsScreen
import com.radustavila.nasaexplorer.feature.details.navigateToDetails
import com.radustavila.nasaexplorer.feature.earthmap.earthMapScreen
import com.radustavila.nasaexplorer.feature.favorites.FavoritesRoute
import com.radustavila.nasaexplorer.feature.favorites.favoritesScreen
import com.radustavila.nasaexplorer.feature.feed.FeedRoute
import com.radustavila.nasaexplorer.feature.feed.feedScreen
import com.radustavila.nasaexplorer.feature.mars.marsPhotoScreen
import com.radustavila.nasaexplorer.feature.mars.marsPhotosScreen
import com.radustavila.nasaexplorer.feature.mars.navigateToMarsPhoto
import com.radustavila.nasaexplorer.feature.universe.GalleryRoute
import com.radustavila.nasaexplorer.feature.universe.galleryScreen
import com.radustavila.nasaexplorer.feature.universe.nasaMediaDetailsScreen
import com.radustavila.nasaexplorer.feature.universe.navigateToNasaMedia
import com.radustavila.nasaexplorer.ui.hub.HubScreen

/**
 * The composition root of navigation: the only place that knows every feature's screens.
 *
 * Adding a feature screen means registering its `NavGraphBuilder` extension inside the graph of
 * the tab it belongs to (so that tab stays selected), or at the root for a full-screen detail.
 */
@Composable
fun NasaNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = TodayGraph,
        modifier = modifier,
    ) {
        navigation<TodayGraph>(startDestination = FeedRoute) {
            feedScreen(
                onApodClick = navController::navigateToDetails,
                header = todayHeader(navController),
            )
        }

        // The gallery is the tab's home; the tab's other features are listed above its rails.
        navigation<UniverseGraph>(startDestination = GalleryRoute) {
            galleryScreen(
                onMediaClick = navController::navigateToNasaMedia,
                header = universeHeader(navController),
            )
            marsPhotosScreen(
                onBackClick = navController::popBackStack,
                onPhotoClick = navController::navigateToMarsPhoto,
            )
        }

        navigation<EarthGraph>(startDestination = EarthHomeRoute) {
            composable<EarthHomeRoute> {
                HubScreen(
                    titleRes = R.string.destination_earth,
                    entries = earthHubEntries(navController),
                )
            }
            earthMapScreen(onBackClick = navController::popBackStack)
        }

        navigation<FavoritesGraph>(startDestination = FavoritesRoute) {
            favoritesScreen(onFavoriteClick = navController::openFavorite)
        }

        navigation<MoreGraph>(startDestination = MoreHomeRoute) {
            composable<MoreHomeRoute> {
                HubScreen(
                    titleRes = R.string.destination_more,
                    entries = moreHubEntries(navController),
                )
            }
            debugScreen()
        }

        // Root level: full-screen details reached from several tabs and from deep links.
        detailsScreen(
            onBackClick = navController::popBackStack,
            onApodClick = navController::navigateToDetails,
        )
        nasaMediaDetailsScreen(onBackClick = navController::popBackStack)
        marsPhotoScreen(onBackClick = navController::popBackStack)
    }
}
