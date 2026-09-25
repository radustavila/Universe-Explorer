package com.radustavila.nasaexplorer.feature.details

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.radustavila.nasaexplorer.core.designsystem.component.NasaAsyncImage

const val VIDEO_PLAYER_TEST_TAG = "video-player"
const val VIDEO_PLAYER_LOADING_TEST_TAG = "video-player-loading"
const val VIDEO_PLAYER_ERROR_TEST_TAG = "video-player-error"
const val VIDEO_PLAYER_FULLSCREEN_TEST_TAG = "video-player-fullscreen"

/**
 * In-app playback for APOD entries whose media is a real stream (see [com.radustavila.nasaexplorer.core.model.Apod.streamUrl]).
 *
 * The player is owned by the composition: it is created for a given [streamUrl] and released when
 * that composition leaves. Playback position and intent survive configuration changes through
 * [rememberSaveable], and the player is paused whenever the screen stops being interactive so it
 * never keeps decoding in the background.
 *
 * The controller's fullscreen button lifts the same player into a full-screen dialog, so expanding
 * neither restarts the stream nor loses the position.
 *
 * [posterUrl] is a still frame of the same media, shown while the stream buffers.
 */
@OptIn(UnstableApi::class)
@Composable
internal fun ApodVideoPlayer(
    streamUrl: String,
    posterUrl: String?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var status by remember(streamUrl) { mutableStateOf(PlaybackStatus.Buffering) }
    var resumePositionMillis by rememberSaveable(streamUrl) { mutableLongStateOf(0L) }
    var resumePlaying by rememberSaveable(streamUrl) { mutableStateOf(false) }
    var isFullscreen by rememberSaveable(streamUrl) { mutableStateOf(false) }
    // APOD publishes both cinema-wide footage and phone-shot verticals; which one this is decides
    // whether full screen should turn the device.
    var videoAspectRatio by rememberSaveable(streamUrl) { mutableFloatStateOf(0f) }

    val player = remember(streamUrl) {
        ExoPlayer.Builder(context)
            // APOD's own videos are progressive files on a slow origin with no adaptive variants,
            // so the only lever against mid-playback stalls is buffering more before starting.
            .setLoadControl(
                DefaultLoadControl.Builder()
                    .setBufferDurationsMs(
                        MIN_BUFFER_MILLIS,
                        MAX_BUFFER_MILLIS,
                        BUFFER_FOR_PLAYBACK_MILLIS,
                        BUFFER_AFTER_REBUFFER_MILLIS,
                    )
                    // Keep filling the time buffer even when it exceeds the default size budget:
                    // a few seconds of a high bitrate file is worth the memory here.
                    .setPrioritizeTimeOverSizeThresholds(true)
                    .build(),
            )
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(
                    DefaultDataSource.Factory(
                        context,
                        DefaultHttpDataSource.Factory()
                            .setConnectTimeoutMs(HTTP_TIMEOUT_MILLIS)
                            .setReadTimeoutMs(HTTP_TIMEOUT_MILLIS)
                            // apod.nasa.gov still serves some media over plain http.
                            .setAllowCrossProtocolRedirects(true),
                    ),
                ),
            )
            .build()
            .apply {
                setMediaItem(MediaItem.fromUri(streamUrl))
                seekTo(resumePositionMillis)
                playWhenReady = resumePlaying
                prepare()
            }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                status = when (playbackState) {
                    Player.STATE_BUFFERING -> PlaybackStatus.Buffering
                    Player.STATE_READY, Player.STATE_ENDED -> PlaybackStatus.Ready
                    // Idle is also where a failed player lands; the error callback owns that case.
                    else -> status
                }
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.height == 0) return
                videoAspectRatio =
                    videoSize.width * videoSize.pixelWidthHeightRatio / videoSize.height
            }

            override fun onPlayerError(error: PlaybackException) {
                status = PlaybackStatus.Failed
                // An unplayable stream has nothing to show at full screen.
                isFullscreen = false
            }
        }

        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    DisposableEffect(lifecycleOwner, player) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                // Captured before pausing, so a configuration change resumes where the user was.
                resumePositionMillis = player.currentPosition
                resumePlaying = player.playWhenReady
                player.pause()
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Only one of the two stages is composed at a time: a player renders into a single surface, so
    // the inline slot gives its view up while the dialog holds one.
    if (isFullscreen) {
        FullscreenPlayerDialog(
            // Only a wide video gains anything from turning the device; a vertical one would just
            // end up letterboxed the other way round.
            isLandscapeVideo = videoAspectRatio > 1f,
            onDismiss = { isFullscreen = false },
        ) {
            PlayerStage(
                player = player,
                status = status,
                posterUrl = posterUrl,
                isFullscreen = true,
                onFullscreenClick = { isFullscreen = false },
                onRetry = {
                    status = PlaybackStatus.Buffering
                    player.prepare()
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }

    Box(
        modifier = modifier
            .background(Color.Black)
            .testTag(VIDEO_PLAYER_TEST_TAG),
        contentAlignment = Alignment.Center,
    ) {
        if (isFullscreen) {
            // The inline slot still occupies its place in the scrolling layout; the poster keeps it
            // from being a black hole while the media plays in the dialog.
            Poster(posterUrl)
        } else {
            PlayerStage(
                player = player,
                status = status,
                posterUrl = posterUrl,
                isFullscreen = false,
                onFullscreenClick = { isFullscreen = true },
                onRetry = {
                    status = PlaybackStatus.Buffering
                    player.prepare()
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** The player surface plus the overlays that stand in for it while it has nothing to show. */
@OptIn(UnstableApi::class)
@Composable
private fun PlayerStage(
    player: ExoPlayer,
    status: PlaybackStatus,
    posterUrl: String?,
    isFullscreen: Boolean,
    onFullscreenClick: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // The view is built once, but the callback it holds has to stay the current one.
    val currentOnFullscreenClick by rememberUpdatedState(onFullscreenClick)

    Box(
        modifier = modifier.background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        if (status != PlaybackStatus.Failed) {
            AndroidView(
                factory = { viewContext ->
                    PlayerView(viewContext).apply {
                        this.player = player
                        useController = true
                        // Set the button's state before installing the listener: Media3 reports a
                        // state change to that listener, which would read as a tap and bounce the
                        // dialog straight back shut.
                        setFullscreenButtonState(isFullscreen)
                        // Installing the listener is also what makes the controller show the button.
                        setFullscreenButtonClickListener { currentOnFullscreenClick() }
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                    }
                },
                onRelease = { view -> view.player = null },
                modifier = Modifier.fillMaxSize(),
            )
        }

        when (status) {
            // Drawn over the player surface: until the first frame is decoded that surface is
            // black, and the poster is the same image the feed already has cached.
            PlaybackStatus.Buffering -> {
                Poster(posterUrl)
                CircularProgressIndicator(
                    modifier = Modifier.testTag(VIDEO_PLAYER_LOADING_TEST_TAG),
                )
            }

            PlaybackStatus.Failed -> PlaybackErrorState(onRetry = onRetry)

            PlaybackStatus.Ready -> Unit
        }
    }
}

/**
 * Full-screen stage for the player.
 *
 * APOD videos are wide, so a portrait phone can only show them as a letterboxed strip; going
 * full screen locks the activity to landscape and hides the system bars for as long as the dialog
 * is up. The activity itself handles the orientation change (see the manifest), so the player is
 * never torn down and rebuffered on the way in.
 */
@Composable
private fun FullscreenPlayerDialog(
    isLandscapeVideo: Boolean,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        if (isLandscapeVideo) LandscapeWhileVisible()
        HiddenSystemBarsWhileVisible()

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .testTag(VIDEO_PLAYER_FULLSCREEN_TEST_TAG),
            contentAlignment = Alignment.Center,
        ) {
            content()
        }
    }
}

@Composable
private fun LandscapeWhileVisible() {
    val activity = LocalContext.current.findActivity() ?: return

    DisposableEffect(activity) {
        val previousOrientation = activity.requestedOrientation
        // Sensor landscape rather than plain landscape, so the video still follows the device when
        // it is turned the other way up.
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        onDispose { activity.requestedOrientation = previousOrientation }
    }
}

@Composable
private fun HiddenSystemBarsWhileVisible() {
    val view = LocalView.current
    val window = (view.parent as? DialogWindowProvider)?.window ?: return

    DisposableEffect(window) {
        val controller = WindowInsetsControllerCompat(window, view)
        // A swipe brings the bars back temporarily; they are never needed to leave the dialog,
        // which the back gesture and the controller's own button both do.
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
        onDispose { controller.show(WindowInsetsCompat.Type.systemBars()) }
    }
}

@Composable
private fun Poster(url: String?, modifier: Modifier = Modifier) {
    if (url == null) return

    NasaAsyncImage(
        url = url,
        contentDescription = null,
        backgroundColor = Color.Black,
        modifier = modifier.fillMaxSize(),
    )
}

@Composable
private fun PlaybackErrorState(onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .padding(16.dp)
            .testTag(VIDEO_PLAYER_ERROR_TEST_TAG),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = stringResource(R.string.details_video_error),
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White,
            textAlign = TextAlign.Center,
        )
        TextButton(onClick = onRetry) {
            Text(text = stringResource(R.string.details_video_retry))
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * Playback starts only once this much media is buffered. The Media3 default is 2.5s, which is why a
 * slowly served progressive file starts playing and then immediately stalls; waiting longer up front
 * trades a little startup latency for playback that runs through.
 */
private const val BUFFER_FOR_PLAYBACK_MILLIS = 5_000

/** A stall already happened, so rebuild a larger cushion before resuming. */
private const val BUFFER_AFTER_REBUFFER_MILLIS = 10_000

private const val MIN_BUFFER_MILLIS = 30_000
private const val MAX_BUFFER_MILLIS = 120_000
private const val HTTP_TIMEOUT_MILLIS = 15_000

private enum class PlaybackStatus {
    Buffering,
    Ready,
    Failed,
}
