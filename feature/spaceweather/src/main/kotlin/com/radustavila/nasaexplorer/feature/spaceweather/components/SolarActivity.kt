package com.radustavila.nasaexplorer.feature.spaceweather.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Cyclone
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.radustavila.nasaexplorer.core.designsystem.component.SectionTitle
import com.radustavila.nasaexplorer.core.designsystem.util.formatDateTime
import com.radustavila.nasaexplorer.core.model.CoronalMassEjection
import com.radustavila.nasaexplorer.core.model.SolarFlare
import com.radustavila.nasaexplorer.feature.spaceweather.R
import kotlin.math.roundToInt

const val SOLAR_FLARE_TEST_TAG = "solar-flare"
const val CME_TEST_TAG = "cme"

@Composable
internal fun SolarFlareCard(flare: SolarFlare?, modifier: Modifier = Modifier) {
    SolarEventCard(
        icon = Icons.Outlined.Bolt,
        title = stringResource(R.string.space_weather_flare_title),
        modifier = modifier.testTag(SOLAR_FLARE_TEST_TAG),
    ) {
        if (flare == null) {
            Text(stringResource(R.string.space_weather_flare_none), style = MaterialTheme.typography.bodyMedium)
            return@SolarEventCard
        }
        Text(
            text = stringResource(R.string.space_weather_flare_class, flare.classType),
            style = MaterialTheme.typography.headlineSmall,
            color = flareColor(flare.classLetter),
        )
        Text(
            text = stringResource(R.string.space_weather_flare_peak, (flare.peakTime ?: flare.beginTime).formatDateTime()),
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = stringResource(flareMeaning(flare.classLetter)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val region = listOfNotNull(
            flare.activeRegion?.let { stringResource(R.string.space_weather_flare_region, it) },
            flare.sourceLocation,
        ).joinToString(" · ")
        if (region.isNotEmpty()) {
            Text(region, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun CmeCard(cme: CoronalMassEjection?, modifier: Modifier = Modifier) {
    SolarEventCard(
        icon = Icons.Outlined.Cyclone,
        title = stringResource(R.string.space_weather_cme_title),
        modifier = modifier.testTag(CME_TEST_TAG),
    ) {
        if (cme == null) {
            Text(stringResource(R.string.space_weather_cme_none), style = MaterialTheme.typography.bodyMedium)
            return@SolarEventCard
        }
        Text(
            text = stringResource(R.string.space_weather_cme_started, cme.startTime.formatDateTime()),
            style = MaterialTheme.typography.bodyMedium,
        )
        cme.speedKmPerSecond?.let { speed ->
            Text(
                text = stringResource(R.string.space_weather_cme_speed, speed.roundToInt()),
                style = MaterialTheme.typography.headlineSmall,
            )
        }
        Text(
            text = when {
                cme.earthArrival != null && cme.isEarthGlancingBlow ->
                    stringResource(R.string.space_weather_cme_glancing, cme.earthArrival!!.formatDateTime())

                cme.earthArrival != null ->
                    stringResource(R.string.space_weather_cme_arrival, cme.earthArrival!!.formatDateTime())

                else -> stringResource(R.string.space_weather_cme_misses)
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (cme.earthArrival != null) Color(0xFFFFC107) else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SolarEventCard(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                SectionTitle(title)
            }
            content()
        }
    }
}

private fun flareColor(letter: Char?): Color = when (letter) {
    'X' -> Color(0xFFFF5252)
    'M' -> Color(0xFFFFB74D)
    else -> Color(0xFFFFEB3B)
}

private fun flareMeaning(letter: Char?): Int = when (letter) {
    'X' -> R.string.space_weather_flare_x
    'M' -> R.string.space_weather_flare_m
    else -> R.string.space_weather_flare_minor
}
