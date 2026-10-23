package com.radustavila.nasaexplorer.core.designsystem.podcast

import androidx.annotation.StringRes
import com.radustavila.nasaexplorer.core.designsystem.R
import com.radustavila.nasaexplorer.core.model.PodcastShow

/** Each show's own title, for the Podcasts tab, the player and its lock-screen notification. */
@get:StringRes
val PodcastShow.nameRes: Int
    get() = when (this) {
        PodcastShow.HOUSTON_WE_HAVE_A_PODCAST -> R.string.podcast_show_houston
        PodcastShow.CURIOUS_UNIVERSE -> R.string.podcast_show_curious_universe
        PodcastShow.SMALL_STEPS_GIANT_LEAPS -> R.string.podcast_show_small_steps
    }
