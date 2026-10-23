package com.radustavila.nasaexplorer.core.notifications

import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import com.radustavila.nasaexplorer.core.testing.data.TestLaunches
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
class SystemTrayLaunchReminderNotifierTest {

    private val context: Application = ApplicationProvider.getApplicationContext()
    private val notificationManager = context.getSystemService(NotificationManager::class.java)
    private val notifier = SystemTrayLaunchReminderNotifier(context, FakeUserPreferencesRepository())
    private val soon = Instant.now().plusSeconds(15 * 60)

    @Test
    fun `posts the launch, its time and pad on its own channel, with a livestream button`() = runTest {
        notifier.notifyLiftoffSoon(TestLaunches.launch(net = soon))

        val posted = shadowOf(notificationManager).allNotifications.single()
        assertEquals(
            "Falcon 9 Block 5 | Bandwagon 5",
            posted.extras.getCharSequence(Notification.EXTRA_TITLE).toString(),
        )
        val text = posted.extras.getCharSequence(Notification.EXTRA_TEXT).toString()
        assertTrue(text, text.startsWith("Lifts off at ") && text.endsWith("Cape Canaveral SFS, FL, USA."))
        assertEquals("launch_reminders", posted.channelId)
        assertEquals("Watch live", posted.actions.single().title.toString())
        assertEquals(
            NotificationManager.IMPORTANCE_HIGH,
            notificationManager.getNotificationChannel("launch_reminders").importance,
        )
    }

    @Test
    fun `without a livestream there is no button, and each launch keeps its own notification`() = runTest {
        notifier.notifyLiftoffSoon(TestLaunches.launch(id = "a", net = soon, livestreamUrl = null))
        notifier.notifyLiftoffSoon(TestLaunches.launch(id = "b", net = soon))
        notifier.notifyLiftoffSoon(TestLaunches.launch(id = "b", net = soon))

        val posted = shadowOf(notificationManager).allNotifications
        assertEquals(2, posted.size)
        assertTrue(posted.any { it.actions == null })
    }

    @Test
    fun `stays silent when notifications are blocked for the app`() = runTest {
        shadowOf(notificationManager).setNotificationsEnabled(false)

        notifier.notifyLiftoffSoon(TestLaunches.launch(net = soon))

        assertTrue(shadowOf(notificationManager).allNotifications.isEmpty())
    }
}
