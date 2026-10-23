package com.radustavila.nasaexplorer.core.notifications

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import com.radustavila.nasaexplorer.core.designsystem.skyevent.SkyEventText
import com.radustavila.nasaexplorer.core.designsystem.units.UnitFormatter
import com.radustavila.nasaexplorer.core.domain.repository.UserPreferencesRepository
import com.radustavila.nasaexplorer.core.domain.sky.SkyEventNotifier
import com.radustavila.nasaexplorer.core.model.MeteorShowerPeak
import com.radustavila.nasaexplorer.core.model.SkyEvent
import com.radustavila.nasaexplorer.core.model.SkyEventsDeepLink
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * Posts the reminder the user set for one sky event: its name and when it starts, opening the
 * event in the calendar. Each event gets its own notification (tagged with its id), so two
 * reminders on the same night both stay in the tray.
 */
@Singleton
class SystemTraySkyEventNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val userPreferences: UserPreferencesRepository,
) : SkyEventNotifier {

    private val notificationManager = NotificationManagerCompat.from(context)

    override suspend fun notifyUpcoming(event: SkyEvent) {
        if (!notificationManager.areNotificationsEnabled()) return
        ensureChannel()

        val units = UnitFormatter.create(context, userPreferences.unitPreferences.first())
        val text = when {
            event is MeteorShowerPeak && event.viewing != null -> context.getString(
                R.string.sky_event_notification_meteors,
                units.dayAndTime(event.viewing!!.darkFrom),
                units.time(event.viewing!!.bestTime),
            )

            event.start != event.time -> context.getString(
                R.string.sky_event_notification_starts,
                units.dayAndTime(event.start),
            )
            else -> context.getString(R.string.sky_event_notification_at, units.dayAndTime(event.time))
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_apod)
            .setContentTitle(SkyEventText(context.resources).title(event))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(eventPendingIntent(event))
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            // Useless a few hours after the event has started.
            .setTimeoutAfter(millisUntil(event.start.plus(EXPIRES_AFTER_START)))
            .build()

        try {
            notificationManager.notify(event.id, NotificationIds.SKY_EVENT, notification)
        } catch (_: SecurityException) {
            // The permission was revoked between the check above and this call.
        }
    }

    private fun millisUntil(at: Instant): Long = Duration.between(Instant.now(), at).toMillis().coerceAtLeast(0L)

    private fun ensureChannel() {
        val channel = NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_HIGH)
            .setName(context.getString(R.string.sky_event_channel_name))
            .setDescription(context.getString(R.string.sky_event_channel_description))
            .build()
        notificationManager.createNotificationChannel(channel)
    }

    private fun eventPendingIntent(event: SkyEvent): PendingIntent? {
        val launcher = context.packageManager.getLaunchIntentForPackage(context.packageName)?.component ?: return null
        val intent = Intent(Intent.ACTION_VIEW, SkyEventsDeepLink.event(event.id).toUri())
            .setComponent(launcher)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        return PendingIntent.getActivity(
            context,
            // One request code per event: otherwise a second reminder would retarget the first one's tap.
            event.id.hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private companion object {
        const val CHANNEL_ID = "sky_events"
        val EXPIRES_AFTER_START: Duration = Duration.ofHours(6)
    }
}
