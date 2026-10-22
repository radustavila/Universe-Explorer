package com.radustavila.nasaexplorer.core.skymap

import kotlin.math.log2

/**
 * How bright and how coloured a star is drawn. Colours follow the B-V index (hot blue-white stars
 * near -0.3, the Sun at 0.65, cool red giants above 1.4), muted the way the eye sees them; sizes and
 * the faintest magnitude shown follow the zoom, so a wide view is not a grey haze.
 */
@Suppress("MagicNumber")
object StarStyle {

    /** Upper B-V bound of each colour bucket; the last bucket takes everything redder. */
    private val COLOUR_BOUNDS = floatArrayOf(-0.15f, 0.15f, 0.45f, 0.7f, 1.0f, 1.35f)

    /** Opaque RGB per bucket, blue-white to orange-red; the extra last one is for unknown colours. */
    private val COLOURS = intArrayOf(
        0xFFAFC4FF.toInt(),
        0xFFCCD8FF.toInt(),
        0xFFF1F2FF.toInt(),
        0xFFFFF4E6.toInt(),
        0xFFFFE6C8.toInt(),
        0xFFFFD2A1.toInt(),
        0xFFFFB98A.toInt(),
        0xFFFFFFFF.toInt(),
    )

    val COLOUR_BUCKETS: Int = COLOURS.size

    fun colourBucket(colourIndex: Float): Int = when {
        colourIndex.isNaN() -> COLOURS.size - 1
        else -> COLOUR_BOUNDS.indexOfFirst { colourIndex < it }.takeIf { it >= 0 } ?: COLOUR_BOUNDS.size
    }

    fun colour(bucket: Int): Int = COLOURS[bucket]

    /** Faintest magnitude drawn: every star of the catalogue at 30 degrees or less, about 4.8 across 160. */
    fun magnitudeLimit(fieldOfViewDegrees: Double): Float =
        (6.5 - 0.7 * log2(fieldOfViewDegrees / 30.0)).coerceIn(4.5, 6.5).toFloat()

    /** Dot radius in dp for a star of [magnitude] when stars down to [limit] are drawn. */
    fun radiusDp(magnitude: Float, limit: Float): Float =
        (0.7f + 0.55f * (limit - magnitude)).coerceIn(0.7f, 4.2f)

    /** 0 to 1: the faintest drawn stars fade towards the sky colour. */
    fun alpha(magnitude: Float, limit: Float): Float = (0.45f + 0.3f * (limit - magnitude)).coerceIn(0.45f, 1f)
}
