package com.radustavila.nasaexplorer.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navOptions
import com.radustavila.nasaexplorer.feature.debug.navigateToDebug
import com.radustavila.nasaexplorer.feature.favorites.navigateToFavorites
import com.radustavila.nasaexplorer.feature.feed.navigateToFeed
import com.radustavila.nasaexplorer.navigation.NasaNavHost
import com.radustavila.nasaexplorer.navigation.TopLevelDestination

@Composable
fun NasaApp(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = currentBackStackEntry?.destination
    val topLevelDestinations = remember { TopLevelDestination.entries }
    val currentTopLevelDestination = topLevelDestinations.firstOrNull { destination ->
        currentDestination.isOn(destination)
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        // Insets are consumed by the screens themselves (their top bars) and by the navigation
        // bar below; applying them here as well padded every screen twice.
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            // The bottom bar belongs to the top-level destinations only; details is a detail screen.
            if (currentTopLevelDestination != null) {
                NavigationBar {
                    topLevelDestinations.forEach { destination ->
                        val selected = destination == currentTopLevelDestination
                        NavigationBarItem(
                            selected = selected,
                            onClick = { navController.navigateToTopLevel(destination) },
                            icon = {
                                Icon(
                                    imageVector = if (selected) {
                                        destination.selectedIcon
                                    } else {
                                        destination.unselectedIcon
                                    },
                                    contentDescription = null,
                                )
                            },
                            label = { Text(stringResource(destination.labelRes)) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NasaNavHost(
            navController = navController,
            modifier = Modifier.padding(padding),
        )
    }
}

private fun NavDestination?.isOn(destination: TopLevelDestination): Boolean =
    this?.hierarchy?.any { it.hasRoute(destination.route) } == true

private fun NavHostController.navigateToTopLevel(destination: TopLevelDestination) {
    val navOptions = navOptions {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }

    when (destination) {
        TopLevelDestination.FEED -> navigateToFeed(navOptions)
        TopLevelDestination.FAVORITES -> navigateToFavorites(navOptions)
        TopLevelDestination.DEBUG -> navigateToDebug(navOptions)
    }
}
