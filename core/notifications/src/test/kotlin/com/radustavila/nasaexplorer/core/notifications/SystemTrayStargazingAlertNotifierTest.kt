package com.radustavila.nasaexplorer.core.notifications

import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import com.radustavila.nasaexplorer.core.model.MoonDuringWindow
import com.radustavila.nasaexplorer.core.model.ObservingWindow
import com.radustavila.nasaexplorer.core.model.StargazingAlert
import com.radustavila.nasaexplorer.core.model.TimeFormat
import com.radustavila.nasaexplorer.core.testing.fake.FakeUserPreferencesRepository
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class SystemTrayStargazingAlertNotifierTest {

    private val context: Application = ApplicationProvider.getApplicationContext()
    private val notificationManager = context.getSystemService(NotificationManager::class.java)
    private val preferences = FakeUserPreferencesRepository()
    private val notifier = SystemTrayStargazingAlertNotifier(context, preferences)

    private val window = ObservingWindow(
        start = Instant.parse("2026-10-23T19:00:00Z"),
        end = Instant.parse("2026-10-24T01:00:00Z"),
        meanCloudCover = 8.0,
        moon = MoonDuringWindow.SETS,
    )
    private val alert = StargazingAlert(score = 86, window = window, moonIlluminatedFraction = 0.42)

    @Test
    fun `posts the clear window, the Moon and the score on its own channel`() = runTest {
        preferences.setTimeFormat(TimeFormat.TWENTY_FOUR_HOUR)
        notifier.notifyClearNight(alert)

        val posted = shadowOf(notificationManager).allNotifications.single()
        val text = posted.extras.getCharSequence(Notification.EXTRA_TEXT).toString()
        val start = window.start.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"))
        assertTrue(text, text.startsWith("Clear from $start to "))
        assertTrue(text, text.contains("Moon sets (42% lit). Observing score 86 out of 100."))
        assertEquals("Clear night ahead", posted.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertEquals("stargazing_alerts", posted.channelId)
    }

    @Test
    fun `tonight's alert replaces last night's`() = runTest {
        notifier.notifyClearNight(alert)
        notifier.notifyClearNight(alert.copy(score = 70))

        val posted = shadowOf(notificationManager).allNotifications.single()
        assertTrue(posted.extras.getCharSequence(Notification.EXTRA_TEXT).toString().contains("score 70 "))
    }

    @Test
    fun `stays silent when notifications are blocked for the app`() = runTest {
        shadowOf(notificationManager).setNotificationsEnabled(false)

        notifier.notifyClearNight(alert)

        assertTrue(shadowOf(notificationManager).allNotifications.isEmpty())
    }
}
