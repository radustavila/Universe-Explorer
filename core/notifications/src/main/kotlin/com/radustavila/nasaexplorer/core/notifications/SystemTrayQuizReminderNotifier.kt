package com.radustavila.nasaexplorer.core.notifications

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import com.radustavila.nasaexplorer.core.domain.quiz.QuizReminderNotifier
import com.radustavila.nasaexplorer.core.model.QuizDeepLink
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Posts the daily quiz reminder (F28), with the streak at stake when there is one; a tap opens
 * the quiz. Silent when notifications are blocked.
 */
@Singleton
class SystemTrayQuizReminderNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) : QuizReminderNotifier {

    private val notificationManager = NotificationManagerCompat.from(context)

    override suspend fun notifyDailyQuiz(streak: Int) {
        if (!notificationManager.areNotificationsEnabled()) return
        ensureChannel()

        val text = if (streak > 0) {
            context.resources.getQuantityString(R.plurals.quiz_notification_text_streak, streak, streak)
        } else {
            context.getString(R.string.quiz_notification_text)
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_quiz)
            .setContentTitle(context.getString(R.string.quiz_notification_title))
            .setContentText(text)
            .setContentIntent(quizPendingIntent())
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()

        try {
            // Its own tag: today's reminder replaces yesterday's unread one, never another feature's.
            notificationManager.notify(NotificationIds.QUIZ_TAG, NotificationIds.QUIZ_REMINDER, notification)
        } catch (_: SecurityException) {
            // The permission was revoked between the check above and this call.
        }
    }

    private fun ensureChannel() {
        val channel = NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_DEFAULT)
            .setName(context.getString(R.string.quiz_notification_channel_name))
            .setDescription(context.getString(R.string.quiz_notification_channel_description))
            .build()
        notificationManager.createNotificationChannel(channel)
    }

    private fun quizPendingIntent(): PendingIntent? {
        val launcher = context.packageManager.getLaunchIntentForPackage(context.packageName)?.component ?: return null
        val intent = Intent(Intent.ACTION_VIEW, QuizDeepLink.URI.toUri())
            .setComponent(launcher)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        return PendingIntent.getActivity(
            context,
            NotificationIds.RequestCodes.QUIZ_REMINDER,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private companion object {
        const val CHANNEL_ID = "quiz_reminders"
    }
}
