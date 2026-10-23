// Named after the feature, as every module's navigation file is, not after its route classes.
@file:Suppress("MatchingDeclarationName")

package com.radustavila.nasaexplorer.feature.launches

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import androidx.navigation.navDeepLink
import com.radustavila.nasaexplorer.core.designsystem.adaptive.ListDetailHost
import com.radustavila.nasaexplorer.core.designsystem.adaptive.listOnly
import com.radustavila.nasaexplorer.core.model.LaunchDeepLink
import com.radustavila.nasaexplorer.feature.launches.details.LaunchDetailsScreen
import com.radustavila.nasaexplorer.feature.launches.list.LaunchesScreen
import kotlinx.serialization.Serializable

/** Upcoming and past launches (F27). */
@Serializable
data object LaunchesRoute

/** One launch by its Launch Library 2 id. */
@Serializable
data class LaunchDetailsRoute(val id: String)

fun NavController.navigateToLaunches(navOptions: NavOptions? = null) = navigate(LaunchesRoute, navOptions)

fun NavController.navigateToLaunchDetails(id: String, navOptions: NavOptions? = null) =
    navigate(LaunchDetailsRoute(id), navOptions)

/**
 * The Launches screen, registered inside the Today tab's graph (its card lives there); also opened
 * by `nasaexplorer://launches` (the launcher shortcut).
 *
 * @param listDetail places the list beside the launch it opened, where the window allows.
 */
fun NavGraphBuilder.launchesScreen(
    onBackClick: () -> Unit,
    onLaunchClick: (id: String) -> Unit,
    listDetail: ListDetailHost<String> = listOnly(),
) {
    composable<LaunchesRoute>(deepLinks = listOf(navDeepLink { uriPattern = LaunchDeepLink.LIST_URI })) {
        listDetail(onLaunchClick) { onItemClick ->
            LaunchesScreen(onBackClick = onBackClick, onLaunchClick = onItemClick)
        }
    }
}

/**
 * One launch full screen; reached from the card, the list, search and the reminder notification
 * (`nasaexplorer://launch/{id}`), so registered at the root.
 */
fun NavGraphBuilder.launchDetailsScreen(onBackClick: () -> Unit) {
    composable<LaunchDetailsRoute>(
        deepLinks = listOf(navDeepLink<LaunchDetailsRoute>(basePath = LaunchDeepLink.BASE_URI)),
    ) {
        LaunchDetailsScreen(onBackClick = onBackClick)
    }
}

/** The same page inside the list-detail pane, which must not declare deep links. */
fun NavGraphBuilder.launchDetailsPane(onBackClick: () -> Unit) {
    composable<LaunchDetailsRoute> {
        LaunchDetailsScreen(onBackClick = onBackClick)
    }
}
