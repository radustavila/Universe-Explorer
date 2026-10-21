package com.radustavila.nasaexplorer.feature.mars.rail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.radustavila.nasaexplorer.core.designsystem.component.SectionTitle
import com.radustavila.nasaexplorer.feature.mars.MarsPhotoCard
import com.radustavila.nasaexplorer.feature.mars.R

const val MARS_RAIL_TEST_TAG = "mars-rail"

/**
 * The newest rover photos as a rail, for the Universe tab. Draws its own 16 dp side margins, like
 * the gallery's rails, so its row scrolls edge to edge.
 */
@Composable
fun MarsRail(
    onPhotoClick: (id: String) -> Unit,
    onSeeAllClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MarsRailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    MarsRailContent(
        uiState = uiState,
        onPhotoClick = onPhotoClick,
        onSeeAllClick = onSeeAllClick,
        modifier = modifier,
    )
}

@Composable
internal fun MarsRailContent(
    uiState: MarsRailUiState,
    onPhotoClick: (id: String) -> Unit,
    onSeeAllClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.testTag(MARS_RAIL_TEST_TAG)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                SectionTitle(text = stringResource(R.string.mars_rail_title), style = MaterialTheme.typography.titleLarge)
                Text(
                    text = stringResource(R.string.mars_rail_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onSeeAllClick) { Text(stringResource(R.string.mars_rail_see_all)) }
        }

        if (uiState.photos.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(items = uiState.photos, key = { it.id }) { photo ->
                    MarsPhotoCard(
                        photo = photo,
                        onClick = { onPhotoClick(photo.id) },
                        modifier = Modifier.width(RAIL_CARD_WIDTH),
                    )
                }
            }
        } else if (uiState.isLoaded) {
            EmptyRailMessage(uiState)
        }
    }
}

@Composable
private fun EmptyRailMessage(uiState: MarsRailUiState) {
    Row(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (uiState.isSyncing) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        Text(
            text = stringResource(
                when {
                    uiState.isSyncing -> R.string.mars_rail_syncing
                    uiState.lastAttemptFailed -> R.string.mars_rail_failed
                    else -> R.string.mars_rail_waiting
                },
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private val RAIL_CARD_WIDTH = 156.dp
