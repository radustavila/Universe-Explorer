package com.radustavila.nasaexplorer.feature.favorites

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.radustavila.nasaexplorer.core.designsystem.component.FavoriteButton
import com.radustavila.nasaexplorer.core.designsystem.component.NasaAsyncImage
import com.radustavila.nasaexplorer.core.designsystem.units.formatLocalDateTime
import com.radustavila.nasaexplorer.core.model.EpicCollection
import com.radustavila.nasaexplorer.core.model.EpicImage
import com.radustavila.nasaexplorer.core.model.EpicPhotoRef
import com.radustavila.nasaexplorer.core.model.FavoriteItem
import com.radustavila.nasaexplorer.core.model.FavoriteSource

/**
 * A saved item of any source, drawn from its snapshot only, so it renders offline whether or not
 * the source still has the item cached.
 */
@Composable
internal fun FavoriteCard(
    item: FavoriteItem,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = item.displayTitle()
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        NasaAsyncImage(
            url = item.imageUrl,
            contentDescription = title,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 12.dp, end = 4.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(item.source.labelRes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FavoriteButton(isFavorite = true, onToggle = onRemove)
        }
    }
}

/**
 * The snapshot's title, worded here when the source stores raw data: an EPIC favourite keeps its
 * capture time, shown in the user's language, time zone and clock format.
 */
@Composable
internal fun FavoriteItem.displayTitle(): String {
    if (source != FavoriteSource.EPIC) return title
    val capturedAt = EpicImage.capturedAtOfFavoriteTitle(title) ?: return title
    val enhanced = EpicPhotoRef.fromFavoriteId(itemId)?.collection == EpicCollection.ENHANCED
    return stringResource(
        if (enhanced) R.string.favorites_epic_title_enhanced else R.string.favorites_epic_title,
        capturedAt.formatLocalDateTime(),
    )
}

@get:StringRes
private val FavoriteSource.labelRes: Int
    get() = when (this) {
        FavoriteSource.APOD -> R.string.favorites_source_apod
        FavoriteSource.EPIC -> R.string.favorites_source_epic
        FavoriteSource.NASA_LIBRARY -> R.string.favorites_source_nasa_library
        FavoriteSource.MARS -> R.string.favorites_source_mars
        FavoriteSource.EXOPLANET -> R.string.favorites_source_exoplanet
    }
