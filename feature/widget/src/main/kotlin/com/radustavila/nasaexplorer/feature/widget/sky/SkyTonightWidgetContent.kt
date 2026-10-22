package com.radustavila.nasaexplorer.feature.widget.sky

import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.Text
import com.radustavila.nasaexplorer.feature.widget.WidgetColors
import com.radustavila.nasaexplorer.feature.widget.WidgetSizes
import com.radustavila.nasaexplorer.feature.widget.widgetFrame
import com.radustavila.nasaexplorer.feature.widget.widgetText

/** The Moon's phase beside its name, then, as space allows, the next full moon and ISS pass. */
@Composable
internal fun SkyTonightWidgetContent(state: SkyTonightWidgetState, title: String, moon: Bitmap, openIntent: Intent?) {
    val size = LocalSize.current
    val roomy = WidgetSizes.isWide(size)
    val tall = WidgetSizes.isTall(size)

    Column(
        modifier = widgetFrame(openIntent)
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .semantics { contentDescription = state.contentDescription },
    ) {
        val heading = if (tall && state.placeName != null) "$title · ${state.placeName}" else title
        Text(text = heading, maxLines = 1, style = widgetText(color = WidgetColors.Accent, fontSize = 12.sp))
        Spacer(GlanceModifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                provider = ImageProvider(moon),
                // The phase is spelled out next to it and in the widget's description.
                contentDescription = null,
                modifier = GlanceModifier.size(if (roomy) 56.dp else 44.dp),
            )
            Spacer(GlanceModifier.width(10.dp))
            Column {
                Text(text = state.phase, maxLines = 2, style = widgetText(fontSize = 14.sp, bold = true))
                Text(
                    text = state.illumination,
                    maxLines = 1,
                    style = widgetText(color = WidgetColors.SecondaryText, fontSize = 12.sp),
                )
            }
        }
        // Its own line, as wide as the widget: beside the Moon it would not fit a 2x2 square.
        if (roomy || tall) {
            Text(
                text = state.nextFullMoon,
                maxLines = 1,
                style = widgetText(color = WidgetColors.SecondaryText, fontSize = 12.sp),
                modifier = GlanceModifier.padding(top = 4.dp),
            )
        }
        Spacer(GlanceModifier.height(if (tall) 12.dp else 6.dp))
        Text(
            text = state.iss,
            maxLines = 2,
            style = widgetText(
                color = if (state.hasIssPass) WidgetColors.PrimaryText else WidgetColors.SecondaryText,
                fontSize = 12.sp,
            ),
        )
    }
}
