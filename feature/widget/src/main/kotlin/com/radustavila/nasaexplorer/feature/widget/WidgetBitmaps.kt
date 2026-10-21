package com.radustavila.nasaexplorer.feature.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidgetManager
import kotlin.math.roundToInt

/**
 * Pixel size to decode a picture at for a placed widget, over every size the launcher reported for
 * it, within [minPx]..[maxPx]: its longer side when the picture is cropped to fill the widget, its
 * shorter side when it is fitted inside.
 *
 * A widget's bitmaps travel to the launcher in one binder transaction of about a megabyte, so a
 * picture is never decoded larger than the widget can show, and [maxPx] keeps even a tablet-sized
 * widget well under that budget.
 */
internal suspend fun Context.widgetPictureSizePx(id: GlanceId, cropped: Boolean, minPx: Int, maxPx: Int): Int {
    val sizes = GlanceAppWidgetManager(this).getAppWidgetSizes(id)
    val sideDp = sizes.maxOfOrNull { size ->
        if (cropped) maxOf(size.width.value, size.height.value) else minOf(size.width.value, size.height.value)
    } ?: return maxPx
    return (sideDp * resources.displayMetrics.density).roundToInt().coerceIn(minPx, maxPx)
}
