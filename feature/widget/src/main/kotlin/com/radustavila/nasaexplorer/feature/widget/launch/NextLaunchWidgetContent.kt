package com.radustavila.nasaexplorer.feature.widget.launch

import android.content.Intent
import android.os.SystemClock
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.appwidget.AndroidRemoteViews
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.Text
import com.radustavila.nasaexplorer.feature.widget.R
import com.radustavila.nasaexplorer.feature.widget.WidgetColors
import com.radustavila.nasaexplorer.feature.widget.WidgetSizes
import com.radustavila.nasaexplorer.feature.widget.widgetFrame
import com.radustavila.nasaexplorer.feature.widget.widgetText
import java.time.Instant

/**
 * Mission, provider and rocket, and the countdown: a live one inside the last day, the
 * minute-resolution text further out, both above the target time.
 *
 * @param liveCountdown whether to embed the launcher-ticked chronometer; off in unit tests, which
 * cannot inflate RemoteViews.
 */
@Composable
internal fun NextLaunchWidgetContent(
    state: NextLaunchWidgetState,
    title: String,
    openIntent: Intent?,
    liveCountdown: Boolean = true,
) {
    val tall = WidgetSizes.isTall(LocalSize.current)

    Column(
        modifier = widgetFrame(openIntent)
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .semantics { contentDescription = state.contentDescription },
    ) {
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                maxLines = 1,
                style = widgetText(color = WidgetColors.Accent, fontSize = 12.sp),
                modifier = GlanceModifier.defaultWeight(),
            )
            state.status?.let { status ->
                Text(
                    text = status,
                    maxLines = 1,
                    style = widgetText(color = WidgetColors.SecondaryText, fontSize = 11.sp),
                )
            }
        }
        Text(text = state.mission, maxLines = if (tall) 2 else 1, style = widgetText(fontSize = 15.sp, bold = true))
        if (state.vehicle.isNotEmpty()) {
            Text(
                text = state.vehicle,
                maxLines = 1,
                style = widgetText(color = WidgetColors.SecondaryText, fontSize = 12.sp),
            )
        }
        Spacer(GlanceModifier.height(if (tall) 10.dp else 4.dp))
        val liveTarget = state.liveCountdownTo
        if (liveCountdown && liveTarget != null) {
            LiveCountdown(liveTarget)
        } else {
            Text(
                text = state.countdown,
                maxLines = 1,
                style = widgetText(color = WidgetColors.Accent, fontSize = 20.sp, bold = true),
            )
        }
        Text(
            text = state.net,
            maxLines = 1,
            style = widgetText(color = WidgetColors.SecondaryText, fontSize = 12.sp),
        )
    }
}

/**
 * "T−" and a countdown to [target] that the launcher ticks every second; Glance cannot redraw that
 * often. The embedded views get an explicit height: left to themselves they take the rest of the
 * column and push the lines below out of the widget.
 */
@Composable
private fun LiveCountdown(target: Instant) {
    val context = LocalContext.current
    // The chronometer counts against the boot clock; the launcher re-renders widgets after a reboot.
    val base = SystemClock.elapsedRealtime() + (target.toEpochMilli() - System.currentTimeMillis())
    val countdown = RemoteViews(context.packageName, R.layout.next_launch_widget_countdown).apply {
        setChronometer(R.id.next_launch_countdown, base, null, true)
        setChronometerCountDown(R.id.next_launch_countdown, true)
    }
    Row(modifier = GlanceModifier.height(COUNTDOWN_HEIGHT), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = context.getString(R.string.next_launch_widget_t_minus),
            maxLines = 1,
            style = widgetText(color = WidgetColors.Accent, fontSize = 22.sp, bold = true),
        )
        AndroidRemoteViews(remoteViews = countdown, modifier = GlanceModifier.height(COUNTDOWN_HEIGHT))
    }
}

private val COUNTDOWN_HEIGHT = 32.dp
