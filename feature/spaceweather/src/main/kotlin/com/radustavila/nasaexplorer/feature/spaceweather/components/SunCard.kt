package com.radustavila.nasaexplorer.feature.spaceweather.components

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.radustavila.nasaexplorer.core.designsystem.component.FreshnessLabel
import com.radustavila.nasaexplorer.core.designsystem.component.NasaAsyncImage
import com.radustavila.nasaexplorer.core.designsystem.component.SectionTitle
import com.radustavila.nasaexplorer.core.model.SunChannel
import com.radustavila.nasaexplorer.feature.spaceweather.R
import com.radustavila.nasaexplorer.feature.spaceweather.SunUiState

const val SUN_CARD_TEST_TAG = "sun-card"
const val SUN_IMAGE_TEST_TAG = "sun-image"

/**
 * "The Sun now": SDO's newest picture of the chosen channel at card width, the channel chips with a
 * line on what each shows, how old the picture is (offline too) and SDO's credit. Tapping the
 * picture opens [SunViewerDialog].
 */
@Composable
internal fun SunCard(
    sun: SunUiState,
    isOffline: Boolean,
    onChannelSelected: (SunChannel) -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth().testTag(SUN_CARD_TEST_TAG)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionTitle(stringResource(R.string.sun_now_title))
            SunPicture(sun = sun, isOffline = isOffline, onOpen = onOpen)
            SunChannelChips(selected = sun.channel, onSelected = onChannelSelected)
            Text(
                text = stringResource(sun.channel.descriptionRes),
                style = MaterialTheme.typography.bodyMedium,
            )
            FreshnessLabel(sun.freshness(isOffline))
            SunCredit(stringResource(R.string.sun_credit))
        }
    }
}

@Composable
private fun SunPicture(sun: SunUiState, isOffline: Boolean, onOpen: () -> Unit) {
    val image = sun.image
    val openLabel = stringResource(R.string.sun_open)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(MaterialTheme.shapes.medium)
            .background(Color.Black)
            .then(if (image != null) Modifier.clickable(onClickLabel = openLabel, onClick = onOpen) else Modifier)
            .testTag(SUN_IMAGE_TEST_TAG),
        contentAlignment = Alignment.Center,
    ) {
        when {
            image != null -> NasaAsyncImage(
                url = image.localUrl,
                contentDescription = sun.channel.imageDescription(),
                contentScale = ContentScale.Fit,
                backgroundColor = Color.Black,
                modifier = Modifier.fillMaxSize(),
            )

            sun.isRefreshing -> CircularProgressIndicator()

            else -> Text(
                text = stringResource(
                    when {
                        isOffline -> R.string.sun_no_image_offline
                        sun.lastRefreshFailed -> R.string.sun_no_image_failed
                        else -> R.string.sun_no_image
                    },
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(24.dp),
            )
        }
    }
}

/** One chip per channel, scrolling sideways when they do not fit; starts at the chosen one. */
@Composable
internal fun SunChannelChips(
    selected: SunChannel,
    onSelected: (SunChannel) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        state = rememberLazyListState(initialFirstVisibleItemIndex = selected.ordinal),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(SunChannel.entries, key = { it.name }) { channel ->
            FilterChip(
                selected = channel == selected,
                onClick = { onSelected(channel) },
                label = { Text(stringResource(channel.nameRes)) },
            )
        }
    }
}

@Composable
internal fun SunCredit(text: String, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(text = text, style = MaterialTheme.typography.labelSmall, color = color)
}

@Composable
internal fun SunChannel.imageDescription(): String =
    stringResource(R.string.sun_image_description, stringResource(nameRes))

@get:StringRes
internal val SunChannel.nameRes: Int
    get() = when (this) {
        SunChannel.VISIBLE_LIGHT -> R.string.sun_channel_visible
        SunChannel.MAGNETIC_FIELD -> R.string.sun_channel_magnetic
        SunChannel.CHROMOSPHERE -> R.string.sun_channel_chromosphere
        SunChannel.CORONA -> R.string.sun_channel_corona
        SunChannel.HOT_CORONA -> R.string.sun_channel_hot_corona
        SunChannel.FLARES -> R.string.sun_channel_flares
    }

@get:StringRes
internal val SunChannel.descriptionRes: Int
    get() = when (this) {
        SunChannel.VISIBLE_LIGHT -> R.string.sun_channel_visible_description
        SunChannel.MAGNETIC_FIELD -> R.string.sun_channel_magnetic_description
        SunChannel.CHROMOSPHERE -> R.string.sun_channel_chromosphere_description
        SunChannel.CORONA -> R.string.sun_channel_corona_description
        SunChannel.HOT_CORONA -> R.string.sun_channel_hot_corona_description
        SunChannel.FLARES -> R.string.sun_channel_flares_description
    }
