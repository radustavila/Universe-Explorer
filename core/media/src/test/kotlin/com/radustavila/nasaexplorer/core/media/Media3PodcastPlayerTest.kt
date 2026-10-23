package com.radustavila.nasaexplorer.core.media

import android.content.Context
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.radustavila.nasaexplorer.core.model.PodcastPlaybackError
import com.radustavila.nasaexplorer.core.model.PodcastProgress
import com.radustavila.nasaexplorer.core.testing.data.TestPodcasts
import com.radustavila.nasaexplorer.core.testing.fake.FakeNetworkMonitor
import com.radustavila.nasaexplorer.core.testing.fake.FakePodcastRepository
import java.time.Duration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class Media3PodcastPlayerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `offline, an episode that was not downloaded does not play and says why`() {
        val repository = FakePodcastRepository(listOf(TestPodcasts.houston()))
        val player = Media3PodcastPlayer(
            context,
            repository,
            FakeNetworkMonitor(initiallyOnline = false),
            CoroutineScope(UnconfinedTestDispatcher()),
        )

        player.play(TestPodcasts.houston())
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(PodcastPlaybackError.OFFLINE_NOT_DOWNLOADED, player.playback.value.error)
        assertNull(player.playback.value.episode)

        player.clearError()
        assertNull(player.playback.value.error)
    }

    @Test
    fun `an episode resumes where it was left, unless finished or nearly so`() {
        val hour = Duration.ofHours(1)
        val twelveMinutes = Duration.ofMinutes(12)

        assertEquals(Duration.ZERO, Media3PodcastPlayer.resumePosition(null, hour))
        assertEquals(twelveMinutes, Media3PodcastPlayer.resumePosition(PodcastProgress(twelveMinutes), hour))
        assertEquals(twelveMinutes, Media3PodcastPlayer.resumePosition(PodcastProgress(twelveMinutes), null))
        assertEquals(
            Duration.ZERO,
            Media3PodcastPlayer.resumePosition(PodcastProgress(twelveMinutes, isFinished = true), hour),
        )
        assertEquals(Duration.ZERO, Media3PodcastPlayer.resumePosition(PodcastProgress(hour.minusSeconds(5)), hour))
    }
}
