package com.radustavila.nasaexplorer.feature.dsn

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.radustavila.nasaexplorer.core.designsystem.adaptive.centeringPadding
import com.radustavila.nasaexplorer.core.designsystem.component.SectionTitle
import com.radustavila.nasaexplorer.core.model.DsnDish
import com.radustavila.nasaexplorer.core.model.DsnSite

const val DSN_LIST_TEST_TAG = "dsn-list"

fun dsnSpacecraftTestTag(dishName: String, code: String) = "dsn-spacecraft-$dishName-$code"

@Composable
fun DsnScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DsnViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    PollWhileStarted(viewModel::pollWhileVisible)
    DsnContent(
        uiState = uiState,
        onBackClick = onBackClick,
        onSpacecraftClick = viewModel::onSpacecraftClick,
        onSheetDismiss = viewModel::onSheetDismiss,
        modifier = modifier,
    )
}

/**
 * Runs [poll] while the screen is at least started, and cancels it as soon as it is not: when the
 * app goes to the background, the screen turns off, or another destination covers this one.
 */
@Composable
internal fun PollWhileStarted(poll: suspend () -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentPoll by rememberUpdatedState(poll)
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) { currentPoll() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DsnContent(
    uiState: DsnUiState,
    onBackClick: () -> Unit,
    onSpacecraftClick: (dishName: String, code: String) -> Unit,
    onSheetDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        // See FeedScreen: insets belong to the top bar and to the app scaffold's navigation bar.
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.dsn_title)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.dsn_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            val snapshot = uiState.snapshot
            if (uiState.isLoaded && snapshot == null && uiState.farthest.isEmpty()) {
                NoSnapshot(uiState)
            } else if (uiState.isLoaded) {
                DsnList(uiState = uiState, onSpacecraftClick = onSpacecraftClick)
            }
        }
    }
    uiState.selected?.let { SpacecraftSheet(siteLink = it, onDismiss = onSheetDismiss) }
}

@Composable
private fun DsnList(
    uiState: DsnUiState,
    onSpacecraftClick: (dishName: String, code: String) -> Unit,
) {
    BoxWithConstraints {
        val sidePadding = centeringPadding(containerWidth = maxWidth) + 16.dp
        LazyColumn(
            modifier = Modifier.fillMaxSize().testTag(DSN_LIST_TEST_TAG),
            contentPadding = PaddingValues(start = sidePadding, end = sidePadding, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val snapshot = uiState.snapshot
            if (snapshot == null) {
                item(key = "status") { NoSnapshot(uiState) }
            } else {
                item(key = "status") { LiveStatus(snapshot, uiState) }
                items(snapshot.sites, key = { "site-${it.code}" }) { site ->
                    SiteCard(site = site, onSpacecraftClick = onSpacecraftClick)
                }
            }
            if (uiState.farthest.isNotEmpty()) {
                item(key = "farthest-title") { FarthestHeader() }
                items(uiState.farthest, key = { "probe-${it.horizonsId}" }) { probe ->
                    ProbeCard(
                        probe = probe,
                        isTalking = probe.horizonsId in uiState.talkingNaifIds,
                        at = snapshot?.observedAt,
                    )
                }
            }
        }
    }
}

@Composable
private fun SiteCard(site: DsnSite, onSpacecraftClick: (dishName: String, code: String) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(vertical = 12.dp)) {
            SectionTitle(site.name, Modifier.padding(horizontal = 16.dp))
            Spacer(Modifier.height(4.dp))
            // Busy antennas first, idle ones after, each group in the feed's order.
            site.dishes.sortedBy { !it.isActive }.forEachIndexed { index, dish ->
                if (index > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                DishRow(dish = dish, onSpacecraftClick = onSpacecraftClick)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DishRow(dish: DsnDish, onSpacecraftClick: (dishName: String, code: String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DishDrawing(
            elevationDegrees = dish.elevationDegrees,
            azimuthDegrees = dish.azimuthDegrees,
            isActive = dish.isActive,
            isLarge = (dish.diameterMeters ?: 0) >= LARGE_DISH_METERS,
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = dishTitle(dish), style = MaterialTheme.typography.titleSmall)
            if (dish.links.isEmpty()) {
                Text(
                    text = listOfNotNull(stringResource(R.string.dsn_dish_idle), dish.activity).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    dish.links.forEach { link ->
                        val label = if (link.isActive) {
                            link.spacecraft.displayName
                        } else {
                            "${link.spacecraft.displayName} · ${stringResource(R.string.dsn_link_waiting)}"
                        }
                        val description = stringResource(R.string.dsn_spacecraft_open, link.spacecraft.displayName)
                        AssistChip(
                            onClick = { onSpacecraftClick(dish.name, link.spacecraft.code) },
                            label = { Text(label) },
                            modifier = Modifier
                                .testTag(dsnSpacecraftTestTag(dish.name, link.spacecraft.code))
                                .semantics { contentDescription = description },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun dishTitle(dish: DsnDish): String {
    val parts = mutableListOf(dishLabel(dish.name))
    dish.diameterMeters?.let { parts += stringResource(R.string.dsn_dish_size, it) }
    val azimuth = dish.azimuthDegrees
    val elevation = dish.elevationDegrees
    if (dish.isActive && azimuth != null && elevation != null) {
        parts += stringResource(R.string.dsn_dish_pointing, azimuth.toInt(), elevation.toInt())
    }
    return parts.joinToString(" · ")
}

private const val LARGE_DISH_METERS = 70

