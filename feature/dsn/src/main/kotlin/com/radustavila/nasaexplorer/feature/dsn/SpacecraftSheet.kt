package com.radustavila.nasaexplorer.feature.dsn

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.radustavila.nasaexplorer.core.designsystem.component.SectionTitle
import com.radustavila.nasaexplorer.core.model.DsnLink
import com.radustavila.nasaexplorer.core.model.DsnSignal
import com.radustavila.nasaexplorer.core.model.DsnSiteLink

const val DSN_SPACECRAFT_SHEET_TEST_TAG = "dsn-spacecraft-sheet"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SpacecraftSheet(siteLink: DsnSiteLink, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, modifier = Modifier.testTag(DSN_SPACECRAFT_SHEET_TEST_TAG)) {
        SpacecraftDetails(siteLink, Modifier.padding(start = 24.dp, end = 24.dp, bottom = 32.dp))
    }
}

/** Distance, light time, data rates and the mission's one-line note. */
@Composable
internal fun SpacecraftDetails(siteLink: DsnSiteLink, modifier: Modifier = Modifier) {
    val (site, dish, link) = siteLink
    val format = rememberDsnFormatter()
    val unknown = stringResource(R.string.dsn_sheet_unknown)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            SectionTitle(link.spacecraft.displayName, style = MaterialTheme.typography.titleLarge)
            Text(
                text = stringResource(R.string.dsn_sheet_via, dishLabel(dish.name), site.name),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        missionNoteRes(link.spacecraft.code)?.let { note ->
            Text(stringResource(note), style = MaterialTheme.typography.bodyMedium)
        }
        Fact(stringResource(R.string.dsn_sheet_distance), link.distanceKm?.let(format::distance) ?: unknown)
        Fact(
            stringResource(R.string.dsn_sheet_light_time),
            link.oneWayLightTime?.let { stringResource(R.string.dsn_sheet_light_time_value, format.lightTime(it)) }
                ?: unknown,
        )
        Fact(
            stringResource(R.string.dsn_sheet_downlink),
            signalText(link.downSignals, link.downlinkRateBitsPerSecond, format),
        )
        if (link.upSignals.isNotEmpty()) {
            Fact(
                stringResource(R.string.dsn_sheet_uplink),
                signalText(link.upSignals, link.uplinkRateBitsPerSecond, format),
            )
        }
        if (link.distanceSource == DsnLink.DistanceSource.HORIZONS) {
            Text(
                text = stringResource(R.string.dsn_sheet_horizons),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** "160 bit/s · X band", "carrier only · S band" or "off". */
@Composable
private fun signalText(signals: List<DsnSignal>, rate: Double?, format: DsnFormatter): String {
    val active = signals.filter(DsnSignal::isActive)
    if (active.isEmpty()) return stringResource(R.string.dsn_sheet_off)
    val bands = active.mapNotNull(DsnSignal::band).distinct().map { stringResource(R.string.dsn_sheet_band, it) }
    val speed = rate?.let(format::dataRate) ?: stringResource(R.string.dsn_sheet_carrier_only)
    return (listOf(speed) + bands).joinToString(" · ")
}

@Composable
private fun Fact(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(text = value, style = MaterialTheme.typography.bodyLarge)
    }
}
