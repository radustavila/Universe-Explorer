package com.radustavila.nasaexplorer.feature.feed

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.radustavila.nasaexplorer.core.designsystem.component.NasaAsyncImage
import com.radustavila.nasaexplorer.core.designsystem.component.VideoPlaceholder
import com.radustavila.nasaexplorer.core.model.Apod
import java.time.LocalDate

const val FEED_ON_THIS_DAY_TEST_TAG = "feed-on-this-day"

/** Today's month and day in earlier years of the archive, newest first, as a horizontal rail. */
@Composable
internal fun OnThisDayRail(
    entries: List<Apod>,
    onApodClick: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.testTag(FEED_ON_THIS_DAY_TEST_TAG),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.feed_on_this_day),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() },
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(items = entries, key = { apod -> apod.date.toString() }) { apod ->
                OnThisDayCard(apod = apod, onClick = { onApodClick(apod.date) })
            }
        }
    }
}

@Composable
private fun OnThisDayCard(apod: Apod, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.width(CARD_WIDTH),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        val mediaModifier = Modifier
            .fillMaxWidth()
            .aspectRatio(4f / 3f)
        val previewImageUrl = apod.previewImageUrl
        if (previewImageUrl == null && apod.isPlayable) {
            VideoPlaceholder(modifier = mediaModifier)
        } else {
            NasaAsyncImage(
                url = previewImageUrl,
                contentDescription = apod.imageDescription,
                modifier = mediaModifier,
            )
        }

        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(
                text = apod.date.year.toString(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = apod.title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private val CARD_WIDTH = 168.dp
