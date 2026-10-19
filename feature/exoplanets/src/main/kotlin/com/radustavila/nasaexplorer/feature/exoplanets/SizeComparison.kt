package com.radustavila.nasaexplorer.feature.exoplanets

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.radustavila.nasaexplorer.core.model.ExoplanetUnits

const val SIZE_COMPARISON_TEST_TAG = "size-comparison"

/**
 * Earth, the planet and Jupiter drawn to the same scale, side by side on a common centre line.
 * The largest body fills its third of the width; a body that would be smaller than a few pixels
 * is kept visible as a dot.
 */
@Composable
fun SizeComparison(
    planetName: String,
    radiusEarth: Double,
    description: String,
    modifier: Modifier = Modifier,
) {
    val bodies = listOf(
        Body(
            label = stringResource(R.string.exoplanet_details_earth),
            radiusEarth = 1.0,
            color = EARTH_BLUE,
        ),
        Body(label = planetName, radiusEarth = radiusEarth, color = MaterialTheme.colorScheme.tertiary),
        Body(
            label = stringResource(R.string.exoplanet_details_jupiter),
            radiusEarth = ExoplanetUnits.EARTH_RADII_PER_JUPITER_RADIUS,
            color = JUPITER_OCHRE,
        ),
    )
    Column(
        modifier = modifier
            .testTag(SIZE_COMPARISON_TEST_TAG)
            .semantics(mergeDescendants = true) { contentDescription = description },
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp),
        ) { drawBodies(bodies) }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            bodies.forEach { body ->
                Text(
                    text = body.label,
                    style = MaterialTheme.typography.labelMedium,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

private data class Body(val label: String, val radiusEarth: Double, val color: Color)

private fun DrawScope.drawBodies(bodies: List<Body>) {
    val slotWidth = size.width / bodies.size
    val maxPixelRadius = minOf(size.height, slotWidth) / 2f * FILL
    val largest = bodies.maxOf { it.radiusEarth }
    val minPixelRadius = MIN_DOT.dp.toPx()
    bodies.forEachIndexed { index, body ->
        val radius = (maxPixelRadius * (body.radiusEarth / largest).toFloat()).coerceAtLeast(minPixelRadius)
        drawCircle(
            color = body.color,
            radius = radius,
            center = Offset(x = slotWidth * (index + HALF), y = size.height / 2f),
        )
    }
}

private val EARTH_BLUE = Color(0xFF4F8BD6)
private val JUPITER_OCHRE = Color(0xFFD9A066)
private const val FILL = 0.92f
private const val HALF = 0.5f
private const val MIN_DOT = 2
