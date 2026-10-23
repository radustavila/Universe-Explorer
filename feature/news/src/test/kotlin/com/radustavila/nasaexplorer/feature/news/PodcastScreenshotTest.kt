package com.radustavila.nasaexplorer.feature.news

import androidx.compose.ui.test.junit4.createComposeRule
import com.radustavila.nasaexplorer.core.model.DataFreshness
import com.radustavila.nasaexplorer.core.model.PodcastDownload
import com.radustavila.nasaexplorer.core.model.PodcastDownloadState
import com.radustavila.nasaexplorer.core.model.PodcastPlayback
import com.radustavila.nasaexplorer.core.model.PodcastPlaybackError
import com.radustavila.nasaexplorer.core.model.PodcastProgress
import com.radustavila.nasaexplorer.core.screenshottesting.ScreenshotDevices
import com.radustavila.nasaexplorer.core.screenshottesting.captureComponent
import com.radustavila.nasaexplorer.core.screenshottesting.captureScreen
import com.radustavila.nasaexplorer.core.testing.data.TestNews
import com.radustavila.nasaexplorer.core.testing.data.TestPodcasts
import com.radustavila.nasaexplorer.feature.news.list.NewsContent
import com.radustavila.nasaexplorer.feature.news.list.NewsTab
import com.radustavila.nasaexplorer.feature.news.list.NewsUiState
import com.radustavila.nasaexplorer.feature.news.podcast.PodcastsContent
import com.radustavila.nasaexplorer.feature.news.podcast.PodcastsUiState
import com.radustavila.nasaexplorer.feature.news.podcast.episode.PodcastEpisodeContent
import com.radustavila.nasaexplorer.feature.news.podcast.episode.PodcastEpisodeUiState
import com.radustavila.nasaexplorer.feature.news.podcast.player.MiniPlayerContent
import com.radustavila.nasaexplorer.feature.news.podcast.player.PlayerSheetContent
import java.time.Duration
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Golden images of the Podcasts tab and the player (F21); `./gradlew recordRoborazziDebug` rewrites them. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = ScreenshotDevices.COMPACT)
class PodcastScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val curious = TestPodcasts.curiousUniverse()
    private val houston = TestPodcasts.houston().copy(
        progress = PodcastProgress(Duration.ofMinutes(21)),
        download = PodcastDownload(PodcastDownloadState.DOWNLOADED, bytes = 57_000_000),
    )
    private val smallSteps = TestPodcasts.smallSteps()
        .copy(progress = PodcastProgress(Duration.ZERO, isFinished = true))
    private val playing = PodcastPlayback(
        episode = curious,
        isPlaying = true,
        position = Duration.ofSeconds(402),
        duration = curious.duration,
        speed = 1.2f,
    )

    @Test
    fun podcastsTab() {
        composeTestRule.captureScreen("PodcastsTab") {
            NewsContent(
                uiState = NewsUiState(isLoaded = true, now = TestNews.NOW),
                onBackClick = {},
                onItemClick = {},
                onKindSelected = {},
                onRefresh = {},
                onErrorShown = {},
                selectedTab = NewsTab.PODCASTS,
                podcasts = {
                    PodcastsContent(
                        uiState = PodcastsUiState(
                            episodes = listOf(curious, houston, smallSteps),
                            hasEpisodes = true,
                            isLoaded = true,
                            freshness = DataFreshness(lastUpdatedAt = TestPodcasts.NOW.minusSeconds(1_200)),
                            playback = playing,
                            now = TestPodcasts.NOW,
                        ),
                        onEpisodeClick = {},
                        onPlayClick = {},
                        onShowSelected = {},
                        onRefresh = {},
                        onErrorShown = {},
                    )
                },
            )
        }
    }

    @Test
    fun podcastEpisode() {
        composeTestRule.captureScreen("PodcastEpisode") {
            PodcastEpisodeContent(
                uiState = PodcastEpisodeUiState.Content(houston, isFavorite = true, now = TestPodcasts.NOW),
                onBackClick = {},
                onPlayClick = {},
                onToggleDownload = {},
                onToggleFavorite = {},
                onOpenPage = {},
                onErrorDismissed = {},
                onPlaybackErrorDismissed = {},
            )
        }
    }

    @Test
    fun miniPlayer() {
        composeTestRule.captureComponent("PodcastMiniPlayer") {
            MiniPlayerContent(
                playback = playing,
                onOpen = {},
                onPlayPause = {},
                onClose = {},
                onErrorDismissed = {},
            )
        }
    }

    @Test
    fun miniPlayerOfflineError() {
        composeTestRule.captureComponent("PodcastMiniPlayerOffline") {
            MiniPlayerContent(
                playback = PodcastPlayback(error = PodcastPlaybackError.OFFLINE_NOT_DOWNLOADED),
                onOpen = {},
                onPlayPause = {},
                onClose = {},
                onErrorDismissed = {},
            )
        }
    }

    @Test
    fun playerSheet() {
        composeTestRule.captureScreen("PodcastPlayerSheet") {
            PlayerSheetContent(
                playback = playing,
                onPlayPause = {},
                onSkipBack = {},
                onSkipForward = {},
                onSeek = {},
                onSpeedSelected = {},
            )
        }
    }
}
