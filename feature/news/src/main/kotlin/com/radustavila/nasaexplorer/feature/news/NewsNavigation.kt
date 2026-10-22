package com.radustavila.nasaexplorer.feature.news

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import com.radustavila.nasaexplorer.feature.news.item.NewsItemScreen
import com.radustavila.nasaexplorer.feature.news.list.NewsScreen
import kotlinx.serialization.Serializable

/** Every cached story, article, Image of the Day and video, newest first. */
@Serializable
data object NewsRoute

/** One news item by its page URL: Images of the Day open here, and search results of any kind. */
@Serializable
data class NewsItemRoute(val id: String)

fun NavController.navigateToNews(navOptions: NavOptions? = null) = navigate(NewsRoute, navOptions)

fun NavController.navigateToNewsItem(id: String, navOptions: NavOptions? = null) =
    navigate(NewsItemRoute(id), navOptions)

/**
 * Stories and videos open outside the app (a Custom Tab, YouTube); [onImageClick] opens an Image of
 * the Day on its own screen.
 */
fun NavGraphBuilder.newsScreen(
    onBackClick: () -> Unit,
    onImageClick: (id: String) -> Unit,
) {
    composable<NewsRoute> {
        NewsScreen(onBackClick = onBackClick, onImageClick = onImageClick)
    }
}

fun NavGraphBuilder.newsItemScreen(onBackClick: () -> Unit) {
    composable<NewsItemRoute> {
        NewsItemScreen(onBackClick = onBackClick)
    }
}
