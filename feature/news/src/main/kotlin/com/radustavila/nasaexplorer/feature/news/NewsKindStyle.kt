package com.radustavila.nasaexplorer.feature.news

import android.text.format.DateUtils
import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Newspaper
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.SmartDisplay
import androidx.compose.ui.graphics.vector.ImageVector
import com.radustavila.nasaexplorer.core.model.NewsKind
import java.time.Instant

/** The kind as a row's label ("Science"). */
@get:StringRes
internal val NewsKind.labelRes: Int
    get() = when (this) {
        NewsKind.STORY -> R.string.news_kind_story
        NewsKind.SCIENCE -> R.string.news_kind_science
        NewsKind.IMAGE_OF_THE_DAY -> R.string.news_kind_image_of_the_day
        NewsKind.VIDEO -> R.string.news_kind_video
    }

/** The kind as a filter chip ("Stories"). */
@get:StringRes
internal val NewsKind.chipRes: Int
    get() = when (this) {
        NewsKind.STORY -> R.string.news_filter_stories
        NewsKind.SCIENCE -> R.string.news_filter_science
        NewsKind.IMAGE_OF_THE_DAY -> R.string.news_filter_image_of_the_day
        NewsKind.VIDEO -> R.string.news_filter_videos
    }

internal val NewsKind.icon: ImageVector
    get() = when (this) {
        NewsKind.STORY -> Icons.Outlined.Newspaper
        NewsKind.SCIENCE -> Icons.Outlined.Science
        NewsKind.IMAGE_OF_THE_DAY -> Icons.Outlined.Image
        NewsKind.VIDEO -> Icons.Outlined.SmartDisplay
    }

/** "5 min. ago", "3 hours ago", "Oct 19" (localised by the system), relative to [now]. */
internal fun Instant.formatAge(now: Instant): String =
    DateUtils.getRelativeTimeSpanString(
        toEpochMilli(),
        maxOf(now.toEpochMilli(), toEpochMilli()),
        DateUtils.MINUTE_IN_MILLIS,
        DateUtils.FORMAT_ABBREV_RELATIVE,
    ).toString()
