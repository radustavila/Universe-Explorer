package com.radustavila.nasaexplorer.feature.universe.gallery

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.radustavila.nasaexplorer.core.designsystem.component.NasaAsyncImage
import com.radustavila.nasaexplorer.core.model.NasaMedia
import com.radustavila.nasaexplorer.core.model.NasaMediaType
import com.radustavila.nasaexplorer.feature.universe.R

/** A library item in a rail or the results grid: its thumbnail, a media badge and its title. */
@Composable
internal fun MediaCard(
    media: NasaMedia,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Box {
            NasaAsyncImage(
                url = media.thumbnailUrl ?: media.imageUrl,
                // The title below already names the item for screen readers.
                contentDescription = null,
                fallbackIcon = media.mediaType.icon ?: Icons.Outlined.Image,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(CARD_IMAGE_ASPECT_RATIO),
            )
            media.mediaType.badge?.let { (icon, label) ->
                MediaBadge(
                    icon = icon,
                    label = stringResource(label),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp),
                )
            }
        }
        Text(
            text = media.title,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 2,
            minLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        )
    }
}

@Composable
private fun MediaBadge(icon: ImageVector, label: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = Color.Black.copy(alpha = 0.6f),
        contentColor = Color.White,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(14.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
    }
}

private val NasaMediaType.icon: ImageVector?
    get() = when (this) {
        NasaMediaType.IMAGE -> null
        NasaMediaType.VIDEO -> Icons.Outlined.PlayCircle
        NasaMediaType.AUDIO -> Icons.Outlined.GraphicEq
    }

private val NasaMediaType.badge: Pair<ImageVector, Int>?
    get() = when (this) {
        NasaMediaType.IMAGE -> null
        NasaMediaType.VIDEO -> Icons.Outlined.PlayCircle to R.string.media_type_label_video
        NasaMediaType.AUDIO -> Icons.Outlined.GraphicEq to R.string.media_type_label_audio
    }

private const val CARD_IMAGE_ASPECT_RATIO = 4f / 3f
