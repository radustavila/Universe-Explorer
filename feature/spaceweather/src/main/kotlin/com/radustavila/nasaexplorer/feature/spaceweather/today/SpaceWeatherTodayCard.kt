package com.radustavila.nasaexplorer.feature.spaceweather.today

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.radustavila.nasaexplorer.core.designsystem.component.SectionTitle
import com.radustavila.nasaexplorer.core.designsystem.units.rememberUnitFormatter
import com.radustavila.nasaexplorer.core.model.Kp
import com.radustavila.nasaexplorer.core.model.KpReading
import com.radustavila.nasaexplorer.feature.spaceweather.R
import com.radustavila.nasaexplorer.feature.spaceweather.components.currentLocale
import com.radustavila.nasaexplorer.feature.spaceweather.components.kpColor
import com.radustavila.nasaexplorer.feature.spaceweather.components.kpDescription
import java.text.NumberFormat

const val SPACE_WEATHER_TODAY_CARD_TEST_TAG = "space-weather-today-card"

/**
 * "Space weather" for the Today screen: the Kp index now, what it means for the aurora, and a
 * stronger Kp the forecast expects within a day. Offline it shows the cached reading, or says that
 * none has been downloaded yet.
 */
@Composable
fun SpaceWeatherTodayCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SpaceWeatherTodayViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    SpaceWeatherTodayCardContent(uiState = uiState, onClick = onClick, modifier = modifier)
}

@Composable
internal fun SpaceWeatherTodayCardContent(
    uiState: SpaceWeatherTodayUiState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!uiState.isLoaded) return

    Card(onClick = onClick, modifier = modifier.fillMaxWidth().testTag(SPACE_WEATHER_TODAY_CARD_TEST_TAG)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 16.dp, bottom = 16.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.WbSunny,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    SectionTitle(stringResource(R.string.space_weather_title))
                }
                val kp = uiState.kp
                if (kp == null) {
                    Text(
                        text = stringResource(R.string.space_weather_today_empty),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    KpLine(kp)
                }
                uiState.peak?.let { PeakLine(it) }
            }
            Icon(
                Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = stringResource(R.string.space_weather_today_open),
            )
        }
    }
}

@Composable
private fun KpLine(kp: Double) {
    val level = Kp.level(kp)
    val locale = currentLocale()
    val value = NumberFormat.getNumberInstance(locale).apply {
        minimumFractionDigits = 1
        maximumFractionDigits = 1
    }.format(kp)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(12.dp).background(kpColor(level), CircleShape))
        Spacer(Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.space_weather_today_kp, value, stringResource(kpDescription(level))),
            style = MaterialTheme.typography.titleSmall,
        )
    }
}

@Composable
private fun PeakLine(peak: KpReading) {
    Text(
        text = stringResource(
            R.string.space_weather_today_peak,
            peak.level,
            stringResource(kpDescription(peak.level)),
            rememberUnitFormatter().dayAndTime(peak.start),
        ),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
