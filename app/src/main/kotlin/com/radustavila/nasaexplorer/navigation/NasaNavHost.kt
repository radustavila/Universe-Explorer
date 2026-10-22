package com.radustavila.nasaexplorer.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import com.radustavila.nasaexplorer.R
import com.radustavila.nasaexplorer.feature.debug.debugScreen
import com.radustavila.nasaexplorer.feature.details.detailsPane
import com.radustavila.nasaexplorer.feature.details.detailsScreen
import com.radustavila.nasaexplorer.feature.details.navigateToDetails
import com.radustavila.nasaexplorer.feature.earth.EarthTodayRoute
import com.radustavila.nasaexplorer.feature.earth.earthTodayScreen
import com.radustavila.nasaexplorer.feature.earthevents.earthEventsScreen
import com.radustavila.nasaexplorer.feature.earthmap.earthMapScreen
import com.radustavila.nasaexplorer.feature.dsn.dsnScreen
import com.radustavila.nasaexplorer.feature.exoplanets.exoplanetDetailsScreen
import com.radustavila.nasaexplorer.feature.exoplanets.exoplanetsScreen
import com.radustavila.nasaexplorer.feature.exoplanets.navigateToExoplanetDetails
import com.radustavila.nasaexplorer.feature.favorites.FavoritesRoute
import com.radustavila.nasaexplorer.feature.favorites.favoritesScreen
import com.radustavila.nasaexplorer.feature.feed.FeedRoute
import com.radustavila.nasaexplorer.feature.feed.feedScreen
import com.radustavila.nasaexplorer.feature.mars.marsPhotoScreen
import com.radustavila.nasaexplorer.feature.mars.marsPhotosScreen
import com.radustavila.nasaexplorer.feature.mars.navigateToMarsPhoto
import com.radustavila.nasaexplorer.feature.news.navigateToNewsItem
import com.radustavila.nasaexplorer.feature.news.newsItemScreen
import com.radustavila.nasaexplorer.feature.news.newsScreen
import com.radustavila.nasaexplorer.feature.search.searchScreen
import com.radustavila.nasaexplorer.feature.settings.aboutScreen
import com.radustavila.nasaexplorer.feature.settings.customizeTodayScreen
import com.radustavila.nasaexplorer.feature.settings.licencesScreen
import com.radustavila.nasaexplorer.feature.settings.navigateToCustomizeToday
import com.radustavila.nasaexplorer.feature.settings.navigateToLicences
import com.radustavila.nasaexplorer.feature.settings.settingsScreen
import com.radustavila.nasaexplorer.feature.sky.location.ObserverLocationSection
import com.radustavila.nasaexplorer.feature.sky.skyTonightScreen
import com.radustavila.nasaexplorer.feature.spaceweather.navigateToSmallBody
import com.radustavila.nasaexplorer.feature.spaceweather.neighbourhoodScreen
import com.radustavila.nasaexplorer.feature.spaceweather.smallBodyScreen
import com.radustavila.nasaexplorer.feature.spaceweather.spaceWeatherScreen
import com.radustavila.nasaexplorer.feature.universe.GalleryRoute
import com.radustavila.nasaexplorer.feature.universe.galleryScreen
import com.radustavila.nasaexplorer.feature.universe.gallerySearchScreen
import com.radustavila.nasaexplorer.feature.universe.nasaMediaDetailsScreen
import com.radustavila.nasaexplorer.feature.universe.navigateToGallerySearch
import com.radustavila.nasaexplorer.feature.universe.navigateToNasaMedia
import com.radustavila.nasaexplorer.ui.hub.HubScreen

