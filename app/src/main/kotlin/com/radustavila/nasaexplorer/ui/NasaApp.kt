package com.radustavila.nasaexplorer.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.WindowAdaptiveInfo
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteItem
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.window.core.layout.WindowSizeClass
import com.radustavila.nasaexplorer.core.designsystem.adaptive.LocalWindowAdaptiveInfo
import com.radustavila.nasaexplorer.core.designsystem.adaptive.isHeightCompact
import com.radustavila.nasaexplorer.core.designsystem.adaptive.isWidthCompact
import com.radustavila.nasaexplorer.navigation.NasaNavHost
import com.radustavila.nasaexplorer.navigation.TopLevelDestination

@Composable
fun NasaApp(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = currentBackStackEntry?.destination
    val currentTopLevelDestination = TopLevelDestination.entries.firstOrNull { destination ->
        currentDestination.isOn(destination)
    }

    ProvideWindowAdaptiveInfo {
        NasaNavigationSuite(
            currentTopLevelDestination = currentTopLevelDestination,
            onTopLevelDestinationClick = navController::navigateToTopLevel,
            modifier = modifier,
        ) {
            NasaNavHost(navController = navController)
        }
    }
}

/**
 * Measures the window once for the whole UI and hands it down through [LocalWindowAdaptiveInfo]:
 * the navigation suite, the list-detail panes and the features all read the same size classes.
 */
@Composable
internal fun ProvideWindowAdaptiveInfo(content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalWindowAdaptiveInfo provides currentWindowAdaptiveInfoV2(),
        content = content,
    )
}

/**
 * The tabs as a bottom bar on phones, a rail beside the content on wider windows and a permanent
 * drawer on very wide ones. Only the tabs' own screens show it: a detail screen registered at
 * the root ([currentTopLevelDestination] null) takes the whole window.
 */
@Composable
internal fun NasaNavigationSuite(
    currentTopLevelDestination: TopLevelDestination?,
    onTopLevelDestinationClick: (TopLevelDestination) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val topLevelDestinations = remember { TopLevelDestination.entries }
    val windowAdaptiveInfo = LocalWindowAdaptiveInfo.current
    val navigationSuiteType = if (currentTopLevelDestination == null) {
        NavigationSuiteType.None
    } else {
        navigationSuiteType(windowAdaptiveInfo)
    }

    NavigationSuiteScaffold(
        navigationItems = {
            topLevelDestinations.forEach { destination ->
                val selected = destination == currentTopLevelDestination
                NavigationSuiteItem(
                    selected = selected,
                    onClick = { onTopLevelDestinationClick(destination) },
                    icon = {
                        Icon(
                            imageVector = if (selected) destination.selectedIcon else destination.unselectedIcon,
                            contentDescription = null,
                        )
                    },
                    label = { Text(stringResource(destination.labelRes)) },
                    navigationSuiteType = navigationSuiteType,
                )
            }
        },
        modifier = modifier.fillMaxSize(),
        navigationSuiteType = navigationSuiteType,
    ) {
        // Insets are consumed by the screens themselves (their top bars) and by the navigation
        // suite, which takes the bottom (bar) or the start (rail, drawer). Beside a rail, the end
        // and the bottom are left: the system navigation sits there when a phone lies sideways,
        // and a tablet's taskbar runs along the bottom.
        Box(
            modifier = if (navigationSuiteType.isVertical) {
                Modifier.windowInsetsPadding(
                    WindowInsets.safeDrawing.only(WindowInsetsSides.End + WindowInsetsSides.Bottom),
                )
            } else {
                Modifier
            },
        ) {
            content()
        }
    }
}

/**
 * A bottom bar while the window is phone-narrow (or folded like a laptop, where the bottom half
 * is the one within reach); a rail otherwise, including a phone in landscape, whose height is too
 * precious for a bar; a drawer only once the window is wide enough to spare it.
 */
internal fun navigationSuiteType(windowAdaptiveInfo: WindowAdaptiveInfo): NavigationSuiteType = when {
    windowAdaptiveInfo.isWidthCompact || windowAdaptiveInfo.windowPosture.isTabletop ->
        NavigationSuiteType.NavigationBar

    windowAdaptiveInfo.windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXTRA_LARGE_LOWER_BOUND) &&
        !windowAdaptiveInfo.isHeightCompact -> NavigationSuiteType.NavigationDrawer

    else -> NavigationSuiteType.NavigationRail
}

private val NavigationSuiteType.isVertical: Boolean
    get() = this == NavigationSuiteType.NavigationRail || this == NavigationSuiteType.NavigationDrawer

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
