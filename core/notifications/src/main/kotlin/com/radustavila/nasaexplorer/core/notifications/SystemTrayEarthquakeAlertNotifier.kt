package com.radustavila.nasaexplorer.core.notifications

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import com.radustavila.nasaexplorer.core.designsystem.earthquake.EarthquakeText
import com.radustavila.nasaexplorer.core.designsystem.units.UnitFormatter
import com.radustavila.nasaexplorer.core.domain.earthquake.EarthquakeAlertNotifier
import com.radustavila.nasaexplorer.core.domain.repository.UserPreferencesRepository
import com.radustavila.nasaexplorer.core.model.EarthEventsDeepLink
import com.radustavila.nasaexplorer.core.model.EarthquakeAlert
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * Posts the strong-quake alert: magnitude, place, how far from the saved place and when, with the
 * USGS credit; a tap opens the Live Earth events map on the quake. Silent when notifications are
 * blocked.
 */
@Singleton
class SystemTrayEarthquakeAlertNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val userPreferences: UserPreferencesRepository,
) : EarthquakeAlertNotifier {

    private val notificationManager = NotificationManagerCompat.from(context)

    override suspend fun notifyEarthquake(alert: EarthquakeAlert) {
        if (!notificationManager.areNotificationsEnabled()) return
        ensureChannel()

        val quake = alert.earthquake
        val resources = context.resources
        val formatter = UnitFormatter.create(context, userPreferences.unitPreferences.first())
        val text = context.getString(
            if (quake.tsunami) R.string.earthquake_alert_text_tsunami else R.string.earthquake_alert_text,
            EarthquakeText.title(resources, formatter, quake),
            formatter.distance(alert.distanceKm),
            formatter.time(quake.time),
        )
        val title = if (alert.isNearby) R.string.earthquake_alert_title_nearby else R.string.earthquake_alert_title_tsunami

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_earthquake)
            .setContentTitle(context.getString(title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(eventsPendingIntent(quake.id))
            .setWhen(quake.time.toEpochMilli())
            .setShowWhen(true)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .build()

        try {
            notificationManager.notify(NotificationIds.EARTHQUAKE_TAG, NotificationIds.EARTHQUAKE_ALERT, notification)
        } catch (_: SecurityException) {
            // The permission was revoked between the check above and this call.
        }
    }

    private fun ensureChannel() {
        val channel = NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_HIGH)
            .setName(context.getString(R.string.earthquake_alert_channel_name))
            .setDescription(context.getString(R.string.earthquake_alert_channel_description))
            .build()
        notificationManager.createNotificationChannel(channel)
    }

    private fun eventsPendingIntent(earthquakeId: String): PendingIntent? {
        val launcher = context.packageManager.getLaunchIntentForPackage(context.packageName)?.component ?: return null
        val intent = Intent(Intent.ACTION_VIEW, EarthEventsDeepLink.uriFor(earthquakeId).toUri())
            .setComponent(launcher)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        return PendingIntent.getActivity(
            context,
            NotificationIds.RequestCodes.EARTHQUAKE_ALERT,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private companion object {
        const val CHANNEL_ID = "earthquake_alerts"
    }
}
