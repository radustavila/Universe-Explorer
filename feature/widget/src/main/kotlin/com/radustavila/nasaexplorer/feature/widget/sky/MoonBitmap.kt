package com.radustavila.nasaexplorer.feature.widget.sky

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import androidx.core.graphics.createBitmap
import kotlin.math.abs
import kotlin.math.cos

/**
 * Draws the Moon as seen at [phaseAngleDegrees] (0 new, 90 first quarter, 180 full, 270 last
 * quarter): a dim disc with its sunlit part. Widgets cannot draw, so the picture is a small bitmap.
 *
 * The lit part is a half disc on the Sun's side closed by the terminator, a half ellipse whose
 * width follows the cosine of the phase angle. Seen from the southern hemisphere the Moon is upside
 * down, so the waxing Moon is lit on the left instead of the right.
 */
internal fun drawMoon(phaseAngleDegrees: Double, sizePx: Int, southernHemisphere: Boolean = false): Bitmap {
    val bitmap = createBitmap(sizePx, sizePx)
    val canvas = Canvas(bitmap)
    val radius = sizePx / 2f * DISC_FILL
    val centre = sizePx / 2f
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    paint.color = DARK_SIDE
    canvas.drawCircle(centre, centre, radius, paint)

    val angle = ((phaseAngleDegrees % FULL_TURN) + FULL_TURN) % FULL_TURN
    val waxing = angle < HALF_TURN
    // Measured from new moon on the waxing side: 0..180.
    val elongation = if (waxing) angle else FULL_TURN - angle
    val terminatorHalfWidth = (radius * cos(Math.toRadians(elongation))).toFloat()

    val lit = Path().apply {
        val disc = RectF(centre - radius, centre - radius, centre + radius, centre + radius)
        // The bright limb: the right half of the disc, from top to bottom.
        arcTo(disc, -QUARTER_TURN_F, HALF_TURN_F)
        // Back up along the terminator: through the right side while a crescent, the left once gibbous.
        val terminator = RectF(
            centre - abs(terminatorHalfWidth),
            centre - radius,
            centre + abs(terminatorHalfWidth),
            centre + radius,
        )
        arcTo(terminator, QUARTER_TURN_F, if (terminatorHalfWidth > 0) -HALF_TURN_F else HALF_TURN_F)
        close()
    }
    // A waning Moon is lit on the other side; so is a waxing one seen from the south.
    if (waxing == southernHemisphere) canvas.scale(-1f, 1f, centre, centre)
    paint.color = LIT_SIDE
    canvas.drawPath(lit, paint)
    return bitmap
}

private const val DISC_FILL = 0.94f
private const val FULL_TURN = 360.0
private const val HALF_TURN = 180.0
private const val HALF_TURN_F = 180f
private const val QUARTER_TURN_F = 90f
private const val DARK_SIDE = 0xFF2B2B38.toInt()
private const val LIT_SIDE = 0xFFECECF3.toInt()
