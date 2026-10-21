package com.radustavila.nasaexplorer.feature.widget.aurora

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.LocalSize
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.Text
import com.radustavila.nasaexplorer.core.model.Kp
import com.radustavila.nasaexplorer.feature.widget.WidgetColors
import com.radustavila.nasaexplorer.feature.widget.WidgetSizes
import com.radustavila.nasaexplorer.feature.widget.widgetFrame
import com.radustavila.nasaexplorer.feature.widget.widgetText

/**
 * Kp in its colour above NOAA's 0–9 scale, then, as space allows, the coming peak and the outlook at
 * the saved place. The time of the reading is always shown: the widget only refreshes when the
 * space weather sync runs.
 */
@Composable
internal fun AuroraWidgetContent(state: AuroraWidgetState, title: String, openIntent: Intent?) {
    val size = LocalSize.current
    val roomy = size.width >= WidgetSizes.Wide.width
    val tall = size.height >= WidgetSizes.Large.height
    val numberColor = if (state.isStale) WidgetColors.SecondaryText else kpColor(state.level)

    Column(
        modifier = widgetFrame(openIntent)
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .semantics { contentDescription = state.contentDescription },
    ) {
        Text(text = title, maxLines = 1, style = widgetText(color = WidgetColors.Accent, fontSize = 12.sp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(text = state.kp, maxLines = 1, style = widgetText(color = numberColor, fontSize = 30.sp, bold = true))
            Spacer(GlanceModifier.width(8.dp))
            Text(
                text = state.activity,
                maxLines = 1,
                style = widgetText(color = WidgetColors.SecondaryText, fontSize = 13.sp),
                modifier = GlanceModifier.padding(bottom = 5.dp),
            )
        }
        Spacer(GlanceModifier.height(4.dp))
        KpScale(level = state.level, availableWidth = size.width - 28.dp)
        Spacer(GlanceModifier.height(6.dp))
        if (roomy || tall) {
            state.peak?.let { Text(text = it, maxLines = 1, style = widgetText(fontSize = 13.sp)) }
        }
        if (tall) {
            state.local?.let { Text(text = it, maxLines = 2, style = widgetText(fontSize = 13.sp)) }
        }
        Text(
            text = state.updated,
            maxLines = 1,
            style = widgetText(
                color = if (state.isStale) WidgetColors.Warning else WidgetColors.SecondaryText,
                fontSize = 11.sp,
            ),
        )
    }
}

/** Ten segments, Kp 0 to 9, lit in their colours up to the current level. */
@Composable
private fun KpScale(level: Int, availableWidth: Dp) {
    val gap = 2.dp
    val segment = ((availableWidth - gap * Kp.MAX) / (Kp.MAX + 1)).coerceIn(6.dp, 22.dp)
    Row(modifier = GlanceModifier.fillMaxWidth()) {
        for (kp in 0..Kp.MAX) {
            if (kp > 0) Spacer(GlanceModifier.width(gap))
            Box(
                modifier = GlanceModifier
                    .width(segment)
                    .height(6.dp)
                    .cornerRadius(3.dp)
                    .background(if (kp <= level) kpColor(kp) else WidgetColors.Outline),
                content = {},
            )
        }
    }
}
