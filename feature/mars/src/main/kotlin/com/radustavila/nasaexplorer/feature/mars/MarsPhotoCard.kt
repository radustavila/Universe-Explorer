package com.radustavila.nasaexplorer.feature.mars

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.radustavila.nasaexplorer.core.designsystem.component.NasaAsyncImage
import com.radustavila.nasaexplorer.core.model.MarsPhoto

fun marsPhotoTestTag(id: String): String = "mars-photo-$id"

/** A rover photo in the rail: the picture, then which rover and sol, then the camera. */
@Composable
internal fun MarsPhotoCard(
    photo: MarsPhoto,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier.testTag(marsPhotoTestTag(photo.id)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        NasaAsyncImage(
            url = photo.previewUrl,
            // The caption below names the photo for screen readers.
            contentDescription = null,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
        )
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(
                text = stringResource(R.string.mars_photo_rover_sol, photo.rover.displayName, photo.sol),
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = photo.cameraName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** A rover photo in a sol's camera row: the picture alone, named for screen readers. */
@Composable
internal fun MarsPhotoThumbnail(
    photo: MarsPhoto,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier.testTag(marsPhotoTestTag(photo.id)),
        shape = RoundedCornerShape(12.dp),
    ) {
        NasaAsyncImage(
            url = photo.previewUrl,
            contentDescription = photo.title,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
        )
    }
}
