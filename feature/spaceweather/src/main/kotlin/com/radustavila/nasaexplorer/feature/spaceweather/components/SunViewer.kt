package com.radustavila.nasaexplorer.feature.spaceweather.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.radustavila.nasaexplorer.core.designsystem.component.FreshnessLabel
import com.radustavila.nasaexplorer.core.designsystem.component.NasaAsyncImage
import com.radustavila.nasaexplorer.core.designsystem.component.NasaMediaPlayer
import com.radustavila.nasaexplorer.core.designsystem.component.ZoomableImage
import com.radustavila.nasaexplorer.core.model.SunChannel
import com.radustavila.nasaexplorer.feature.spaceweather.R
import com.radustavila.nasaexplorer.feature.spaceweather.SunUiState
import com.radustavila.nasaexplorer.feature.spaceweather.SunVideo

const val SUN_VIEWER_TEST_TAG = "sun-viewer"
const val SUN_VIDEO_ASK_TEST_TAG = "sun-video-ask"

/**
 * The Sun full screen: SDO's newest picture at full resolution with pinch-to-zoom, or the last 48
 * hours as a looping video, with the same channel chips as the card.
 *
 * The video is composed, and so downloaded, only in [SunVideo.Playing]: on mobile data the user
 * taps play first ([SunVideo.AskFirst]).
 */
@Composable
internal fun SunViewerDialog(
    sun: SunUiState,
    isOffline: Boolean,
    onChannelSelected: (SunChannel) -> Unit,
    onVideoSelected: () -> Unit,
    onVideoConfirmed: () -> Unit,
    onVideoClosed: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = {
            onVideoClosed()
            onDismiss()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        SunViewerContent(
            sun = sun,
            isOffline = isOffline,
            onChannelSelected = onChannelSelected,
            onVideoSelected = onVideoSelected,
            onVideoConfirmed = onVideoConfirmed,
            onVideoClosed = onVideoClosed,
            onClose = {
                onVideoClosed()
                onDismiss()
            },
        )
    }
}

@Composable
internal fun SunViewerContent(
    sun: SunUiState,
    isOffline: Boolean,
    onChannelSelected: (SunChannel) -> Unit,
    onVideoSelected: () -> Unit,
    onVideoConfirmed: () -> Unit,
    onVideoClosed: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val showingVideo = sun.video != SunVideo.Closed
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .safeDrawingPadding()
            .testTag(SUN_VIEWER_TEST_TAG),
    ) {
        ViewerTopBar(onClose = onClose)
        Box(
            modifier = Modifier.weight(1f).fillMaxWidth().clipToBounds(),
            contentAlignment = Alignment.Center,
        ) {
            if (showingVideo) {
                SunVideoStage(sun = sun, isOffline = isOffline, onVideoConfirmed = onVideoConfirmed)
            } else {
                sun.image?.let { image ->
                    ZoomableImage(
                        url = image.fullSizeUrl,
                        previewUrl = image.localUrl,
                        contentDescription = sun.channel.imageDescription(),
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
        Column(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .widthIn(max = CONTROLS_MAX_WIDTH)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ModeSelector(
                showingVideo = showingVideo,
                onPicture = onVideoClosed,
                onVideo = onVideoSelected,
            )
            SunChannelChips(selected = sun.channel, onSelected = onChannelSelected)
            Text(
                text = stringResource(sun.channel.descriptionRes),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
            )
            if (!showingVideo) FreshnessLabel(sun.freshness(isOffline))
            SunCredit(stringResource(R.string.sun_credit), color = Color.White.copy(alpha = CREDIT_ALPHA))
        }
    }
}

@Composable
private fun ViewerTopBar(onClose: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.sun_now_title),
            style = MaterialTheme.typography.titleLarge,
            color = Color.White,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onClose) {
            Icon(
                imageVector = Icons.Outlined.Close,
                contentDescription = stringResource(R.string.sun_viewer_close),
                tint = Color.White,
            )
        }
    }
}

@Composable
private fun SunVideoStage(sun: SunUiState, isOffline: Boolean, onVideoConfirmed: () -> Unit) {
    val videoUrl = sun.videoUrl
    when {
        sun.video == SunVideo.Playing && videoUrl != null -> NasaMediaPlayer(
            streamUrl = videoUrl,
            posterUrl = sun.image?.localUrl,
            autoPlay = true,
            loop = true,
            modifier = Modifier.fillMaxWidth().aspectRatio(1f),
        )

        sun.video == SunVideo.Checking -> CircularProgressIndicator()

        else -> Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            sun.image?.let { image ->
                NasaAsyncImage(
                    url = image.localUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    backgroundColor = Color.Black,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .padding(24.dp)
                    .background(Color.Black.copy(alpha = SCRIM_ALPHA), MaterialTheme.shapes.medium)
                    .padding(16.dp)
                    .testTag(SUN_VIDEO_ASK_TEST_TAG),
            ) {
                Text(
                    text = stringResource(if (isOffline) R.string.sun_video_offline else R.string.sun_video_metered),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                )
                if (!isOffline) {
                    Button(onClick = onVideoConfirmed) { Text(stringResource(R.string.sun_video_play)) }
                }
            }
        }
    }
}

@Composable
private fun ModeSelector(showingVideo: Boolean, onPicture: () -> Unit, onVideo: () -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        SegmentedButton(
            selected = !showingVideo,
            onClick = { if (showingVideo) onPicture() },
            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
            label = { Text(stringResource(R.string.sun_mode_picture)) },
        )
        SegmentedButton(
            selected = showingVideo,
            onClick = { if (!showingVideo) onVideo() },
            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
            label = { Text(stringResource(R.string.sun_mode_video)) },
        )
    }
}

private val CONTROLS_MAX_WIDTH = 640.dp
private const val SCRIM_ALPHA = 0.7f
private const val CREDIT_ALPHA = 0.7f
