package com.radustavila.nasaexplorer.core.notifications

import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class SystemTrayQuizReminderNotifierTest {

    private val context: Application = ApplicationProvider.getApplicationContext()
    private val notificationManager = context.getSystemService(NotificationManager::class.java)
    private val notifier = SystemTrayQuizReminderNotifier(context)

    @Test
    fun `the reminder names the streak at stake`() = runTest {
        notifier.notifyDailyQuiz(streak = 4)

        val notification = shadowOf(notificationManager).allNotifications.single()
        assertEquals("quiz_reminders", notification.channelId)
        assertEquals("Today's space quiz is waiting", notification.extras.getString(Notification.EXTRA_TITLE))
        assertTrue(notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString().contains("4-day streak"))
    }

    @Test
    fun `a newer reminder replaces the unread one`() = runTest {
        notifier.notifyDailyQuiz(streak = 0)
        notifier.notifyDailyQuiz(streak = 1)

        val notification = shadowOf(notificationManager).allNotifications.single()
        assertEquals(
            "Five new questions. Play to keep your 1-day streak going.",
            notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString(),
        )
    }

    @Test
    fun `nothing is posted while notifications are blocked`() = runTest {
        shadowOf(notificationManager).setNotificationsEnabled(false)

        notifier.notifyDailyQuiz(streak = 2)

        assertTrue(shadowOf(notificationManager).allNotifications.isEmpty())
    }
}
