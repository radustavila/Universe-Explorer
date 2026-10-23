package com.radustavila.nasaexplorer.core.designsystem.earthquake

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.radustavila.nasaexplorer.core.designsystem.R
import com.radustavila.nasaexplorer.core.designsystem.component.SectionTitle
import com.radustavila.nasaexplorer.core.designsystem.units.rememberUnitFormatter
import com.radustavila.nasaexplorer.core.model.Earthquake

const val EARTHQUAKE_SHEET_TEST_TAG = "earthquake-sheet"
const val EARTHQUAKE_PAGE_BUTTON_TEST_TAG = "earthquake-page"

/**
 * What is known about one quake, floating over a map: magnitude and place, time, depth, felt
 * reports, USGS's impact alert and tsunami flag, and the USGS event page ([onOpenPage], a Custom
 * Tab). The credit USGS asks for sits at the bottom.
 */
@Composable
fun EarthquakeSheet(
    quake: Earthquake,
    onOpenPage: (url: String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val resources = LocalContext.current.resources
    val formatter = rememberUnitFormatter()
    Surface(
        modifier = modifier.testTag(EARTHQUAKE_SHEET_TEST_TAG),
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
                Box(Modifier.size(12.dp).background(quake.depth.color, CircleShape))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.earthquake_label),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onClose) {
                    Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.earthquake_close))
                }
            }
            SectionTitle(
                text = EarthquakeText.title(resources, formatter, quake),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(end = 12.dp),
            )
            Detail(formatter.dateTime(quake.time))
            Detail(EarthquakeText.depth(resources, formatter, quake))
            quake.feltReports?.let { felt -> Detail(pluralStringResource(R.plurals.earthquake_felt, felt, felt)) }
            quake.alertLevel?.let { level -> Detail(stringResource(EarthquakeText.alertRes(level))) }
            if (quake.tsunami) {
                Text(
                    text = stringResource(R.string.earthquake_tsunami),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(end = 12.dp),
                )
            }
            FilledTonalButton(
                onClick = { onOpenPage(quake.url) },
                modifier = Modifier.padding(top = 6.dp).testTag(EARTHQUAKE_PAGE_BUTTON_TEST_TAG),
            ) {
                Text(stringResource(R.string.earthquake_open_page))
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Icon(
                    Icons.AutoMirrored.Outlined.OpenInNew,
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.IconSize),
                )
            }
            Text(
                text = stringResource(R.string.earthquake_credit),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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

private val SHEET_MAX_HEIGHT = 340.dp
