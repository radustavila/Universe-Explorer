package com.radustavila.nasaexplorer.feature.skymap.deepsky

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.radustavila.nasaexplorer.core.model.ObservingConditions
import com.radustavila.nasaexplorer.core.model.ObservingLogEntry
import com.radustavila.nasaexplorer.feature.skymap.R
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

const val MARK_SEEN_DIALOG_TEST_TAG = "mark-seen-dialog"
const val MARK_SEEN_NOTE_TEST_TAG = "mark-seen-note"

private const val NOTE_MAX_LENGTH = 500

/**
 * "Mark as seen" (F23): the day it was seen (today unless changed, never in the future), how the sky
 * was and a note. With an earlier [entry] it edits that mark and offers to remove it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MarkSeenDialog(
    title: String,
    entry: ObservingLogEntry?,
    today: LocalDate,
    onSave: (observedOn: LocalDate, conditions: ObservingConditions?, note: String?) -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    var day by rememberSaveable { mutableStateOf((entry?.observedOn ?: today).toEpochDay()) }
    var conditions by rememberSaveable { mutableStateOf(entry?.conditions) }
    var note by rememberSaveable { mutableStateOf(entry?.note.orEmpty()) }
    var isPickingDay by rememberSaveable { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag(MARK_SEEN_DIALOG_TEST_TAG),
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { isPickingDay = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.CalendarMonth, contentDescription = null)
                    Text(
                        text = stringResource(R.string.deep_sky_seen_on, LocalDate.ofEpochDay(day).formatDay()),
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
                Text(stringResource(R.string.deep_sky_conditions), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ObservingConditions.entries.forEach { option ->
                        FilterChip(
                            selected = conditions == option,
                            onClick = { conditions = option.takeUnless { it == conditions } },
                            label = { Text(stringResource(option.nameRes())) },
                        )
                    }
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it.take(NOTE_MAX_LENGTH) },
                    label = { Text(stringResource(R.string.deep_sky_note)) },
                    modifier = Modifier.fillMaxWidth().testTag(MARK_SEEN_NOTE_TEST_TAG),
                )
                if (entry != null) {
                    TextButton(onClick = onRemove) { Text(stringResource(R.string.deep_sky_remove_mark)) }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(LocalDate.ofEpochDay(day), conditions, note) }) {
                Text(stringResource(R.string.deep_sky_save_mark))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.deep_sky_cancel)) }
        },
    )
    if (isPickingDay) {
        DayPicker(
            day = LocalDate.ofEpochDay(day),
            latest = today,
            onPick = {
                day = it.toEpochDay()
                isPickingDay = false
            },
            onDismiss = { isPickingDay = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DayPicker(day: LocalDate, latest: LocalDate, onPick: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    val latestMillis = latest.toUtcMillis()
    val state = rememberDatePickerState(
        initialSelectedDateMillis = day.toUtcMillis(),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis <= latestMillis

            override fun isSelectableYear(year: Int): Boolean = year <= latest.year
        },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val picked = state.selectedDateMillis?.let(::utcDay)
                    if (picked == null) onDismiss() else onPick(picked)
                },
            ) {
                Text(stringResource(R.string.deep_sky_pick_day))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.deep_sky_cancel)) } },
    ) {
        DatePicker(state = state)
    }
}

private fun utcDay(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()

private fun LocalDate.toUtcMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
