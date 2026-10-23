package com.radustavila.nasaexplorer.core.notifications

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import com.radustavila.nasaexplorer.core.designsystem.units.UnitFormatter
import com.radustavila.nasaexplorer.core.domain.announcement.AuroraAlertNotifier
import com.radustavila.nasaexplorer.core.domain.repository.UserPreferencesRepository
import com.radustavila.nasaexplorer.core.model.AuroraAlert
import com.radustavila.nasaexplorer.core.model.GeomagneticStorm
import com.radustavila.nasaexplorer.core.model.Kp
import com.radustavila.nasaexplorer.core.model.SpaceWeatherDeepLink
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * Posts the aurora alert: when the storm starts, how strong it is expected to be, and a tap that
 * opens the space weather screen.
 *
 * Unlike the new-picture notification it is also posted while the app is open: a storm is worth
 * hearing about even then. It stays silent when notifications are blocked for the app.
 */
@Singleton
class SystemTrayAuroraAlertNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val userPreferences: UserPreferencesRepository,
) : AuroraAlertNotifier {

    private val notificationManager = NotificationManagerCompat.from(context)

    override suspend fun notifyAuroraAlert(alert: AuroraAlert) {
        if (!notificationManager.areNotificationsEnabled()) return
        ensureChannel()

        val level = Kp.level(alert.expectedKp)
        val strength = GeomagneticStorm.forLevel(level)
            ?.let { storm -> context.getString(R.string.aurora_alert_strength_storm, level, storm.name) }
            ?: context.getString(R.string.aurora_alert_strength, level)
        val startsAt = UnitFormatter.create(context, userPreferences.unitPreferences.first()).dayAndTime(alert.startsAt)
        val text = context.getString(R.string.aurora_alert_text, strength, startsAt, alert.threshold)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_aurora)
            .setContentTitle(context.getString(R.string.aurora_alert_title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(spaceWeatherPendingIntent())
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .build()

        try {
            // A single id: a later storm replaces an unread earlier alert instead of stacking up.
            notificationManager.notify(NotificationIds.AURORA_ALERT, notification)
        } catch (_: SecurityException) {
            // The permission was revoked between the check above and this call.
        }
    }

    private fun ensureChannel() {
        val channel = NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_HIGH)
            .setName(context.getString(R.string.aurora_alert_channel_name))
            .setDescription(context.getString(R.string.aurora_alert_channel_description))
            .build()
        notificationManager.createNotificationChannel(channel)
    }

    private fun spaceWeatherPendingIntent(): PendingIntent? {
        val launcher = context.packageManager.getLaunchIntentForPackage(context.packageName)?.component ?: return null
        val intent = Intent(Intent.ACTION_VIEW, SpaceWeatherDeepLink.URI.toUri())
            .setComponent(launcher)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        return PendingIntent.getActivity(
            context,
            NotificationIds.RequestCodes.AURORA_ALERT,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private companion object {
        const val CHANNEL_ID = "aurora_alerts"
    }
}
