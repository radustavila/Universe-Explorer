package com.radustavila.nasaexplorer.core.notifications

import android.content.Context
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.radustavila.nasaexplorer.core.designsystem.units.UnitFormatter
import com.radustavila.nasaexplorer.core.domain.repository.UserPreferencesRepository
import com.radustavila.nasaexplorer.core.domain.sky.IssPassNotifier
import com.radustavila.nasaexplorer.core.model.SatellitePass
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.first

/**
 * Posts the opt-in "ISS overhead soon" reminder: when, where to look, how high it climbs. Unlike
 * the new-picture notification it is posted in the foreground too - the user may be on another
 * screen and the pass will not wait.
 */
@Singleton
class SystemTrayIssPassNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val userPreferences: UserPreferencesRepository,
) : IssPassNotifier {

    private val notificationManager = NotificationManagerCompat.from(context)

    override suspend fun notifyUpcomingPass(pass: SatellitePass) {
        if (!notificationManager.areNotificationsEnabled()) return
        ensureChannel()

        val start = UnitFormatter.create(context, userPreferences.unitPreferences.first()).time(pass.start)
        val minutes = Duration.between(pass.start, pass.end).toMinutes().coerceAtLeast(1).toInt()
        val text = context.getString(
            R.string.iss_pass_notification_text,
            context.getString(compassPoint(pass.startAzimuthDegrees)),
            pass.maxElevationDegrees.roundToInt(),
            minutes,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_apod)
            .setContentTitle(context.getString(R.string.iss_pass_notification_title, start))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(context.openAppIntent(NotificationIds.RequestCodes.ISS_PASS))
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            // Useless after the pass: gone once it has ended.
            .setTimeoutAfter(Duration.between(Instant.now(), pass.end).toMillis().coerceAtLeast(0))
            .build()

        try {
            notificationManager.notify(NotificationIds.ISS_PASS, notification)
        } catch (_: SecurityException) {
            // The permission was revoked between the check above and this call.
        }
    }

    private fun ensureChannel() {
        val channel = NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_HIGH)
            .setName(context.getString(R.string.iss_pass_channel_name))
            .setDescription(context.getString(R.string.iss_pass_channel_description))
            .build()
        notificationManager.createNotificationChannel(channel)
    }

    private companion object {
        const val CHANNEL_ID = "iss_passes"
    }
}
