package com.radustavila.nasaexplorer.feature.earth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.radustavila.nasaexplorer.core.designsystem.component.NasaAsyncImage
import com.radustavila.nasaexplorer.core.designsystem.theme.NasaTheme
import com.radustavila.nasaexplorer.core.designsystem.util.formatMedium
import com.radustavila.nasaexplorer.core.model.EpicCollection
import com.radustavila.nasaexplorer.core.model.EpicImage
import com.radustavila.nasaexplorer.core.model.GeoPoint
import java.time.Instant

const val EARTH_TODAY_CARD_TEST_TAG = "earth-today-card"

/**
 * Small Earth card for the Today screen. Takes care of its own data, so a host only places it and
 * says where a tap goes. Renders nothing until there is something to show or a sync in flight, so
 * a host never displays an empty frame.
 */
@Composable
fun EarthTodayCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: EarthTodayCardViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    EarthTodayCardContent(uiState = uiState, onClick = onClick, modifier = modifier)
}

@Composable
internal fun EarthTodayCardContent(
    uiState: EarthTodayCardUiState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cover = uiState.coverImage
    if (cover == null && !uiState.isLoading) return

    Card(
        onClick = onClick,
        enabled = cover != null,
        modifier = modifier
            .fillMaxWidth()
            .testTag(EARTH_TODAY_CARD_TEST_TAG),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(DISC_SIZE)
                    .clip(CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (cover == null) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                } else {
                    NasaAsyncImage(
                        url = cover.imageUrl,
                        previewUrl = cover.thumbnailUrl,
                        contentDescription = null,
                        backgroundColor = Color.Black,
                        modifier = Modifier.size(DISC_SIZE),
                    )
                }
            }

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.earth_today_card_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = if (cover == null) {
                        stringResource(R.string.earth_today_card_loading)
                    } else {
                        pluralStringResource(
                            R.plurals.earth_today_card_subtitle,
                            uiState.images.size,
                            cover.date.formatMedium(),
                            uiState.images.size,
                        )
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (cover != null) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private val DISC_SIZE = 72.dp

@Preview
@Composable
private fun EarthTodayCardPreview() {
    NasaTheme {
        EarthTodayCardContent(
            uiState = EarthTodayCardUiState(
                images = List(13) { index ->
                    EpicImage(
                        identifier = "$index",
                        collection = EpicCollection.NATURAL,
                        capturedAt = Instant.parse("2026-10-16T00:22:24Z"),
                        caption = "",
                        centroid = GeoPoint(latitude = 0.66, longitude = 163.7),
                        thumbnailUrl = "",
                        imageUrl = "",
                    )
                },
            ),
            onClick = {},
        )
    }
}
