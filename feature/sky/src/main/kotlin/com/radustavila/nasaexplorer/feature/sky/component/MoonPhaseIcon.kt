package com.radustavila.nasaexplorer.feature.sky.component

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.scale
import kotlin.math.abs
import kotlin.math.cos

/**
 * The Moon's disc with its lit part, from the phase angle (0 new, 180 full). As seen from the
 * northern hemisphere a waxing Moon is lit on the right; [mirrored] flips it for the southern one.
 */
@Composable
internal fun MoonPhaseIcon(
    phaseAngleDegrees: Double,
    modifier: Modifier = Modifier,
    mirrored: Boolean = false,
    darkColor: Color = Color(DARK_SIDE),
    litColor: Color = Color(LIT_SIDE),
) {
    Canvas(modifier = modifier) {
        val radius = size.minDimension / 2
        val center = Offset(size.width / 2, size.height / 2)
        drawCircle(color = darkColor, radius = radius, center = center)

        val phase = ((phaseAngleDegrees % FULL_TURN) + FULL_TURN) % FULL_TURN
        val waxing = phase < HALF_TURN
        // Half-width of the terminator ellipse: a full radius at new and full Moon, zero at quarters.
        val terminator = radius * abs(cos(Math.toRadians(phase))).toFloat()
        val gibbous = phase in QUARTER..(FULL_TURN - QUARTER)
        val disc = Rect(center, radius)
        val ellipse = Rect(center.x - terminator, center.y - radius, center.x + terminator, center.y + radius)

        // Lit limb on the right (waxing), then back along the terminator: bulging into the dark half
        // for a gibbous Moon, into the lit half for a crescent.
        val lit = Path().apply {
            arcTo(disc, startAngleDegrees = -90f, sweepAngleDegrees = 180f, forceMoveTo = true)
            arcTo(ellipse, startAngleDegrees = 90f, sweepAngleDegrees = if (gibbous) 180f else -180f, forceMoveTo = false)
            close()
        }
        val flip = waxing == mirrored
        scale(scaleX = if (flip) -1f else 1f, scaleY = 1f, pivot = center) {
            drawPath(lit, color = litColor)
        }
    }
}

private const val FULL_TURN = 360.0
private const val HALF_TURN = 180.0
private const val QUARTER = 90.0
private const val DARK_SIDE = 0xFF2B2F3A
private const val LIT_SIDE = 0xFFF2EBD3
