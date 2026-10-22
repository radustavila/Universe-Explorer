package com.radustavila.nasaexplorer.core.notifications

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import com.radustavila.nasaexplorer.core.designsystem.stargazing.StargazingText
import com.radustavila.nasaexplorer.core.designsystem.units.UnitFormatter
import com.radustavila.nasaexplorer.core.domain.repository.UserPreferencesRepository
import com.radustavila.nasaexplorer.core.domain.stargazing.StargazingAlertNotifier
import com.radustavila.nasaexplorer.core.model.SkyTonightDeepLink
import com.radustavila.nasaexplorer.core.model.StargazingAlert
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * Posts the clear-night alert: when tonight is clear, where the Moon is, the score, and a tap that
 * opens Sky tonight. Posted while the app is open too; silent when notifications are blocked.
 */
@Singleton
class SystemTrayStargazingAlertNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val userPreferences: UserPreferencesRepository,
) : StargazingAlertNotifier {

    private val notificationManager = NotificationManagerCompat.from(context)

    override suspend fun notifyClearNight(alert: StargazingAlert) {
        if (!notificationManager.areNotificationsEnabled()) return
        ensureChannel()

        val formatter = UnitFormatter.create(context, userPreferences.unitPreferences.first())
        val window = StargazingText.window(context.resources, formatter, alert.window, alert.moonIlluminatedFraction)
        val text = context.getString(R.string.stargazing_alert_text, window, alert.score)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_stargazing)
            .setContentTitle(context.getString(R.string.stargazing_alert_title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(skyTonightPendingIntent())
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .build()

        try {
            // Its own tag: tonight's alert replaces last night's unread one, never another feature's.
            notificationManager.notify(TAG, NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // The permission was revoked between the check above and this call.
        }
    }

    private fun ensureChannel() {
        val channel = NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_DEFAULT)
            .setName(context.getString(R.string.stargazing_alert_channel_name))
            .setDescription(context.getString(R.string.stargazing_alert_channel_description))
            .build()
        notificationManager.createNotificationChannel(channel)
    }

    private fun skyTonightPendingIntent(): PendingIntent? {
        val launcher = context.packageManager.getLaunchIntentForPackage(context.packageName)?.component ?: return null
        val intent = Intent(Intent.ACTION_VIEW, SkyTonightDeepLink.URI.toUri())
            .setComponent(launcher)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        return PendingIntent.getActivity(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private companion object {
        const val CHANNEL_ID = "stargazing_alerts"
        const val TAG = "stargazing"
        const val NOTIFICATION_ID = 1
        const val REQUEST_CODE = 11
    }
}
