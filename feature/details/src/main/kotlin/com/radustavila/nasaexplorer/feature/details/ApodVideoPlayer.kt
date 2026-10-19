package com.radustavila.nasaexplorer.feature.details

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.radustavila.nasaexplorer.core.designsystem.component.MEDIA_PLAYER_ERROR_TEST_TAG
import com.radustavila.nasaexplorer.core.designsystem.component.MEDIA_PLAYER_FULLSCREEN_TEST_TAG
import com.radustavila.nasaexplorer.core.designsystem.component.MEDIA_PLAYER_LOADING_TEST_TAG
import com.radustavila.nasaexplorer.core.designsystem.component.MEDIA_PLAYER_TEST_TAG
import com.radustavila.nasaexplorer.core.designsystem.component.NasaMediaPlayer

const val VIDEO_PLAYER_TEST_TAG = MEDIA_PLAYER_TEST_TAG
const val VIDEO_PLAYER_LOADING_TEST_TAG = MEDIA_PLAYER_LOADING_TEST_TAG
const val VIDEO_PLAYER_ERROR_TEST_TAG = MEDIA_PLAYER_ERROR_TEST_TAG
const val VIDEO_PLAYER_FULLSCREEN_TEST_TAG = MEDIA_PLAYER_FULLSCREEN_TEST_TAG

/**
 * In-app playback for APOD entries whose media is a real stream (see
 * [com.radustavila.nasaexplorer.core.model.Apod.streamUrl]): the shared [NasaMediaPlayer], which
 * the NASA library's details screen plays its videos and recordings with too.
 */
@Composable
internal fun ApodVideoPlayer(
    streamUrl: String,
    posterUrl: String?,
    modifier: Modifier = Modifier,
) {
    NasaMediaPlayer(streamUrl = streamUrl, posterUrl = posterUrl, modifier = modifier)
}
