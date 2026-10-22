package com.radustavila.nasaexplorer.feature.events.component

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.radustavila.nasaexplorer.core.designsystem.units.UnitFormatter
import com.radustavila.nasaexplorer.core.domain.sky.SkyEventReminderLead
import com.radustavila.nasaexplorer.feature.events.R
import java.time.Instant

/* The building blocks of an event's sheet: lines, warnings, a timeline and the reminder switch. */

@Composable
internal fun ReminderControls(
    reminderOn: Boolean,
    reminderLead: SkyEventReminderLead?,
    onReminderClick: () -> Unit,
    onLeadChange: (SkyEventReminderLead) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.sky_events_remind_me),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = reminderOn,
                onCheckedChange = { onReminderClick() },
                modifier = Modifier.testTag(SKY_EVENT_REMINDER_SWITCH_TEST_TAG),
            )
        }
        if (reminderOn) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SkyEventReminderLead.entries.forEach { lead ->
                    FilterChip(
                        selected = lead == (reminderLead ?: SkyEventReminderLead.DEFAULT),
                        onClick = { onLeadChange(lead) },
                        label = { Text(stringResource(lead.labelRes)) },
                    )
                }
            }
        }
    }
}

/** Labelled times in order, skipping the phases this event does not have. */
@Composable
internal fun Timeline(units: UnitFormatter, vararg steps: Pair<Int, Instant?>) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        steps.forEach { (label, at) ->
            if (at != null) {
                Row {
                    Text(
                        stringResource(label),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        units.time(at),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
internal fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
}

@Composable
internal fun Line(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium)
}

@Composable
internal fun Warning(text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(Icons.Outlined.WarningAmber, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@get:StringRes
internal val SkyEventReminderLead.labelRes: Int
    get() = when (this) {
        SkyEventReminderLead.FIFTEEN_MINUTES -> R.string.sky_events_lead_fifteen_minutes
        SkyEventReminderLead.ONE_HOUR -> R.string.sky_events_lead_one_hour
        SkyEventReminderLead.ONE_DAY -> R.string.sky_events_lead_one_day
    }
