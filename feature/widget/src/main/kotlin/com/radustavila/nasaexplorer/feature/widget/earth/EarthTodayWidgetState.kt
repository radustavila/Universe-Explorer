package com.radustavila.nasaexplorer.feature.widget.earth

import android.content.res.Resources
import com.radustavila.nasaexplorer.core.model.EpicImage
import com.radustavila.nasaexplorer.feature.widget.R
import com.radustavila.nasaexplorer.feature.widget.WidgetFormat
import java.time.Instant

/** What the Earth today widget shows, apart from the picture itself. */
internal data class EarthTodayWidgetState(
    /** "Earth · Tue 21 Oct 12:34": when DSCOVR took the photo, in the device's zone. */
    val caption: String,
    /** EPIC's own caption with the capture time, for TalkBack. */
    val pictureDescription: String,
)

/** The newest photo of the newest cached day, or null when nothing is cached. */
internal fun List<EpicImage>.newestPhoto(): EpicImage? = maxByOrNull { it.capturedAt }

internal fun earthTodayWidgetState(
    photo: EpicImage,
    now: Instant,
    format: WidgetFormat,
    resources: Resources,
): EarthTodayWidgetState {
    val taken = format.dayAndTime(photo.capturedAt, now)
    return EarthTodayWidgetState(
        caption = resources.getString(R.string.earth_widget_caption, taken),
        pictureDescription = resources.getString(R.string.earth_widget_picture_description, photo.caption, taken),
    )
}
