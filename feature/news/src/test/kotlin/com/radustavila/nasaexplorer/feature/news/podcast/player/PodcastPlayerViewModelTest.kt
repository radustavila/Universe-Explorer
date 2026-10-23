package com.radustavila.nasaexplorer.feature.news.podcast.player

import com.radustavila.nasaexplorer.core.model.PodcastPlayback
import com.radustavila.nasaexplorer.core.testing.data.TestPodcasts
import com.radustavila.nasaexplorer.core.testing.fake.FakePodcastPlayer
import java.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PodcastPlayerViewModelTest {

    private val player = FakePodcastPlayer()
    private val viewModel = PodcastPlayerViewModel(player)

    @Test
    fun `the controls drive the player`() {
        val episode = TestPodcasts.curiousUniverse()
        player.setPlayback(PodcastPlayback(episode = episode, duration = episode.duration))

        viewModel.onPlayPause()
        viewModel.onSkipForward()
        viewModel.onSkipBack()
        viewModel.onSeek(Duration.ofMinutes(5))
        viewModel.onSpeedSelected(1.5f)
        viewModel.onPlayPause()
        viewModel.onClose()

        assertEquals(
            listOf(
                "play:${episode.id}",
                "seekBy:PT30S",
                "seekBy:PT-15S",
                "seekTo:PT5M",
                "setSpeed:1.5",
                "pause",
                "stop",
            ),
            player.calls,
        )
        assertNull(viewModel.playback.value.episode)
        assertEquals(1.5f, viewModel.playback.value.speed)
    }

    @Test
    fun `progress is known once the length is`() {
        assertNull(PodcastPlayback(position = Duration.ofMinutes(1)).fraction())
        assertEquals(
            0.25f,
            PodcastPlayback(position = Duration.ofMinutes(15), duration = Duration.ofHours(1)).fraction(),
        )
    }
}
