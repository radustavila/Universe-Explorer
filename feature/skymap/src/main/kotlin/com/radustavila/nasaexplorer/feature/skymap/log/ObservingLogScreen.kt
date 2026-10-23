package com.radustavila.nasaexplorer.feature.skymap.log

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.radustavila.nasaexplorer.core.designsystem.units.rememberUnitFormatter
import com.radustavila.nasaexplorer.core.model.DeepSkyCatalogue
import com.radustavila.nasaexplorer.core.model.DeepSkyObject
import com.radustavila.nasaexplorer.core.model.ObservingConditions
import com.radustavila.nasaexplorer.core.model.ObservingLogEntry
import com.radustavila.nasaexplorer.feature.skymap.R
import com.radustavila.nasaexplorer.feature.skymap.compassPointRes
import com.radustavila.nasaexplorer.feature.skymap.deepsky.MarkSeenDialog
import com.radustavila.nasaexplorer.feature.skymap.deepsky.bestMonthAndInstrument
import com.radustavila.nasaexplorer.feature.skymap.deepsky.brightnessAndSize
import com.radustavila.nasaexplorer.feature.skymap.deepsky.designationLine
import com.radustavila.nasaexplorer.feature.skymap.deepsky.formatDay
import com.radustavila.nasaexplorer.feature.skymap.deepsky.nameRes
import com.radustavila.nasaexplorer.feature.skymap.deepsky.title
import com.radustavila.nasaexplorer.feature.skymap.render.DeepSkyStyle
import java.time.LocalDate
import kotlin.math.roundToInt

const val OBSERVING_LOG_LIST_TEST_TAG = "observing-log-list"
const val OBSERVING_LOG_ROW_TEST_TAG = "observing-log-row"

private val CONTENT_MAX_WIDTH = 720.dp
private val NUMBER_WIDTH = 52.dp

/** The Messier and Caldwell checklists (F23), with progress and the "not seen" and "up tonight" filters. */
@Composable
fun ObservingLogScreen(
    onBackClick: () -> Unit,
    onShowOnSkyMap: (objectId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ObservingLogViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    ObservingLogContent(
        uiState = uiState,
        onBackClick = onBackClick,
        onShowOnSkyMap = onShowOnSkyMap,
        onCatalogueChange = viewModel::onCatalogueChange,
        onNotSeenOnlyChange = viewModel::onNotSeenOnlyChange,
        onUpTonightOnlyChange = viewModel::onUpTonightOnlyChange,
        onMarkSeen = viewModel::onMarkSeen,
        onRemoveSeen = viewModel::onRemoveSeen,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ObservingLogContent(
    uiState: ObservingLogUiState,
    onBackClick: () -> Unit,
    onShowOnSkyMap: (objectId: String) -> Unit,
    onCatalogueChange: (DeepSkyCatalogue) -> Unit,
    onNotSeenOnlyChange: (Boolean) -> Unit,
    onUpTonightOnlyChange: (Boolean) -> Unit,
    onMarkSeen: (objectId: String, observedOn: LocalDate, conditions: ObservingConditions?, note: String?) -> Unit,
    onRemoveSeen: (objectId: String) -> Unit,
    modifier: Modifier = Modifier,
    today: LocalDate = LocalDate.now(),
) {
    var expandedId by rememberSaveable { mutableStateOf<String?>(null) }
    var loggingId by rememberSaveable { mutableStateOf<String?>(null) }
    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.observing_log_title)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.observing_log_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            if (uiState.objects == null) {
                val loading = stringResource(R.string.observing_log_loading)
                CircularProgressIndicator(Modifier.padding(32.dp).semantics { contentDescription = loading })
            } else {
                Column(Modifier.widthIn(max = CONTENT_MAX_WIDTH).fillMaxWidth()) {
                    CatalogueTabs(uiState.catalogue, onCatalogueChange)
                    Progress(uiState)
                    Filters(uiState, onNotSeenOnlyChange, onUpTonightOnlyChange)
                    Checklist(
                        uiState = uiState,
                        expandedId = expandedId,
                        onRowClick = { id -> expandedId = id.takeUnless { it == expandedId } },
                        onLogClick = { loggingId = it },
                        onShowOnSkyMap = onShowOnSkyMap,
                    )
                }
            }
        }
    }
    val logging = loggingId?.let { id -> uiState.objects?.firstOrNull { it.id == id } }
    if (logging != null) {
        val close = { loggingId = null }
        MarkSeenDialog(
            title = logging.title(),
            entry = uiState.log[logging.id],
            today = today,
            onSave = { day, conditions, note ->
                onMarkSeen(logging.id, day, conditions, note)
                close()
            },
            onRemove = {
                onRemoveSeen(logging.id)
                close()
            },
            onDismiss = close,
        )
    }
}

@Composable
private fun CatalogueTabs(selected: DeepSkyCatalogue, onCatalogueChange: (DeepSkyCatalogue) -> Unit) {
    PrimaryTabRow(selectedTabIndex = selected.ordinal) {
        DeepSkyCatalogue.entries.forEach { catalogue ->
            Tab(
                selected = catalogue == selected,
                onClick = { onCatalogueChange(catalogue) },
                text = { Text(stringResource(catalogue.nameRes())) },
            )
        }
    }
}

