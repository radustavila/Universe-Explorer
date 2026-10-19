package com.radustavila.nasaexplorer.feature.universe.gallery

import androidx.annotation.StringRes
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.radustavila.nasaexplorer.core.model.NasaMediaType
import com.radustavila.nasaexplorer.feature.universe.R
import java.time.Year
import kotlin.math.roundToInt

/** Media type chips and a year range, the two filters the library's search takes. */
@Composable
internal fun SearchFiltersRow(
    filters: SearchFilters,
    onToggleMediaType: (NasaMediaType) -> Unit,
    onYearRangeChange: (start: Int?, end: Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var isYearDialogOpen by rememberSaveable { mutableStateOf(false) }

    Row(
        modifier = modifier
            .horizontalScroll(rememberScrollState())
            .padding(PaddingValues(horizontal = 16.dp)),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        NasaMediaType.entries.forEach { type ->
            FilterChip(
                selected = type in filters.mediaTypes,
                onClick = { onToggleMediaType(type) },
                label = { Text(stringResource(type.filterLabelRes)) },
            )
        }

        val hasYears = filters.yearStart != null || filters.yearEnd != null
        FilterChip(
            selected = hasYears,
            onClick = { isYearDialogOpen = true },
            leadingIcon = { Icon(Icons.Outlined.DateRange, contentDescription = null) },
            label = {
                Text(
                    if (hasYears) {
                        stringResource(
                            R.string.gallery_filter_years,
                            filters.yearStart ?: FIRST_YEAR,
                            filters.yearEnd ?: currentYear(),
                        )
                    } else {
                        stringResource(R.string.gallery_filter_any_year)
                    },
                )
            },
        )
    }

    if (isYearDialogOpen) {
        YearRangeDialog(
            initialStart = filters.yearStart,
            initialEnd = filters.yearEnd,
            onConfirm = { start, end ->
                onYearRangeChange(start, end)
                isYearDialogOpen = false
            },
            onDismiss = { isYearDialogOpen = false },
        )
    }
}

@Composable
private fun YearRangeDialog(
    initialStart: Int?,
    initialEnd: Int?,
    onConfirm: (start: Int?, end: Int?) -> Unit,
    onDismiss: () -> Unit,
) {
    val lastYear = remember { currentYear() }
    var range by remember {
        mutableStateOf((initialStart ?: FIRST_YEAR).toFloat()..(initialEnd ?: lastYear).toFloat())
    }
    val start = range.start.roundToInt()
    val end = range.endInclusive.roundToInt()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.gallery_years_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.gallery_filter_years, start, end),
                    style = MaterialTheme.typography.titleMedium,
                )
                RangeSlider(
                    value = range,
                    onValueChange = { range = it },
                    valueRange = FIRST_YEAR.toFloat()..lastYear.toFloat(),
                    steps = lastYear - FIRST_YEAR - 1,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    // The full span is no filter at all; leaving it out keeps the query short.
                    onConfirm(start.takeIf { it > FIRST_YEAR }, end.takeIf { it < lastYear })
                },
            ) { Text(stringResource(R.string.gallery_years_apply)) }
        },
        dismissButton = {
            TextButton(onClick = { onConfirm(null, null) }) {
                Text(stringResource(R.string.gallery_years_reset))
            }
        },
    )
}

@get:StringRes
private val NasaMediaType.filterLabelRes: Int
    get() = when (this) {
        NasaMediaType.IMAGE -> R.string.gallery_filter_images
        NasaMediaType.VIDEO -> R.string.gallery_filter_videos
        NasaMediaType.AUDIO -> R.string.gallery_filter_audio
    }

private fun currentYear(): Int = Year.now().value

/** The library's oldest items are early aviation photos from the 1920s. */
private const val FIRST_YEAR = 1920
