package com.radustavila.nasaexplorer.core.notifications

import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import com.radustavila.nasaexplorer.core.model.AuroraAlert
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SystemTrayAuroraAlertNotifierTest {

    private val context: Application = ApplicationProvider.getApplicationContext()
    private val notificationManager = context.getSystemService(NotificationManager::class.java)
    private val notifier = SystemTrayAuroraAlertNotifier(context)

    private val alert = AuroraAlert(startsAt = Instant.parse("2026-10-19T21:00:00Z"), expectedKp = 5.67, threshold = 5)

    @Test
    fun `posts the storm's strength and the user's threshold on its own channel, even in the foreground`() = runTest {
        notifier.notifyAuroraAlert(alert)

        val posted = shadowOf(notificationManager).allNotifications.single()
        val text = posted.extras.getCharSequence(Notification.EXTRA_TEXT).toString()
        assertTrue(text, text.startsWith("Kp 6 (G2 storm) is forecast from "))
        assertTrue(text, text.endsWith("at Kp 5 or more."))
        assertEquals("aurora_alerts", posted.channelId)
        assertEquals(NotificationManager.IMPORTANCE_HIGH, notificationManager.getNotificationChannel("aurora_alerts").importance)
    }

    @Test
    fun `a later storm replaces the earlier alert`() = runTest {
        notifier.notifyAuroraAlert(alert)
        notifier.notifyAuroraAlert(alert.copy(expectedKp = 4.0, threshold = 4))

        val posted = shadowOf(notificationManager).allNotifications.single()
        assertTrue(posted.extras.getCharSequence(Notification.EXTRA_TEXT).toString().startsWith("Kp 4 is forecast"))
    }

    @Test
    fun `stays silent when notifications are blocked for the app`() = runTest {
        shadowOf(notificationManager).setNotificationsEnabled(false)

        notifier.notifyAuroraAlert(alert)

        assertTrue(shadowOf(notificationManager).allNotifications.isEmpty())
    }
}
