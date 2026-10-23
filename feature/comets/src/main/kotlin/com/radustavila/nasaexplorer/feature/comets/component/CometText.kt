package com.radustavila.nasaexplorer.feature.comets.component

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.radustavila.nasaexplorer.core.designsystem.units.UnitFormatter
import com.radustavila.nasaexplorer.core.model.CometLocalSky
import com.radustavila.nasaexplorer.core.model.CometTonight
import com.radustavila.nasaexplorer.core.model.CometVisibility
import com.radustavila.nasaexplorer.feature.comets.R
import kotlin.math.roundToInt

/*
 * The comets' shared wording. Times and numbers go through [UnitFormatter]; degrees and au are
 * astronomical and stay as they are.
 */

/** Predictions are rough, so magnitudes are shown whole. */
internal fun UnitFormatter.magnitude(value: Double): String = number(value.roundToInt().toDouble(), 0)

internal fun UnitFormatter.au(value: Double): String = number(value, 2)

@StringRes
internal fun CometVisibility.labelRes(): Int = when (this) {
    CometVisibility.NAKED_EYE -> R.string.comets_visibility_naked_eye
    CometVisibility.BINOCULARS -> R.string.comets_visibility_binoculars
    CometVisibility.TELESCOPE -> R.string.comets_visibility_telescope
    CometVisibility.TOO_FAINT -> R.string.comets_visibility_too_faint
}

/** "Predicted magnitude 8 · binoculars". */
@Composable
internal fun magnitudeLine(comet: CometTonight, units: UnitFormatter): String = comet.magnitude?.let {
    stringResource(R.string.comets_magnitude_line, units.magnitude(it), stringResource(comet.visibility.labelRes()))
} ?: stringResource(R.string.comets_magnitude_unknown)

/** "Best at 19:40, 61° up in the north-west", or that it does not get high enough in the dark. */
@Composable
internal fun bestLine(sky: CometLocalSky, units: UnitFormatter): String {
    val bestTime = sky.bestTime
    val best = sky.best
    return if (bestTime != null && best != null) {
        stringResource(
            R.string.comets_best,
            units.time(bestTime),
            best.altitudeDegrees.roundToInt(),
            stringResource(compassPointRes(best.azimuthDegrees)),
        )
    } else {
        stringResource(R.string.comets_not_visible)
    }
}

/** "Rises 20:10 · Sets 05:40" in time order, or that it stays up or down all day. */
@Composable
internal fun riseSetLine(sky: CometLocalSky, units: UnitFormatter): String {
    val rise = sky.rise
    val set = sky.set
    return when {
        sky.isAlwaysUp -> stringResource(R.string.comets_always_up)
        rise == null && set == null -> stringResource(R.string.comets_never_rises)
        else -> listOfNotNull(
            rise?.let { it to stringResource(R.string.comets_rises_at, units.time(it)) },
            set?.let { it to stringResource(R.string.comets_sets_at, units.time(it)) },
        ).sortedBy { it.first }.joinToString(stringResource(R.string.comets_separator)) { it.second }
    }
}

private val COMPASS = intArrayOf(
    R.string.comets_compass_n, R.string.comets_compass_ne, R.string.comets_compass_e, R.string.comets_compass_se,
    R.string.comets_compass_s, R.string.comets_compass_sw, R.string.comets_compass_w, R.string.comets_compass_nw,
)

private const val COMPASS_SECTOR = 45.0
private const val FULL_TURN = 360.0

@StringRes
internal fun compassPointRes(azimuth: Double): Int {
    val normalized = ((azimuth % FULL_TURN) + FULL_TURN) % FULL_TURN
    return COMPASS[((normalized + COMPASS_SECTOR / 2) / COMPASS_SECTOR).toInt() % COMPASS.size]
}
