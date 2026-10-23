package com.radustavila.nasaexplorer.core.notifications

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import com.radustavila.nasaexplorer.core.designsystem.units.UnitFormatter
import com.radustavila.nasaexplorer.core.domain.launch.LaunchReminderNotifier
import com.radustavila.nasaexplorer.core.domain.repository.UserPreferencesRepository
import com.radustavila.nasaexplorer.core.model.Launch
import com.radustavila.nasaexplorer.core.model.LaunchDeepLink
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * Posts the reminder the user set for one launch, shortly before lift-off: what flies, when and
 * from where, opening the launch's page in the app, with a button for the livestream when one is
 * announced. Each launch gets its own notification (tagged with its id).
 */
@Singleton
class SystemTrayLaunchReminderNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val userPreferences: UserPreferencesRepository,
) : LaunchReminderNotifier {

    private val notificationManager = NotificationManagerCompat.from(context)

    override suspend fun notifyLiftoffSoon(launch: Launch) {
        if (!notificationManager.areNotificationsEnabled()) return
        ensureChannel()

        val units = UnitFormatter.create(context, userPreferences.unitPreferences.first())
        val text = context.getString(R.string.launch_reminder_text, units.time(launch.net), launch.pad)
            .takeIf { launch.pad.isNotBlank() }
            ?: context.getString(R.string.launch_reminder_text_no_pad, units.time(launch.net))
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_apod)
            .setContentTitle(launch.name)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(launchPendingIntent(launch))
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            // Stale once the rocket is well on its way.
            .setTimeoutAfter(millisUntil(launch.net.plus(EXPIRES_AFTER_NET)))
        launch.livestreamUrl?.let { url ->
            val watch = context.getString(R.string.launch_reminder_watch)
            builder.addAction(0, watch, livestreamPendingIntent(launch, url))
        }

        try {
            notificationManager.notify(launch.id, NotificationIds.LAUNCH_REMINDER, builder.build())
        } catch (_: SecurityException) {
            // The permission was revoked between the check above and this call.
        }
    }

    private fun millisUntil(at: Instant): Long = Duration.between(Instant.now(), at).toMillis().coerceAtLeast(0L)

    private fun ensureChannel() {
        val channel = NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_HIGH)
            .setName(context.getString(R.string.launch_reminder_channel_name))
            .setDescription(context.getString(R.string.launch_reminder_channel_description))
            .build()
        notificationManager.createNotificationChannel(channel)
    }

    private fun launchPendingIntent(launch: Launch): PendingIntent? {
        val launcher = context.packageManager.getLaunchIntentForPackage(context.packageName)?.component ?: return null
        val intent = Intent(Intent.ACTION_VIEW, LaunchDeepLink.uriFor(launch.id).toUri())
            .setComponent(launcher)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        return PendingIntent.getActivity(
            context,
            // One request code per launch: otherwise a second reminder would retarget the first one's tap.
            launch.id.hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    /** The stream opens in whatever app handles it (YouTube, X, a browser). */
    private fun livestreamPendingIntent(launch: Launch, url: String): PendingIntent = PendingIntent.getActivity(
        context,
        launch.id.hashCode() + 1,
        Intent(Intent.ACTION_VIEW, url.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private companion object {
        const val CHANNEL_ID = "launch_reminders"
        val EXPIRES_AFTER_NET: Duration = Duration.ofHours(2)
    }
}
