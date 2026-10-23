package com.radustavila.nasaexplorer.feature.news

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.radustavila.nasaexplorer.feature.news.item.NewsItemScreen
import com.radustavila.nasaexplorer.feature.news.list.NewsScreen
import com.radustavila.nasaexplorer.feature.news.list.NewsTab
import com.radustavila.nasaexplorer.feature.news.podcast.episode.PodcastEpisodeScreen
import kotlinx.serialization.Serializable

/**
 * Every cached story, article, Image of the Day and video, newest first, and NASA's podcasts in
 * their own tab ([podcasts] opens on it).
 */
@Serializable
data class NewsRoute(val podcasts: Boolean = false)

/** One podcast episode (F21) by its id: from the Podcasts tab, Favourites and search. */
@Serializable
data class PodcastEpisodeRoute(val id: String)

/** One news item by its page URL: Images of the Day open here, and search results of any kind. */
@Serializable
data class NewsItemRoute(val id: String)

fun NavController.navigateToNews(navOptions: NavOptions? = null) = navigate(NewsRoute(), navOptions)

fun NavController.navigateToPodcasts(navOptions: NavOptions? = null) = navigate(NewsRoute(podcasts = true), navOptions)

fun NavController.navigateToPodcastEpisode(id: String, navOptions: NavOptions? = null) =
    navigate(PodcastEpisodeRoute(id), navOptions)

fun NavController.navigateToNewsItem(id: String, navOptions: NavOptions? = null) =
    navigate(NewsItemRoute(id), navOptions)

/**
 * Stories and videos open outside the app (a Custom Tab, YouTube); [onImageClick] opens an Image of
 * the Day on its own screen, [onEpisodeClick] a podcast episode.
 */
fun NavGraphBuilder.newsScreen(
    onBackClick: () -> Unit,
    onImageClick: (id: String) -> Unit,
    onEpisodeClick: (id: String) -> Unit,
) {
    composable<NewsRoute> { entry ->
        NewsScreen(
            onBackClick = onBackClick,
            onImageClick = onImageClick,
            onEpisodeClick = onEpisodeClick,
            initialTab = if (entry.toRoute<NewsRoute>().podcasts) NewsTab.PODCASTS else NewsTab.NEWS,
        )
    }
}

fun NavGraphBuilder.podcastEpisodeScreen(onBackClick: () -> Unit) {
    composable<PodcastEpisodeRoute> {
        PodcastEpisodeScreen(onBackClick = onBackClick)
    }
}

fun NavGraphBuilder.newsItemScreen(onBackClick: () -> Unit) {
    composable<NewsItemRoute> {
        NewsItemScreen(onBackClick = onBackClick)
    }
}
