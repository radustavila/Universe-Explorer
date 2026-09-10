package com.radustavila.nasaexplorer.feature.feed

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import java.time.LocalDate
import kotlinx.serialization.Serializable

/**
 * Type-safe navigation contract owned by the feature module: `:app` composes routes, it does not
 * define them.
 */
@Serializable
data object FeedRoute

fun NavController.navigateToFeed(navOptions: NavOptions? = null) = navigate(FeedRoute, navOptions)

fun NavGraphBuilder.feedScreen(onApodClick: (LocalDate) -> Unit) {
    composable<FeedRoute> {
        FeedScreen(onApodClick = onApodClick)
    }
}
