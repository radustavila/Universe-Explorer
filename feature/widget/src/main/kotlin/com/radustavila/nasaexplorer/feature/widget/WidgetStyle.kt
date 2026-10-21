package com.radustavila.nasaexplorer.feature.widget

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider

/**
 * The app is always dark, so its widgets are too, whatever the launcher's theme. The values mirror
 * the app's palette in `:core:designsystem`, which keeps them internal to Material.
 */
internal object WidgetColors {
    val Background = Color(0xFF07070D)
    val Card = Color(0xFF16161F)
    val Outline = Color(0xFF2B2B38)
    val Scrim = Color(0x99000000)
    val PrimaryText = Color(0xFFFFFFFF)
    val SecondaryText = Color(0xFFB8C0D9)
    val Accent = Color(0xFFCFC2FF)
    val Warning = Color(0xFFFFB74D)
}

/**
 * The size buckets the data widgets lay themselves out for (see `SizeMode.Responsive`): a 2x2 cell
 * square, the same stretched wide, and wide with room for extra lines.
 */
internal object WidgetSizes {
    val Small = DpSize(110.dp, 110.dp)
    val Wide = DpSize(220.dp, 110.dp)
    val Large = DpSize(220.dp, 180.dp)

    val all = setOf(Small, Wide, Large)
}

internal fun widgetText(
    color: Color = WidgetColors.PrimaryText,
    fontSize: TextUnit = 13.sp,
    bold: Boolean = false,
    textAlign: TextAlign? = null,
) = TextStyle(
    color = ColorProvider(color),
    fontSize = fontSize,
    fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
    textAlign = textAlign,
)

/** Rounded dark card filling the widget, opening [openIntent] when tapped. */
internal fun widgetFrame(openIntent: Intent?): GlanceModifier {
    val frame = GlanceModifier
        .fillMaxSize()
        .cornerRadius(16.dp)
        .background(WidgetColors.Background)
    return if (openIntent != null) frame.clickable(actionStartActivity(openIntent)) else frame
}

/** What a widget shows while its source has nothing cached, e.g. before the first sync. */
internal class WidgetMessageState(
    val message: String,
    val openIntent: Intent?,
)

@Composable
internal fun WidgetMessage(state: WidgetMessageState) {
    Box(
        modifier = widgetFrame(state.openIntent)
            .padding(16.dp)
            .semantics { contentDescription = state.message },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = state.message,
            style = widgetText(fontSize = 14.sp, textAlign = TextAlign.Center),
        )
    }
}