/**
 * The composition root of navigation: the only place that knows every feature's screens.
 *
 * Adding a feature screen means registering its `NavGraphBuilder` extension inside the graph of
 * the tab it belongs to (so that tab stays selected), or at the root for a full-screen detail.
 * A list whose items have a detail screen also gets a [listDetail] host: on a window with room for
 * two panes, the item opens beside the list instead of full screen.
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
                listDetail = listDetail(
                    openInPane = { date, navOptions -> navigateToDetails(date, navOptions) },
                ) { pane, onBackClick ->
                    detailsPane(onBackClick = onBackClick, onApodClick = pane::navigateToDetails)
                },
            )
        }

        // The gallery is the tab's home; the tab's other features are listed above its rails.
        navigation<UniverseGraph>(startDestination = GalleryRoute) {
            galleryScreen(
                onMediaClick = navController::navigateToNasaMedia,
                header = universeHeader(navController),
                listDetail = listDetail(
                    openInPane = { nasaId, navOptions -> navigateToNasaMedia(nasaId, navOptions) },
                ) { _, onBackClick ->
                    nasaMediaDetailsScreen(onBackClick = onBackClick)
                },
            )
            marsPhotosScreen(
                onBackClick = navController::popBackStack,
                onPhotoClick = navController::navigateToMarsPhoto,
            )
            exoplanetsScreen(
                onBackClick = navController::popBackStack,
                onExoplanetClick = navController::navigateToExoplanetDetails,
                listDetail = listDetail(
                    openInPane = { name, navOptions -> navigateToExoplanetDetails(name, navOptions) },
                ) { _, onBackClick ->
                    exoplanetDetailsScreen(onBackClick = onBackClick)
                },
            )
            skyTonightScreen(onBackClick = navController::popBackStack)
            dsnScreen(onBackClick = navController::popBackStack)
            newsScreen(onBackClick = navController::popBackStack, onImageClick = navController::navigateToNewsItem)
            gallerySearchScreen(
                onMediaClick = navController::navigateToNasaMedia,
                listDetail = listDetail(
                    openInPane = { nasaId, navOptions -> navigateToNasaMedia(nasaId, navOptions) },
                ) { _, onBackClick ->
                    nasaMediaDetailsScreen(onBackClick = onBackClick)
                },
            )
        }

        // Earth today is the tab's home; the tab's other features are listed below the rotation.
        navigation<EarthGraph>(startDestination = EarthTodayRoute()) {
            earthTodayScreen(
                onBackClick = navController::popBackStack,
                footer = earthFooter(navController),
            )
            earthMapScreen(onBackClick = navController::popBackStack)
            earthEventsScreen(onBackClick = navController::popBackStack)
            spaceWeatherScreen(onBackClick = navController::popBackStack)
            neighbourhoodScreen(
                onBackClick = navController::popBackStack,
                onApproachClick = navController::navigateToSmallBody,
            )
            smallBodyScreen(onBackClick = navController::popBackStack)
        }

        navigation<FavoritesGraph>(startDestination = FavoritesRoute) {
            favoritesScreen(
                onFavoriteClick = navController::openFavorite,
                listDetail = listDetail(
                    openInPane = { item, navOptions -> openFavorite(item, navOptions) },
                    opensInPane = ::opensInFavoritesPane,
                ) { pane, onBackClick ->
                    // The pane hosts every screen a favourite opens in, except EPIC's.
                    detailsPane(onBackClick = onBackClick, onApodClick = pane::navigateToDetails)
                    nasaMediaDetailsScreen(onBackClick = onBackClick)
                    exoplanetDetailsScreen(onBackClick = onBackClick)
                    marsPhotoScreen(onBackClick = onBackClick)
                    newsItemScreen(onBackClick = onBackClick)
                },
            )
        }

        navigation<MoreGraph>(startDestination = MoreHomeRoute) {
            composable<MoreHomeRoute> {
                HubScreen(
                    titleRes = R.string.destination_more,
                    entries = moreHubEntries(navController),
                )
            }
            settingsScreen(
                onBackClick = navController::popBackStack,
                onCustomizeTodayClick = navController::navigateToCustomizeToday,
                locationSection = { modifier -> ObserverLocationSection(modifier) },
            )
            aboutScreen(onBackClick = navController::popBackStack, onLicencesClick = navController::navigateToLicences)
            licencesScreen(onBackClick = navController::popBackStack, librariesRes = R.raw.aboutlibraries)
            debugScreen()
        }

        // Root level: full-screen details reached from several tabs and from deep links.
        detailsScreen(
            onBackClick = navController::popBackStack,
            onApodClick = navController::navigateToDetails,
        )
        nasaMediaDetailsScreen(onBackClick = navController::popBackStack)
        marsPhotoScreen(onBackClick = navController::popBackStack)
        exoplanetDetailsScreen(onBackClick = navController::popBackStack)
        newsItemScreen(onBackClick = navController::popBackStack)
        // From Settings and from Today's header, without switching tabs.
        customizeTodayScreen(onBackClick = navController::popBackStack)
        searchScreen(
            onBackClick = navController::popBackStack,
            onResultClick = navController::openSearchTarget,
            onSearchOnline = navController::navigateToGallerySearch,
        )
    }
}
