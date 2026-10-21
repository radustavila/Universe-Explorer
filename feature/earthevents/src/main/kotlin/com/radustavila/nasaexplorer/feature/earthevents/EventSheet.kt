package com.radustavila.nasaexplorer.feature.earthevents

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.SatelliteAlt
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.radustavila.nasaexplorer.core.designsystem.component.SectionTitle
import com.radustavila.nasaexplorer.core.designsystem.units.UnitFormatter
import com.radustavila.nasaexplorer.core.designsystem.units.formatLocalDateTime
import com.radustavila.nasaexplorer.core.designsystem.units.rememberUnitFormatter
import com.radustavila.nasaexplorer.core.model.EarthEvent
import com.radustavila.nasaexplorer.core.model.EventMagnitude
import java.util.Locale

const val EARTH_EVENT_SHEET_TEST_TAG = "earth-event-sheet"
const val EARTH_EVENT_SHOW_IMAGERY_TEST_TAG = "earth-event-show-imagery"
const val EARTH_EVENT_SOURCE_TEST_TAG = "earth-event-source"

/** What is known about the selected event, floating over the bottom of the map. */
@Composable
internal fun EventSheet(
    event: EarthEvent,
    isShowingImagery: Boolean,
    isLoadingImagery: Boolean,
    onShowImagery: () -> Unit,
    onHideImagery: () -> Unit,
    onOpenSource: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.testTag(EARTH_EVENT_SHEET_TEST_TAG),
        shape = MaterialTheme.shapes.extraLarge,
        tonalElevation = 3.dp,
        shadowElevation = 6.dp,
    ) {
        Column(
            modifier = Modifier
                .heightIn(max = SHEET_MAX_HEIGHT)
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = event.category.icon,
                    contentDescription = null,
                    tint = event.category.color,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = stringResource(event.category.labelRes),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onClose) {
                    Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.earth_events_close_event))
                }
            }
            SectionTitle(
                text = event.title,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(end = 12.dp),
            )
            Detail(stringResource(R.string.earth_events_last_update, event.lastUpdate.formatLocalDateTime()))
            event.latest.magnitude?.let { magnitude ->
                Detail(stringResource(R.string.earth_events_magnitude, magnitude.formatted(rememberUnitFormatter())))
            }
            if (event.track.size >= 2) {
                val first = event.geometry.first().date.formatLocalDateTime()
                Detail(pluralStringResource(R.plurals.earth_events_track, event.track.size, event.track.size, first))
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 6.dp, end = 12.dp),
            ) {
                if (isShowingImagery) {
                    OutlinedButton(onClick = onHideImagery) {
                        Text(stringResource(R.string.earth_events_latest_imagery))
                    }
                } else {
                    FilledTonalButton(
                        onClick = onShowImagery,
                        enabled = !isLoadingImagery,
                        modifier = Modifier.testTag(EARTH_EVENT_SHOW_IMAGERY_TEST_TAG),
                    ) {
                        if (isLoadingImagery) {
                            CircularProgressIndicator(modifier = Modifier.size(ButtonDefaults.IconSize), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Outlined.SatelliteAlt, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                        }
                        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                        Text(stringResource(R.string.earth_events_show_imagery))
                    }
                }
                event.sources.forEach { source ->
                    TextButton(
                        onClick = { onOpenSource(source.url) },
                        modifier = Modifier.testTag(EARTH_EVENT_SOURCE_TEST_TAG),
                    ) {
                        Text(
                            stringResource(
                                R.string.earth_events_source,
                                source.id.ifBlank { stringResource(R.string.earth_events_source_unnamed) },
                            ),
                        )
                        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                        Icon(
                            Icons.AutoMirrored.Outlined.OpenInNew,
                            contentDescription = null,
                            modifier = Modifier.size(ButtonDefaults.IconSize),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Detail(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(end = 12.dp),
    )
}

/**
 * EONET reports wind in knots, burned areas in acres and ice in square nautical miles; each is
 * shown in the user's units (`83 km/h` or `52 mph`, `304 ha` or `751 acres`, `43 km²` or
 * `17 mi²`). A unit EONET may add later is shown as it came.
 */
internal fun EventMagnitude.formatted(units: UnitFormatter): String = when (unit.trim().lowercase(Locale.ROOT)) {
    "kts", "kt", "knots" -> units.speedPerHour(value * UnitFormatter.KM_PER_NAUTICAL_MILE)
    "acres" -> units.landArea(value)
    "nm^2", "nm²" -> units.area(value * UnitFormatter.KM_PER_NAUTICAL_MILE * UnitFormatter.KM_PER_NAUTICAL_MILE)
    else -> {
        val number = units.number(value, fractionDigits = if (value % 1.0 == 0.0) 0 else 1)
        "$number ${unit.replace("^2", "²")}"
    }
}

internal fun Context.openUrl(url: String) {
    try {
        startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
    } catch (_: ActivityNotFoundException) {
        // No browser installed; nothing sensible to do.
    }
}

private val SHEET_MAX_HEIGHT = 320.dp
