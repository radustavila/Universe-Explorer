package com.radustavila.nasaexplorer.core.notifications

import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import com.radustavila.nasaexplorer.core.domain.sky.StarlinkTrain
import com.radustavila.nasaexplorer.core.domain.sky.TrackedSatellite
import com.radustavila.nasaexplorer.core.domain.sky.UpcomingPass
import com.radustavila.nasaexplorer.core.model.SatelliteSet
import com.radustavila.nasaexplorer.core.testing.data.TestSatellites
import com.radustavila.nasaexplorer.core.testing.data.TestSky
import com.radustavila.nasaexplorer.core.testing.fake.FakeUserPreferencesRepository
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class SystemTraySatellitePassNotifierTest {

    private val context: Application = ApplicationProvider.getApplicationContext()
    private val notificationManager = context.getSystemService(NotificationManager::class.java)
    private val notifier = SystemTraySatellitePassNotifier(context, FakeUserPreferencesRepository())

    private val hubble =
        UpcomingPass(TrackedSatellite(SatelliteSet.HUBBLE, TestSatellites.hubbleElements()), TestSky.pass())
    private val train = UpcomingPass(
        TrackedSatellite(
            SatelliteSet.STARLINK_TRAINS,
            TestSatellites.starlinkElements(noradId = 101_004),
            StarlinkTrain(launch = "2026-240", launchedAt = Instant.parse("2026-10-18T12:00:00Z"), satelliteCount = 24),
        ),
        TestSky.pass(),
    )

    @Test
    fun `names the satellite and where to look, on its own channel`() = runTest {
        notifier.notifyUpcomingPass(hubble)

        val posted = shadowOf(notificationManager).allNotifications.single()
        assertTrue(posted.title().startsWith("Hubble passes over you at "))
        assertEquals(
            "Look south-west. It climbs to 62° and is visible for about 7 min.",
            posted.extras.getCharSequence(Notification.EXTRA_TEXT).toString(),
        )
        assertEquals("satellite_passes", posted.channelId)
    }

    @Test
    fun `two satellites passing the same evening get a notification each`() = runTest {
        notifier.notifyUpcomingPass(hubble)
        notifier.notifyUpcomingPass(train)

        val titles = shadowOf(notificationManager).allNotifications.map { it.title() }
        assertEquals(2, titles.size)
        assertTrue(titles.any { it.startsWith("A Starlink train passes over you at ") })
    }

    @Test
    fun `stays silent when notifications are blocked for the app`() = runTest {
        shadowOf(notificationManager).setNotificationsEnabled(false)

        notifier.notifyUpcomingPass(hubble)

        assertTrue(shadowOf(notificationManager).allNotifications.isEmpty())
    }

    private fun Notification.title(): String = extras.getCharSequence(Notification.EXTRA_TITLE).toString()
}
