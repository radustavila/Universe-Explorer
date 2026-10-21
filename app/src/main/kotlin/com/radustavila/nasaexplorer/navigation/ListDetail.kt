package com.radustavila.nasaexplorer.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.layout.PaneAdaptedValue
import androidx.compose.material3.adaptive.layout.ThreePaneScaffoldValue
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import androidx.compose.material3.adaptive.navigation.BackNavigationBehavior
import androidx.compose.material3.adaptive.navigation.NavigableListDetailPaneScaffold
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavOptions
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navOptions
import com.radustavila.nasaexplorer.R
import com.radustavila.nasaexplorer.core.designsystem.adaptive.ListDetailHost
import com.radustavila.nasaexplorer.core.designsystem.adaptive.LocalWindowAdaptiveInfo
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

const val LIST_PANE_TEST_TAG = "list-pane"
const val DETAIL_PANE_TEST_TAG = "detail-pane"
const val DETAIL_PANE_PLACEHOLDER_TEST_TAG = "detail-pane-placeholder"

/**
 * A [ListDetailHost] that shows the picked item beside the list once the window has room for two
 * panes, and opens it full screen (as on a phone) otherwise.
 *
 * The detail pane holds a navigation host of its own, so a feature's detail screen runs there
 * exactly as it does full screen: same type-safe route, same ViewModel reading it from its
 * `SavedStateHandle`. [detailPanes] registers those screens; they must not declare deep links,
 * which belong to the app's own nav host (a pane's host would act on the launch intent again).
 *
 * @param openInPane navigates the pane's host to an item, with the given options.
 * @param opensInPane whether an item can be shown in the pane at all; the others always open full
 * screen.
 * @param detailPanes the detail screens, given the pane's host (for links to further items, which
 * stack inside the pane) and the action for their back arrow.
 */
internal fun <T> listDetail(
    openInPane: NavController.(item: T, navOptions: NavOptions) -> Unit,
    opensInPane: (T) -> Boolean = { true },
    detailPanes: NavGraphBuilder.(pane: NavHostController, onBackClick: () -> Unit) -> Unit,
): ListDetailHost<T> = { openFullScreen, list ->
    ListDetailLayout(
        openFullScreen = openFullScreen,
        openInPane = openInPane,
        opensInPane = opensInPane,
        detailPanes = detailPanes,
        list = list,
    )
}

/** What the detail pane shows until an item is picked. */
@Serializable
internal data object DetailPlaceholderRoute

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
internal fun <T> ListDetailLayout(
    openFullScreen: (T) -> Unit,
    openInPane: NavController.(item: T, navOptions: NavOptions) -> Unit,
    opensInPane: (T) -> Boolean,
    detailPanes: NavGraphBuilder.(pane: NavHostController, onBackClick: () -> Unit) -> Unit,
    list: @Composable (onItemClick: (T) -> Unit) -> Unit,
    modifier: Modifier = Modifier,
) {
    val navigator = rememberListDetailPaneScaffoldNavigator(
        scaffoldDirective = calculatePaneScaffoldDirective(LocalWindowAdaptiveInfo.current),
    )
    // Hoisted above the panes, so the picked item survives the detail pane being hidden (the
    // window narrowing to one pane and back) and is still there when it shows again.
    val pane = rememberNavController()
    val scope = rememberCoroutineScope()

    // The picked item replaces the previous one rather than stacking on it: the pane's back stack
    // holds the item and the items it links to, never the history of picks.
    // (Built on use: the pane's graph exists only once its host has been composed.)
    val replacePick = { navOptions { popUpTo(pane.graph.id) { inclusive = true } } }
    val onItemClick: (T) -> Unit = { item ->
        if (navigator.scaffoldValue.isTwoPane && opensInPane(item)) {
            pane.openInPane(item, replacePick())
            // Also lets the pick lead the way when the window narrows to one pane.
            if (navigator.currentDestination?.pane != ListDetailPaneScaffoldRole.Detail) {
                scope.launch { navigator.navigateTo(ListDetailPaneScaffoldRole.Detail) }
            }
        } else {
            openFullScreen(item)
        }
    }
    val onDetailBackClick: () -> Unit = {
        when {
            pane.previousBackStackEntry != null -> pane.popBackStack()

            // One pane: back to the list; the pick stays for when the window widens again.
            !navigator.scaffoldValue.isTwoPane -> scope.launch { navigator.navigateBack() }

            // Two panes: nothing to go back to, so the arrow puts the item away.
            else -> {
                pane.navigate(DetailPlaceholderRoute, replacePick())
                scope.launch {
                    navigator.navigateBack(BackNavigationBehavior.PopUntilCurrentDestinationChange)
                }
            }
        }
    }

    NavigableListDetailPaneScaffold(
        navigator = navigator,
        modifier = modifier,
        listPane = {
            AnimatedPane(modifier = Modifier.testTag(LIST_PANE_TEST_TAG)) {
                list(onItemClick)
            }
        },
        detailPane = {
            AnimatedPane(modifier = Modifier.testTag(DETAIL_PANE_TEST_TAG)) {
                NavHost(navController = pane, startDestination = DetailPlaceholderRoute) {
                    composable<DetailPlaceholderRoute> { DetailPlaceholder() }
                    detailPanes(pane, onDetailBackClick)
                }
            }
        },
    )
}

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
private val ThreePaneScaffoldValue.isTwoPane: Boolean
    get() = this[ListDetailPaneScaffoldRole.List] == PaneAdaptedValue.Expanded &&
        this[ListDetailPaneScaffoldRole.Detail] == PaneAdaptedValue.Expanded

@Composable
private fun DetailPlaceholder(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp)
            .testTag(DETAIL_PANE_PLACEHOLDER_TEST_TAG),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Outlined.AutoAwesome,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.detail_pane_placeholder),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
