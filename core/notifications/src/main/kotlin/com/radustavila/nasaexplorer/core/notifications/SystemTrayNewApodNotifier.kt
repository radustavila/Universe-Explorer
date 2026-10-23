package com.radustavila.nasaexplorer.core.notifications

import android.app.ActivityManager
import android.app.PendingIntent
import android.content.Context
import android.graphics.Bitmap
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.radustavila.nasaexplorer.core.designsystem.util.apodDetailsIntent
import com.radustavila.nasaexplorer.core.designsystem.util.loadBitmap
import com.radustavila.nasaexplorer.core.domain.announcement.NewApodNotifier
import com.radustavila.nasaexplorer.core.model.Apod
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Posts the "new picture of the day" notification: the title as text, the picture itself as the
 * expanded content, and a tap that opens the entry's details screen.
 *
 * Whether the user opted in is the domain's decision; this class only respects what the platform
 * allows. It stays silent when notifications are blocked for the app, and while the app is in the
 * foreground - the user is already looking at the feed the new entry just appeared in.
 */
@Singleton
class SystemTrayNewApodNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) : NewApodNotifier {

    private val notificationManager = NotificationManagerCompat.from(context)

    override suspend fun notifyNewApod(apod: Apod) {
        if (!notificationManager.areNotificationsEnabled() || isAppInForeground()) return

        ensureChannel()
        val picture = apod.previewImageUrl?.let { url -> context.loadBitmap(url, PICTURE_SIZE_PX) }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_apod)
            .setContentTitle(context.getString(R.string.new_apod_notification_title))
            .setContentText(apod.title)
            .setContentIntent(detailsPendingIntent(apod))
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .apply {
                if (picture != null) {
                    setLargeIcon(picture)
                    // The large icon is a thumbnail of the same picture; hide it once expanded.
                    setStyle(
                        NotificationCompat.BigPictureStyle()
                            .bigPicture(picture)
                            .bigLargeIcon(null as Bitmap?)
                            .setSummaryText(apod.title),
                    )
                }
            }
            .build()

        try {
            // A single id: a newer entry replaces an unread older one instead of stacking up.
            notificationManager.notify(NotificationIds.NEW_APOD, notification)
        } catch (_: SecurityException) {
            // The permission was revoked between the check above and this call.
        }
    }

    private fun ensureChannel() {
        val channel = NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_DEFAULT)
            .setName(context.getString(R.string.new_apod_channel_name))
            .setDescription(context.getString(R.string.new_apod_channel_description))
            .build()
        notificationManager.createNotificationChannel(channel)
    }

    private fun detailsPendingIntent(apod: Apod): PendingIntent? =
        context.apodDetailsIntent(apod.date)?.let { intent ->
            PendingIntent.getActivity(
                context,
                NotificationIds.RequestCodes.NEW_APOD,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        }

    private fun isAppInForeground(): Boolean {
        val processState = ActivityManager.RunningAppProcessInfo()
        ActivityManager.getMyMemoryState(processState)
        return processState.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
    }

    private companion object {
        const val CHANNEL_ID = "new_apod"

        /** Large enough for the expanded notification, small enough to cross the binder. */
        const val PICTURE_SIZE_PX = 1024
    }
}
