package com.radustavila.nasaexplorer.feature.feed

import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import com.radustavila.nasaexplorer.core.designsystem.adaptive.ListDetailHost
import com.radustavila.nasaexplorer.core.designsystem.adaptive.listOnly
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
 * @param onApodClick opens an entry full screen.
 * @param header the Today cards shown above the feed; see [FeedScreen].
 * @param listDetail places the feed beside the entry it opened, where the window allows.
 */
fun NavGraphBuilder.feedScreen(
    onApodClick: (LocalDate) -> Unit,
    header: (@Composable () -> Unit)? = null,
    listDetail: ListDetailHost<LocalDate> = listOnly(),
) {
    composable<FeedRoute> {
        listDetail(onApodClick) { onItemClick ->
            FeedScreen(onApodClick = onItemClick, header = header)
        }
    }
}
