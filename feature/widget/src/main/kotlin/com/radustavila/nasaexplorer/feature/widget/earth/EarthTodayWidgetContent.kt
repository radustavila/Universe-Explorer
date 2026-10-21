package com.radustavila.nasaexplorer.feature.widget.earth

import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import com.radustavila.nasaexplorer.feature.widget.WidgetColors
import com.radustavila.nasaexplorer.feature.widget.widgetFrame
import com.radustavila.nasaexplorer.feature.widget.widgetText

/**
 * The whole-Earth disc fitted into the widget on the app's black, with when it was taken below.
 * EPIC's photos are already a disc on black, so fitting them leaves no visible edge.
 */
@Composable
internal fun EarthTodayWidgetContent(state: EarthTodayWidgetState, picture: Bitmap?, openIntent: Intent?) {
    Column(
        modifier = widgetFrame(openIntent).padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
            contentAlignment = Alignment.Center,
        ) {
            if (picture != null) {
                Image(
                    provider = ImageProvider(picture),
                    contentDescription = state.pictureDescription,
                    contentScale = ContentScale.Fit,
                    modifier = GlanceModifier.fillMaxSize(),
                )
            } else {
                // Offline with the photo never downloaded: the caption still says which one it is.
                Text(
                    text = state.pictureDescription,
                    maxLines = 4,
                    style = widgetText(
                        color = WidgetColors.SecondaryText,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                    ),
                )
            }
        }
        Text(
            text = state.caption,
            maxLines = 1,
            style = widgetText(color = WidgetColors.SecondaryText, fontSize = 11.sp, textAlign = TextAlign.Center),
        )
    }
}
