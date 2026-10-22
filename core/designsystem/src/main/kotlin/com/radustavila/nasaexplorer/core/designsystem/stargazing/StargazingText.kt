package com.radustavila.nasaexplorer.core.designsystem.stargazing

import android.content.res.Resources
import androidx.annotation.StringRes
import com.radustavila.nasaexplorer.core.designsystem.R
import com.radustavila.nasaexplorer.core.designsystem.units.UnitFormatter
import com.radustavila.nasaexplorer.core.model.MoonDuringWindow
import com.radustavila.nasaexplorer.core.model.ObservingWindow
import com.radustavila.nasaexplorer.core.model.StargazingNight
import com.radustavila.nasaexplorer.core.model.StargazingRating
import kotlin.math.roundToInt

/**
 * How the stargazing outlook is worded, shared by Sky tonight, the Today line and the clear-night
 * notification so all three say the same thing.
 */
object StargazingText {

    /** MET Norway's terms ask for this credit, with a link to [CREDIT_URL], wherever its data shows. */
    const val CREDIT_URL = "https://www.met.no/en"

    /** "Clear from 21:10 to 02:00, Moon down". */
    fun window(resources: Resources, formatter: UnitFormatter, window: ObservingWindow, moonLit: Double): String =
        resources.getString(
            R.string.stargazing_window,
            formatter.time(window.start),
            formatter.time(window.end),
            moon(resources, window.moon, moonLit),
        )

    /** The best window, or why there is none. */
    fun summary(resources: Resources, formatter: UnitFormatter, night: StargazingNight): String {
        val window = night.bestWindow
        return when {
            window != null -> window(resources, formatter, window, night.night.moonIlluminatedFraction)
            night.score != null -> resources.getString(R.string.stargazing_no_window)
            night.night.darkStart == null -> resources.getString(R.string.stargazing_not_dark)
            else -> resources.getString(R.string.stargazing_no_forecast)
        }
    }

    fun moon(resources: Resources, moon: MoonDuringWindow, lit: Double): String {
        val percent = (lit * PERCENT).roundToInt()
        return when (moon) {
            MoonDuringWindow.DOWN -> resources.getString(R.string.stargazing_moon_down)
            MoonDuringWindow.UP -> resources.getString(R.string.stargazing_moon_up, percent)
            MoonDuringWindow.RISES -> resources.getString(R.string.stargazing_moon_rises, percent)
            MoonDuringWindow.SETS -> resources.getString(R.string.stargazing_moon_sets, percent)
        }
    }

    @StringRes
    fun ratingRes(rating: StargazingRating): Int = when (rating) {
        StargazingRating.POOR -> R.string.stargazing_rating_poor
        StargazingRating.FAIR -> R.string.stargazing_rating_fair
        StargazingRating.GOOD -> R.string.stargazing_rating_good
        StargazingRating.EXCELLENT -> R.string.stargazing_rating_excellent
    }

    private const val PERCENT = 100
}
