package com.radustavila.nasaexplorer.feature.feed

import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import androidx.navigation.navDeepLink
import com.radustavila.nasaexplorer.core.model.TodayDeepLink
import java.time.LocalDate
import kotlinx.serialization.Serializable

/**
 * Type-safe navigation contract owned by the feature module: `:app` composes routes, it does not
 * define them.
 */
@Serializable
data object FeedRoute

fun NavController.navigateToFeed(navOptions: NavOptions? = null) = navigate(FeedRoute, navOptions)

/**
 * @param header the Today cards shown above the feed; see [FeedScreen]. The next-launch widget
 * opens this screen through [TodayDeepLink].
 */
fun NavGraphBuilder.feedScreen(
    onApodClick: (LocalDate) -> Unit,
    header: (@Composable () -> Unit)? = null,
) {
    composable<FeedRoute>(
        deepLinks = listOf(navDeepLink { uriPattern = TodayDeepLink.URI }),
    ) {
        FeedScreen(onApodClick = onApodClick, header = header)
    }
}
