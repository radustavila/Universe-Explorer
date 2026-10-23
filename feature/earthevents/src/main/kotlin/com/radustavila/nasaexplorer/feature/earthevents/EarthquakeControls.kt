package com.radustavila.nasaexplorer.feature.earthevents

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.radustavila.nasaexplorer.core.designsystem.earthquake.EarthquakeText
import com.radustavila.nasaexplorer.core.designsystem.earthquake.color
import com.radustavila.nasaexplorer.core.designsystem.units.rememberUnitFormatter
import com.radustavila.nasaexplorer.core.model.Earthquake
import com.radustavila.nasaexplorer.core.model.EarthquakeFilter

const val EARTH_EVENTS_QUAKE_FILTER_TEST_TAG = "earth-events-quake-filter"
const val EARTH_EVENTS_QUAKE_LIST_TEST_TAG = "earth-events-quake-list"

fun earthquakeFilterTestTag(filter: EarthquakeFilter) = "earth-events-quake-filter-${filter.key}"

/** One chip per filter with its count; at most one is on, and tapping it again hides the quakes. */
@Composable
internal fun EarthquakeFilterRow(
    counts: List<Pair<EarthquakeFilter, Int>>,
    selected: EarthquakeFilter?,
    onFilterSelected: (EarthquakeFilter) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp)
            .testTag(EARTH_EVENTS_QUAKE_FILTER_TEST_TAG),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        counts.forEach { (filter, count) ->
            FilterChip(
                selected = filter == selected,
                onClick = { onFilterSelected(filter) },
                label = { Text(stringResource(filter.labelRes, count)) },
                modifier = Modifier.testTag(earthquakeFilterTestTag(filter)),
            )
        }
    }
}

/** The filtered quakes, newest first; a tap shows the quake on the map. */
@Composable
internal fun EarthquakeList(
    quakes: List<Earthquake>,
    onQuakeClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (quakes.isEmpty()) {
        Box(modifier.testTag(EARTH_EVENTS_QUAKE_LIST_TEST_TAG), contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(R.string.earth_events_quakes_list_empty),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(24.dp),
            )
        }
        return
    }
    LazyColumn(modifier.testTag(EARTH_EVENTS_QUAKE_LIST_TEST_TAG)) {
        items(quakes, key = Earthquake::id) { quake ->
            EarthquakeRow(quake, onClick = { onQuakeClick(quake.id) })
            HorizontalDivider()
        }
    }
}

@Composable
private fun EarthquakeRow(quake: Earthquake, onClick: () -> Unit) {
    val resources = LocalContext.current.resources
    val formatter = rememberUnitFormatter()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier.size(MAGNITUDE_BADGE_SIZE).background(quake.depth.color, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = formatter.number(quake.magnitude, fractionDigits = 1),
                style = MaterialTheme.typography.labelLarge,
                color = Color.Black,
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = EarthquakeText.place(resources, formatter, quake),
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(
                    R.string.earth_events_quake_row_details,
                    formatter.dateTime(quake.time),
                    EarthquakeText.depth(resources, formatter, quake),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private val EarthquakeFilter.labelRes: Int
    get() = when (this) {
        EarthquakeFilter.MAGNITUDE_2_5 -> R.string.earth_events_quakes_m2_5
        EarthquakeFilter.MAGNITUDE_4_5 -> R.string.earth_events_quakes_m4_5
        EarthquakeFilter.SIGNIFICANT -> R.string.earth_events_quakes_significant
    }

private val MAGNITUDE_BADGE_SIZE = 40.dp
