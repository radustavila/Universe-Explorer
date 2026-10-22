package com.radustavila.nasaexplorer.core.notifications

import android.content.Context
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.radustavila.nasaexplorer.core.designsystem.units.UnitFormatter
import com.radustavila.nasaexplorer.core.domain.repository.UserPreferencesRepository
import com.radustavila.nasaexplorer.core.domain.sky.SatellitePassNotifier
import com.radustavila.nasaexplorer.core.domain.sky.UpcomingPass
import com.radustavila.nasaexplorer.core.model.SatelliteSet
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.first

/**
 * Posts the opt-in "Hubble overhead soon" reminders, like [SystemTrayIssPassNotifier] but on a
 * channel of their own (the user may want the ISS and not the rest) and with one notification per
 * set, so Hubble's does not replace Tiangong's when both pass in the same evening.
 */
@Singleton
class SystemTraySatellitePassNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val userPreferences: UserPreferencesRepository,
) : SatellitePassNotifier {

    private val notificationManager = NotificationManagerCompat.from(context)

    override suspend fun notifyUpcomingPass(pass: UpcomingPass) {
        if (!notificationManager.areNotificationsEnabled()) return
        ensureChannel()

        val satellitePass = pass.pass
        val start = UnitFormatter.create(context, userPreferences.unitPreferences.first()).time(satellitePass.start)
        val minutes = Duration.between(satellitePass.start, satellitePass.end).toMinutes().coerceAtLeast(1).toInt()
        val text = context.getString(
            R.string.iss_pass_notification_text,
            context.getString(compassPoint(satellitePass.startAzimuthDegrees)),
            satellitePass.maxElevationDegrees.roundToInt(),
            minutes,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_apod)
            .setContentTitle(title(pass, start))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(context.openAppIntent(REQUEST_CODE))
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            // Useless after the pass: gone once it has ended.
            .setTimeoutAfter(Duration.between(Instant.now(), satellitePass.end).toMillis().coerceAtLeast(0))
            .build()

        try {
            notificationManager.notify(NOTIFICATION_ID_BASE + pass.satellite.set.ordinal, notification)
        } catch (_: SecurityException) {
            // The permission was revoked between the check above and this call.
        }
    }

    private fun title(pass: UpcomingPass, start: String): String = when (pass.satellite.set) {
        SatelliteSet.ISS -> context.getString(R.string.iss_pass_notification_title, start)
        SatelliteSet.HUBBLE -> context.getString(R.string.satellite_pass_notification_title_hubble, start)
        SatelliteSet.TIANGONG -> context.getString(R.string.satellite_pass_notification_title_tiangong, start)
        SatelliteSet.STARLINK_TRAINS -> context.getString(R.string.satellite_pass_notification_title_starlink, start)
        SatelliteSet.BRIGHT_SATELLITES ->
            context.getString(R.string.satellite_pass_notification_title_named, pass.satellite.name, start)
    }

    private fun ensureChannel() {
        val channel = NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_HIGH)
            .setName(context.getString(R.string.satellite_pass_channel_name))
            .setDescription(context.getString(R.string.satellite_pass_channel_description))
            .build()
        notificationManager.createNotificationChannel(channel)
    }

    private companion object {
        const val CHANNEL_ID = "satellite_passes"

        /** One id per set from here up, clear of the other notifiers' ids. */
        const val NOTIFICATION_ID_BASE = 40
        const val REQUEST_CODE = 4
    }
}
