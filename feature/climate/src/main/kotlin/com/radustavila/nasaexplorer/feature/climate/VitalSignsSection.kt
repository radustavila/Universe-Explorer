package com.radustavila.nasaexplorer.feature.climate

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.radustavila.nasaexplorer.core.designsystem.chart.Sparkline
import com.radustavila.nasaexplorer.core.designsystem.component.SectionTitle
import com.radustavila.nasaexplorer.core.designsystem.units.rememberUnitFormatter
import com.radustavila.nasaexplorer.core.model.ClimateIndicator
import com.radustavila.nasaexplorer.core.model.ClimateSeries

const val VITAL_SIGNS_TEST_TAG = "vital-signs"

/** Test tag of one indicator's card. */
fun vitalSignCardTag(indicator: ClimateIndicator) = "vital-sign-${indicator.key}"

/**
 * The Earth tab's "Vital signs" (F26): CO₂, global temperature, sea level and Arctic sea ice, each
 * a card with its latest value, the change since 1980, the date of that value and a sparkline.
 * [onOpen] opens an indicator's full chart. Works offline from the last weekly download.
 */
@Composable
fun VitalSignsSection(
    onOpen: (ClimateIndicator) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: VitalSignsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    VitalSignsContent(uiState, onOpen, modifier)
}

@Composable
internal fun VitalSignsContent(
    uiState: VitalSignsUiState,
    onOpen: (ClimateIndicator) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!uiState.isLoaded) return
    Column(modifier.testTag(VITAL_SIGNS_TEST_TAG), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle(stringResource(R.string.climate_section_title))
        if (uiState.series.isEmpty()) {
            Text(
                text = if (uiState.freshness.isOffline) {
                    stringResource(R.string.climate_section_offline)
                } else {
                    stringResource(R.string.climate_section_loading)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }
        BoxWithConstraints {
            val columns = if (maxWidth >= WIDE_ROW) ClimateIndicator.entries.size else 2
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                uiState.series.chunked(columns).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { series ->
                            VitalSignCard(series, onClick = { onOpen(series.indicator) }, Modifier.weight(1f))
                        }
                        repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
        Text(
            text = stringResource(R.string.climate_section_sources),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun VitalSignCard(series: ClimateSeries, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val latest = series.latest ?: return
    val units = rememberUnitFormatter()
    val format = remember(series.indicator, units) { ClimateValueFormat(series.indicator, units) }
    val data = remember(series, format) { format.chartData(series) }
    val ppm = rememberPpm()
    Card(onClick = onClick, modifier = modifier.testTag(vitalSignCardTag(series.indicator))) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = stringResource(series.indicator.texts.title),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = format.value(latest.value, ppm),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            series.change()?.let { change ->
                Text(
                    text = stringResource(
                        R.string.climate_change_since,
                        format.change(change.delta, ppm),
                        change.sinceYear,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                )
            }
            Sparkline(data, Modifier.fillMaxWidth().height(40.dp).padding(vertical = 4.dp))
            Text(
                text = stringResource(R.string.climate_last_value, units.month(latest.date)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private val WIDE_ROW = 720.dp
