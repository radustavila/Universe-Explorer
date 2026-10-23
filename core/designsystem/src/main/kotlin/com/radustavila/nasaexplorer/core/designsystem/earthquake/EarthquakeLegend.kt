package com.radustavila.nasaexplorer.core.designsystem.earthquake

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.radustavila.nasaexplorer.core.model.EarthquakeDepth

const val EARTHQUAKE_LEGEND_TEST_TAG = "earthquake-legend"

/** The depth colours, in a small caption over the map like its attribution. */
@Composable
fun EarthquakeDepthLegend(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .testTag(EARTHQUAKE_LEGEND_TEST_TAG)
            .padding(8.dp)
            .background(Color.Black.copy(alpha = SCRIM_ALPHA), MaterialTheme.shapes.small)
            .padding(horizontal = 6.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        EarthquakeDepth.entries.forEach { depth ->
            Box(Modifier.size(8.dp).background(depth.color, CircleShape))
            Text(
                text = stringResource(EarthquakeText.depthLegendRes(depth)),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                modifier = Modifier.padding(end = 4.dp),
            )
        }
    }
}

private const val SCRIM_ALPHA = 0.55f
