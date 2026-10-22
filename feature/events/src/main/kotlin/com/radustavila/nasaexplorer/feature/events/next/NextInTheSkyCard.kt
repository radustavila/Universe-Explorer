package com.radustavila.nasaexplorer.feature.events.next

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.radustavila.nasaexplorer.core.designsystem.component.SectionTitle
import com.radustavila.nasaexplorer.core.designsystem.skyevent.skyEventTitle
import com.radustavila.nasaexplorer.core.designsystem.units.rememberUnitFormatter
import com.radustavila.nasaexplorer.core.model.SkyEvent
import com.radustavila.nasaexplorer.feature.events.R
import com.radustavila.nasaexplorer.feature.events.component.SkyEventIcon
import com.radustavila.nasaexplorer.feature.events.component.listedTime
import com.radustavila.nasaexplorer.feature.events.component.skyEventSummary

const val NEXT_IN_THE_SKY_CARD_TEST_TAG = "next-in-the-sky-card"

/**
 * "Next in the sky": the next eclipses, full and new Moons, meteor showers, planet events and
 * seasons, for Today and Sky tonight. [onOpenCalendar] opens the calendar; [onEventClick] one event.
 * Works without a saved place (global facts only) and offline.
 */
@Composable
fun NextInTheSkyCard(
    onOpenCalendar: () -> Unit,
    onEventClick: (eventId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: NextInTheSkyViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    NextInTheSkyCardContent(uiState, onOpenCalendar, onEventClick, modifier)
}

@Composable
internal fun NextInTheSkyCardContent(
    uiState: NextInTheSkyUiState,
    onOpenCalendar: () -> Unit,
    onEventClick: (eventId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!uiState.isLoaded) return
    Card(onClick = onOpenCalendar, modifier = modifier.fillMaxWidth().testTag(NEXT_IN_THE_SKY_CARD_TEST_TAG)) {
        Column(Modifier.padding(vertical = 8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 16.dp, end = 8.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Event,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                SectionTitle(stringResource(R.string.sky_events_next_title), modifier = Modifier.weight(1f))
                IconButton(onClick = onOpenCalendar) {
                    Icon(
                        Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                        contentDescription = stringResource(R.string.sky_events_next_open),
                    )
                }
            }
            uiState.events.forEach { event ->
                NextEventLine(event, uiState.hasPlace, onClick = { onEventClick(event.id) })
            }
        }
    }
}

@Composable
private fun NextEventLine(event: SkyEvent, hasPlace: Boolean, onClick: () -> Unit) {
    val units = rememberUnitFormatter()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        SkyEventIcon(event, size = 32.dp)
        Column(Modifier.weight(1f)) {
            Text(
                skyEventTitle(event),
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val summary = skyEventSummary(event, hasPlace, units)
            Text(
                text = listOfNotNull(
                    units.day(event.listedTime),
                    summary,
                ).joinToString(stringResource(R.string.sky_events_separator)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
