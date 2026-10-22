package com.radustavila.nasaexplorer.core.notifications

import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import com.radustavila.nasaexplorer.core.testing.data.TestSkyEvents
import com.radustavila.nasaexplorer.core.testing.fake.FakeUserPreferencesRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class SystemTraySkyEventNotifierTest {

    private val context: Application = ApplicationProvider.getApplicationContext()
    private val notificationManager = context.getSystemService(NotificationManager::class.java)
    private val notifier = SystemTraySkyEventNotifier(context, FakeUserPreferencesRepository())

    @Test
    fun `posts the event's name and local start on its own channel`() = runTest {
        notifier.notifyUpcoming(TestSkyEvents.totalSolarEclipse())

        val posted = shadowOf(notificationManager).allNotifications.single()
        assertEquals("Total solar eclipse", posted.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        val text = posted.extras.getCharSequence(Notification.EXTRA_TEXT).toString()
        assertTrue(text, text.startsWith("Starts ") && text.endsWith(" from your place."))
        assertEquals("sky_events", posted.channelId)
        assertEquals(
            NotificationManager.IMPORTANCE_HIGH,
            notificationManager.getNotificationChannel("sky_events").importance,
        )
    }

    @Test
    fun `a meteor shower says when it gets dark, an instant event when it happens`() = runTest {
        notifier.notifyUpcoming(TestSkyEvents.perseids())
        notifier.notifyUpcoming(TestSkyEvents.decemberSolstice())

        val texts = shadowOf(notificationManager).allNotifications.associate {
            it.extras.getCharSequence(Notification.EXTRA_TITLE).toString() to
                it.extras.getCharSequence(Notification.EXTRA_TEXT).toString()
        }
        assertTrue(texts.getValue("Perseids peak"), texts.getValue("Perseids peak").startsWith("Dark from "))
        assertTrue(texts.getValue("December solstice"), texts.getValue("December solstice").endsWith("."))
    }

    @Test
    fun `each event keeps its own notification, and a repeat replaces it`() = runTest {
        notifier.notifyUpcoming(TestSkyEvents.totalSolarEclipse())
        notifier.notifyUpcoming(TestSkyEvents.perseids())
        notifier.notifyUpcoming(TestSkyEvents.perseids())

        assertEquals(2, shadowOf(notificationManager).allNotifications.size)
    }

    @Test
    fun `stays silent when notifications are blocked for the app`() = runTest {
        shadowOf(notificationManager).setNotificationsEnabled(false)

        notifier.notifyUpcoming(TestSkyEvents.totalSolarEclipse())

        assertTrue(shadowOf(notificationManager).allNotifications.isEmpty())
    }
}
