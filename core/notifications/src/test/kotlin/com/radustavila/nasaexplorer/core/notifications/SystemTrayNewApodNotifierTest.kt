package com.radustavila.nasaexplorer.core.notifications

import android.app.ActivityManager
import android.app.ActivityManager.RunningAppProcessInfo
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.os.Process
import androidx.test.core.app.ApplicationProvider
import com.radustavila.nasaexplorer.core.model.Apod
import com.radustavila.nasaexplorer.core.model.MediaType
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class SystemTrayNewApodNotifierTest {

    private val context: Application = ApplicationProvider.getApplicationContext()
    private val notificationManager = context.getSystemService(NotificationManager::class.java)
    private val activityManager = context.getSystemService(ActivityManager::class.java)
    private val notifier = SystemTrayNewApodNotifier(context)

    /** Robolectric reports the test process as foreground, where the notifier deliberately stays quiet. */
    @Before
    fun moveAppToBackground() {
        setAppImportance(RunningAppProcessInfo.IMPORTANCE_CACHED)
    }

    @Test
    fun `posts the new entry on its own channel`() = runTest {
        notifier.notifyNewApod(apod(title = "The Horsehead Nebula"))

        val posted = shadowOf(notificationManager).allNotifications.single()
        assertEquals(
            "The Horsehead Nebula",
            posted.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString(),
        )
        assertEquals(posted.channelId, notificationManager.notificationChannels.single().id)
    }

    @Test
    fun `a newer entry replaces the previous notification`() = runTest {
        notifier.notifyNewApod(apod(title = "Yesterday"))
        notifier.notifyNewApod(apod(title = "Today"))

        val posted = shadowOf(notificationManager).allNotifications.single()
        assertEquals("Today", posted.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString())
    }

    @Test
    fun `stays silent when notifications are blocked for the app`() = runTest {
        shadowOf(notificationManager).setNotificationsEnabled(false)

        notifier.notifyNewApod(apod(title = "The Horsehead Nebula"))

        assertTrue(shadowOf(notificationManager).allNotifications.isEmpty())
    }

    @Test
    fun `stays silent while the app is in the foreground`() = runTest {
        setAppImportance(RunningAppProcessInfo.IMPORTANCE_FOREGROUND)

        notifier.notifyNewApod(apod(title = "The Horsehead Nebula"))

        assertTrue(shadowOf(notificationManager).allNotifications.isEmpty())
    }

    private fun setAppImportance(importance: Int) {
        val process = RunningAppProcessInfo().apply {
            pid = Process.myPid()
            this.importance = importance
        }
        shadowOf(activityManager).setProcesses(listOf(process))
    }

    /** No still image, so the test never reaches the network for a picture. */
    private fun apod(title: String) = Apod(
        date = LocalDate.of(2026, 9, 10),
        title = title,
        explanation = "Explanation",
        mediaType = MediaType.OTHER,
        url = "https://apod.nasa.gov/apod/interactive.html",
    )
}
