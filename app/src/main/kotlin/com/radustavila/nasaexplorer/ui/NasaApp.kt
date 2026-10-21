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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navOptions
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
        // Exposes test tags as resource ids, so UI Automator (the baseline profile generator and
        // the startup benchmark) can find composables by tag.
        modifier = modifier
            .fillMaxSize()
            .semantics { testTagsAsResourceId = true },
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

/** A destination belongs to a tab when it sits anywhere inside that tab's nested graph. */
private fun NavDestination?.isOn(destination: TopLevelDestination): Boolean =
    this?.hierarchy?.any { it.hasRoute(destination.graph::class) } == true

/**
 * Switches tabs keeping one back stack per tab: the current tab's stack is saved, the target's is
 * restored, and re-selecting a tab does not stack a second copy of it.
 */
internal fun NavHostController.navigateToTopLevel(destination: TopLevelDestination) {
    navigate(
        destination.graph,
        navOptions {
            popUpTo(graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        },
    )
}
