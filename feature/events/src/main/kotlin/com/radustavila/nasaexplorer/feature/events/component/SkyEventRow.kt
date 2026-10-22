package com.radustavila.nasaexplorer.feature.events.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.radustavila.nasaexplorer.core.designsystem.skyevent.skyEventTitle
import com.radustavila.nasaexplorer.core.designsystem.units.rememberUnitFormatter
import com.radustavila.nasaexplorer.core.model.SkyEvent
import com.radustavila.nasaexplorer.feature.events.R

/** An event's icon in a tinted circle, by category. */
@Composable
internal fun SkyEventIcon(event: SkyEvent, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
    ) {
        Icon(
            imageVector = event.category.icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.size(size / 2),
        )
    }
}

/** One event in the calendar: icon, name, a short line, when, and a bell when a reminder is set. */
@Composable
internal fun SkyEventRow(
    event: SkyEvent,
    hasPlace: Boolean,
    hasReminder: Boolean,
    isPast: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val units = rememberUnitFormatter()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .alpha(if (isPast) PAST_ALPHA else 1f)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        SkyEventIcon(event)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(skyEventTitle(event), style = MaterialTheme.typography.titleSmall)
            Text(
                text = units.dayAndTime(event.listedTime),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
            skyEventSummary(event, hasPlace, units)?.let { summary ->
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (hasReminder) {
            Icon(
                imageVector = Icons.Outlined.NotificationsActive,
                contentDescription = stringResource(R.string.sky_events_reminder_set),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

private const val PAST_ALPHA = 0.55f
