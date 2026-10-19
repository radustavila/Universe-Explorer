package com.radustavila.nasaexplorer.feature.spaceweather.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.radustavila.nasaexplorer.core.model.GeomagneticStorm
import com.radustavila.nasaexplorer.core.model.Kp
import com.radustavila.nasaexplorer.feature.spaceweather.R
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

const val KP_GAUGE_TEST_TAG = "kp-gauge"

/**
 * A 240° dial from Kp 0 to 9, one coloured segment per Kp, with a marker at [kp]. The centre says
 * the value, the G-scale storm level when there is one, and what it means.
 */
@Composable
internal fun KpGauge(kp: Double?, modifier: Modifier = Modifier) {
    val level = kp?.let(Kp::level)
    val storm = kp?.let(GeomagneticStorm::forKp)
    val locale = currentLocale()
    val value = kp?.let { String.format(locale, "%.1f", it) } ?: "–"
    val meaning = level?.let { stringResource(kpDescription(it)) } ?: stringResource(R.string.space_weather_kp_unknown)
    val description = stringResource(R.string.space_weather_kp_gauge_description, value, meaning)
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val markerColor = MaterialTheme.colorScheme.onSurface

    Box(
        modifier = modifier
            .aspectRatio(GAUGE_ASPECT_RATIO)
            .testTag(KP_GAUGE_TEST_TAG)
            .clearAndSetSemantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize().padding(8.dp)) {
            val stroke = size.minDimension * STROKE_FRACTION
            // The 240° arc is 2r wide and 1.5r tall (it dips half a radius below its centre).
            val diameter = minOf(size.width, size.height / ARC_HEIGHT_FRACTION) - stroke
            val topLeft = Offset((size.width - diameter) / 2, stroke / 2)
            val arcSize = Size(diameter, diameter)
            val segmentSweep = SWEEP / SEGMENTS
            for (segment in 0 until SEGMENTS) {
                drawArc(
                    color = if (level == null) trackColor else kpColor(segment),
                    startAngle = START_ANGLE + segment * segmentSweep + GAP / 2,
                    sweepAngle = segmentSweep - GAP,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Butt),
                )
            }
            if (kp != null) {
                val angle = Math.toRadians((START_ANGLE + SWEEP * (kp / Kp.MAX).coerceIn(0.0, 1.0)).toDouble())
                val radius = diameter / 2
                val center = Offset(topLeft.x + radius, topLeft.y + radius)
                val marker = Offset(
                    (center.x + radius * cos(angle)).toFloat(),
                    (center.y + radius * sin(angle)).toFloat(),
                )
                drawCircle(color = markerColor, radius = stroke * 0.75f, center = marker)
                drawCircle(color = kpColor(level ?: 0), radius = stroke * 0.45f, center = marker)
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(top = 24.dp)) {
            Text(text = stringResource(R.string.space_weather_kp_label), style = MaterialTheme.typography.labelLarge)
            Text(text = value, style = MaterialTheme.typography.displayMedium)
            Text(
                text = storm?.let { stringResource(R.string.space_weather_storm_level, it.name, meaning) } ?: meaning,
                style = MaterialTheme.typography.titleSmall,
                color = level?.takeIf { storm != null }?.let(::kpColor) ?: MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

private const val SEGMENTS = 10
private const val START_ANGLE = 150f
private const val SWEEP = 240f
private const val GAP = 2f
private const val STROKE_FRACTION = 0.07f
private const val ARC_HEIGHT_FRACTION = 0.75f
private const val GAUGE_ASPECT_RATIO = 1.33f
