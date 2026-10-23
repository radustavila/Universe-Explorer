package com.radustavila.nasaexplorer.core.notifications

import android.app.Application
import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import com.radustavila.nasaexplorer.core.domain.sky.TrackedSatellite
import com.radustavila.nasaexplorer.core.domain.sky.UpcomingPass
import com.radustavila.nasaexplorer.core.model.AuroraAlert
import com.radustavila.nasaexplorer.core.model.EarthquakeAlert
import com.radustavila.nasaexplorer.core.model.MoonDuringWindow
import com.radustavila.nasaexplorer.core.model.ObservingWindow
import com.radustavila.nasaexplorer.core.model.SatelliteSet
import com.radustavila.nasaexplorer.core.model.StargazingAlert
import com.radustavila.nasaexplorer.core.testing.data.TestEarthquakes
import com.radustavila.nasaexplorer.core.testing.data.TestLaunches
import com.radustavila.nasaexplorer.core.testing.data.TestSatellites
import com.radustavila.nasaexplorer.core.testing.data.TestSky
import com.radustavila.nasaexplorer.core.testing.data.TestSkyEvents
import com.radustavila.nasaexplorer.core.testing.fake.FakeUserPreferencesRepository
import java.lang.reflect.Modifier
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class NotificationIdsTest {

    @Test
    fun `every notifier posts with its own id`() {
        val fixed = intConstants(NotificationIds::class.java).filterKeys { it != "SATELLITE_PASS_BASE" }
        val satellites = SatelliteSet.entries.associate {
            "satellitePass(${it.name})" to NotificationIds.satellitePass(it)
        }
        val all = fixed + satellites

        assertEquals(duplicates(all), all.size, all.values.toSet().size)
    }

    @Test
    fun `every content intent has its own request code`() {
        val codes = intConstants(NotificationIds.RequestCodes::class.java)

        assertEquals(duplicates(codes), codes.size, codes.values.toSet().size)
    }

    @Test
    fun `alerts and reminders of different features stay in the tray side by side`() = runTest {
        val context: Application = ApplicationProvider.getApplicationContext()
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        val preferences = FakeUserPreferencesRepository()

        SystemTrayAuroraAlertNotifier(context, preferences).notifyAuroraAlert(
            AuroraAlert(startsAt = Instant.parse("2026-10-19T21:00:00Z"), expectedKp = 5.67, threshold = 5),
        )
        SystemTrayIssPassNotifier(context, preferences).notifyUpcomingPass(TestSky.pass())
        SystemTraySatellitePassNotifier(context, preferences).notifyUpcomingPass(
            UpcomingPass(TrackedSatellite(SatelliteSet.HUBBLE, TestSatellites.hubbleElements()), TestSky.pass()),
        )
        SystemTraySkyEventNotifier(context, preferences).notifyUpcoming(TestSkyEvents.totalSolarEclipse())
        SystemTrayStargazingAlertNotifier(context, preferences).notifyClearNight(
            StargazingAlert(
                score = 86,
                window = ObservingWindow(
                    start = Instant.parse("2026-10-23T19:00:00Z"),
                    end = Instant.parse("2026-10-24T01:00:00Z"),
                    meanCloudCover = 8.0,
                    moon = MoonDuringWindow.SETS,
                ),
                moonIlluminatedFraction = 0.42,
            ),
        )
        SystemTrayEarthquakeAlertNotifier(context, preferences).notifyEarthquake(
            EarthquakeAlert(TestEarthquakes.earthquake(), distanceKm = 331.0, isNearby = true),
        )
        SystemTrayLaunchReminderNotifier(context, preferences).notifyLiftoffSoon(
            TestLaunches.launch(net = Instant.now().plusSeconds(900)),
        )
        SystemTrayQuizReminderNotifier(context).notifyDailyQuiz(streak = 3)

        assertEquals(
            setOf(
                "aurora_alerts",
                "iss_passes",
                "satellite_passes",
                "sky_events",
                "stargazing_alerts",
                "earthquake_alerts",
                "launch_reminders",
                "quiz_reminders",
            ),
            shadowOf(notificationManager).allNotifications.map { it.channelId }.toSet(),
        )
        assertEquals(8, shadowOf(notificationManager).allNotifications.size)
    }

    private fun intConstants(holder: Class<*>): Map<String, Int> =
        holder.declaredFields
            .filter { Modifier.isStatic(it.modifiers) && it.type == Int::class.javaPrimitiveType }
            .associate { it.name to it.getInt(null) }

    private fun duplicates(ids: Map<String, Int>): String =
        ids.entries.groupBy({ it.value }, { it.key }).filterValues { it.size > 1 }.toString()
}
