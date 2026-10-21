package com.radustavila.nasaexplorer.ui.hub

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.radustavila.nasaexplorer.R
import com.radustavila.nasaexplorer.core.designsystem.adaptive.PanelColumnMinWidth
import com.radustavila.nasaexplorer.core.designsystem.component.EmptyState

const val HUB_LIST_TEST_TAG = "hub-list"

/** One thing a tab offers, opening a feature's screen. */
data class HubEntry(
    val key: String,
    val icon: ImageVector,
    @param:StringRes val titleRes: Int,
    @param:StringRes val descriptionRes: Int,
    val onClick: () -> Unit,
)

/**
 * Start screen of a tab that no single feature owns: a list of what the tab offers, or a "coming
 * soon" note while nothing has been built for it yet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HubScreen(
    @StringRes titleRes: Int,
    entries: List<HubEntry>,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        // See FeedScreen: insets belong to the top bar and to the app scaffold's navigation bar.
        contentWindowInsets = WindowInsets(0),
        topBar = { TopAppBar(title = { Text(stringResource(titleRes)) }) },
    ) { padding ->
        if (entries.isEmpty()) {
            EmptyState(
                title = stringResource(R.string.hub_coming_soon_title),
                description = stringResource(R.string.hub_coming_soon_description),
                modifier = Modifier.padding(padding),
            )
            return@Scaffold
        }

        // A column of entries on a phone, side by side where the window is wider.
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = PanelColumnMinWidth),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .testTag(HUB_LIST_TEST_TAG),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(items = entries, key = HubEntry::key) { entry -> HubEntryCard(entry) }
        }
    }
}

/**
 * [entries] as a column of cards, for a tab whose first screen is a feature (the Universe gallery)
 * that still lists the tab's other features at its top. Where the width allows, the cards sit
 * side by side, each row as tall as its tallest card.
 */
@Composable
fun HubEntryCards(entries: List<HubEntry>, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier = modifier) {
        val columns = ((maxWidth + ENTRY_SPACING) / (PanelColumnMinWidth + ENTRY_SPACING)).toInt().coerceAtLeast(1)
        Column(verticalArrangement = Arrangement.spacedBy(ENTRY_SPACING)) {
            entries.chunked(columns).forEach { row ->
                Row(
                    modifier = Modifier.height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(ENTRY_SPACING),
                ) {
                    row.forEach { entry -> HubEntryCard(entry, Modifier.weight(1f).fillMaxHeight()) }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

private val ENTRY_SPACING = 12.dp

@Composable
private fun HubEntryCard(entry: HubEntry, modifier: Modifier = Modifier) {
    Card(onClick = entry.onClick, modifier = modifier) {
        ListItem(
            leadingContent = { Icon(entry.icon, contentDescription = null) },
            headlineContent = { Text(stringResource(entry.titleRes)) },
            supportingContent = { Text(stringResource(entry.descriptionRes)) },
            trailingContent = {
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null)
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        )
    }
}
