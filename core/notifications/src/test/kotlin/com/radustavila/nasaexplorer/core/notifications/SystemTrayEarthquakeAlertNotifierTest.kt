package com.radustavila.nasaexplorer.core.notifications

import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import com.radustavila.nasaexplorer.core.model.EarthquakeAlert
import com.radustavila.nasaexplorer.core.model.MeasurementSystem
import com.radustavila.nasaexplorer.core.testing.data.TestEarthquakes
import com.radustavila.nasaexplorer.core.testing.fake.FakeUserPreferencesRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class SystemTrayEarthquakeAlertNotifierTest {

    private val context: Application = ApplicationProvider.getApplicationContext()
    private val notificationManager = context.getSystemService(NotificationManager::class.java)
    private val preferences = FakeUserPreferencesRepository()
    private val notifier = SystemTrayEarthquakeAlertNotifier(context, preferences)
    private val alert = EarthquakeAlert(TestEarthquakes.earthquake(), distanceKm = 331.0, isNearby = true)

    @Test
    fun `posts magnitude, place, distance and the USGS credit on its own channel`() = runTest {
        preferences.setMeasurementSystem(MeasurementSystem.METRIC)
        notifier.notifyEarthquake(alert)

        val posted = shadowOf(notificationManager).allNotifications.single()
        val text = posted.extras.getCharSequence(Notification.EXTRA_TEXT).toString()
        assertTrue(text, text.startsWith("M 5.7, 61 km WNW of Leni, Italy: 331 km from your place, at "))
        assertTrue(text, text.endsWith("Data: U.S. Geological Survey."))
        assertEquals("Strong earthquake near you", posted.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertEquals("earthquake_alerts", posted.channelId)
    }

    @Test
    fun `a tsunami flag says so and a newer quake replaces the older alert`() = runTest {
        notifier.notifyEarthquake(alert)
        notifier.notifyEarthquake(alert.copy(earthquake = TestEarthquakes.significant(), isNearby = false))

        val posted = shadowOf(notificationManager).allNotifications.single()
        assertEquals("Earthquake at sea, tsunami possible", posted.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertTrue(posted.extras.getCharSequence(Notification.EXTRA_TEXT).toString().contains("Check official tsunami warnings."))
    }

    @Test
    fun `stays silent when notifications are blocked for the app`() = runTest {
        shadowOf(notificationManager).setNotificationsEnabled(false)

        notifier.notifyEarthquake(alert)

        assertTrue(shadowOf(notificationManager).allNotifications.isEmpty())
    }
}
