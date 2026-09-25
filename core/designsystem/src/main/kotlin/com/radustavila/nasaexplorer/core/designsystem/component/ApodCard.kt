package com.radustavila.nasaexplorer.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.radustavila.nasaexplorer.core.designsystem.util.formatMedium
import com.radustavila.nasaexplorer.core.model.Apod

/** Feed and favourites row: media on top, title and favourite toggle below. */
@Composable
fun ApodCard(
    apod: Apod,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = CardShape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Box {
            val mediaModifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)

            val previewImageUrl = apod.previewImageUrl

            when {
                previewImageUrl != null -> NasaAsyncImage(
                    url = previewImageUrl,
                    contentDescription = apod.title,
                    modifier = mediaModifier,
                )

                apod.isPlayable -> VideoPlaceholder(modifier = mediaModifier)

                else -> NasaAsyncImage(
                    url = null,
                    contentDescription = apod.title,
                    modifier = mediaModifier,
                )
            }

            // A still frame on its own reads as a photograph, so a video that does have one still
            // needs the play affordance over it.
            if (apod.isPlayable && previewImageUrl != null) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .background(PLAY_SCRIM_COLOR, CircleShape)
                        .padding(10.dp)
                        .size(28.dp),
                )
            }

            MediaTypeBadge(
                mediaType = apod.mediaType,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp),
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 12.dp, end = 4.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = apod.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = apod.date.formatMedium(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            FavoriteButton(isFavorite = apod.isFavorite, onToggle = onToggleFavorite)
        }
    }
}

private val CardShape = RoundedCornerShape(20.dp)
private val PLAY_SCRIM_COLOR = Color.Black.copy(alpha = 0.45f)