@Composable
private fun Progress(uiState: ObservingLogUiState) {
    val total = uiState.checklist.size
    val seen = uiState.seenCount(uiState.catalogue)
    Column(
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(stringResource(R.string.observing_log_progress, seen, total), style = MaterialTheme.typography.titleMedium)
        LinearProgressIndicator(
            progress = { if (total == 0) 0f else seen / total.toFloat() },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun Filters(
    uiState: ObservingLogUiState,
    onNotSeenOnlyChange: (Boolean) -> Unit,
    onUpTonightOnlyChange: (Boolean) -> Unit,
) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = uiState.notSeenOnly,
                onClick = { onNotSeenOnlyChange(!uiState.notSeenOnly) },
                label = { Text(stringResource(R.string.observing_log_filter_not_seen)) },
            )
            FilterChip(
                selected = uiState.upTonightOnly,
                onClick = { onUpTonightOnlyChange(!uiState.upTonightOnly) },
                label = { Text(stringResource(R.string.observing_log_filter_tonight)) },
            )
        }
        if (uiState.upTonightOnly) {
            when (uiState.tonight) {
                TonightSky.NoPlace -> R.string.observing_log_tonight_needs_place
                TonightSky.NotDark -> R.string.observing_log_tonight_not_dark
                else -> null
            }?.let { message ->
                Text(
                    text = stringResource(message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun Checklist(
    uiState: ObservingLogUiState,
    expandedId: String?,
    onRowClick: (String) -> Unit,
    onLogClick: (String) -> Unit,
    onShowOnSkyMap: (String) -> Unit,
) {
    val shown = uiState.shown
    if (shown.isEmpty()) {
        Text(
            text = stringResource(R.string.observing_log_empty),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(16.dp),
        )
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag(OBSERVING_LOG_LIST_TEST_TAG),
        contentPadding = PaddingValues(bottom = 16.dp),
    ) {
        items(shown, key = { it.id }) { item ->
            ChecklistRow(
                item = item,
                number = item.numberIn(uiState.catalogue),
                catalogue = uiState.catalogue,
                constellation = uiState.constellationNames[item.constellation] ?: item.constellation,
                entry = uiState.log[item.id],
                tonight = uiState.tonight,
                isExpanded = item.id == expandedId,
                onClick = { onRowClick(item.id) },
                onLogClick = { onLogClick(item.id) },
                onShowOnSkyMap = { onShowOnSkyMap(item.id) },
            )
            HorizontalDivider()
        }
    }
}

@Composable
private fun ChecklistRow(
    item: DeepSkyObject,
    number: Int?,
    catalogue: DeepSkyCatalogue,
    constellation: String,
    entry: ObservingLogEntry?,
    tonight: TonightSky,
    isExpanded: Boolean,
    onClick: () -> Unit,
    onLogClick: () -> Unit,
    onShowOnSkyMap: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag(OBSERVING_LOG_ROW_TEST_TAG)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = catalogue.prefix() + number,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.width(NUMBER_WIDTH),
            )
            Column(Modifier.weight(1f)) {
                Text(
                    text = item.commonName ?: item.designation.ifEmpty { DeepSkyStyle.shortName(item) },
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = stringResource(R.string.sky_map_kind_in, stringResource(item.type.nameRes()), constellation),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (entry != null) {
                Icon(
                    imageVector = Icons.Outlined.CheckCircle,
                    contentDescription = stringResource(R.string.observing_log_seen),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
        if (isExpanded) {
            RowDetails(item, entry, tonight, onLogClick, onShowOnSkyMap)
        }
    }
}

@Composable
private fun RowDetails(
    item: DeepSkyObject,
    entry: ObservingLogEntry?,
    tonight: TonightSky,
    onLogClick: () -> Unit,
    onShowOnSkyMap: () -> Unit,
) {
    val units = rememberUnitFormatter()
    Column(Modifier.padding(start = NUMBER_WIDTH, top = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        item.designationLine()?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        item.brightnessAndSize(units)?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        Text(item.bestMonthAndInstrument(), style = MaterialTheme.typography.bodyMedium)
        (tonight as? TonightSky.Known)?.tonight?.visible?.get(item.id)?.let { best ->
            Text(
                text = stringResource(
                    R.string.observing_log_up_tonight,
                    units.time(best.bestTime),
                    best.altitudeDegrees.roundToInt().toString(),
                    stringResource(compassPointRes(best.azimuthDegrees)),
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (entry != null) {
            val conditions = entry.conditions?.let { stringResource(it.nameRes()) }
            val seenOn = stringResource(R.string.deep_sky_seen_on, entry.observedOn.formatDay())
            Text(
                text = listOfNotNull(seenOn, conditions).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            entry.note?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        }
        Row {
            TextButton(onClick = onLogClick) {
                Text(stringResource(if (entry == null) R.string.deep_sky_mark_seen else R.string.deep_sky_edit_mark))
            }
            TextButton(onClick = onShowOnSkyMap) { Text(stringResource(R.string.observing_log_show_on_map)) }
        }
        item.nasaPageUrl?.let { url ->
            val uriHandler = LocalUriHandler.current
            TextButton(onClick = { uriHandler.openUri(url) }) { Text(stringResource(R.string.deep_sky_read_more)) }
        }
    }
}

private fun DeepSkyCatalogue.nameRes(): Int = when (this) {
    DeepSkyCatalogue.MESSIER -> R.string.observing_log_messier
    DeepSkyCatalogue.CALDWELL -> R.string.observing_log_caldwell
}

/** The catalogue letter in front of the number: "M31", "C14". Catalogue designations, not words. */
private fun DeepSkyCatalogue.prefix(): String = when (this) {
    DeepSkyCatalogue.MESSIER -> "M"
    DeepSkyCatalogue.CALDWELL -> "C"
}
