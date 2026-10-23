package com.radustavila.nasaexplorer.feature.climate

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.radustavila.nasaexplorer.core.designsystem.chart.LineChart
import com.radustavila.nasaexplorer.core.designsystem.component.EmptyState
import com.radustavila.nasaexplorer.core.designsystem.component.FreshnessLabel
import com.radustavila.nasaexplorer.core.designsystem.component.LoadingState
import com.radustavila.nasaexplorer.core.designsystem.component.OfflineBanner
import com.radustavila.nasaexplorer.core.designsystem.component.OfflineState
import com.radustavila.nasaexplorer.core.designsystem.component.SectionTitle
import com.radustavila.nasaexplorer.core.designsystem.units.rememberUnitFormatter
import com.radustavila.nasaexplorer.core.designsystem.util.rememberCustomTabOpener
import com.radustavila.nasaexplorer.core.model.ClimateSeries
import java.time.LocalDate

@Composable
fun ClimateScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ClimateViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val failedMessage = stringResource(R.string.climate_refresh_failed)
    LaunchedEffect(uiState.refreshFailed) {
        if (uiState.refreshFailed) {
            snackbarHostState.showSnackbar(failedMessage)
            viewModel.onRefreshFailedShown()
        }
    }
    ClimateContent(
        uiState = uiState,
        onBackClick = onBackClick,
        onRefresh = viewModel::onRefresh,
        onOpenSource = rememberCustomTabOpener(),
        snackbarHostState = snackbarHostState,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ClimateContent(
    uiState: ClimateUiState,
    onBackClick: () -> Unit,
    onRefresh: () -> Unit,
    onOpenSource: (String) -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    val texts = uiState.indicator.texts
    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(texts.title)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.climate_back),
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            if (uiState.freshness.isOffline && uiState.freshness.lastUpdatedAt != null) OfflineBanner()
            PullToRefreshBox(
                isRefreshing = uiState.isRefreshing,
                onRefresh = onRefresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                val series = uiState.series
                when {
                    !uiState.isLoaded -> LoadingState()
                    series == null || series.points.isEmpty() -> NeverDownloaded(uiState.freshness.isOffline)
                    else -> Details(uiState, series, onOpenSource)
                }
            }
        }
    }
}

@Composable
private fun NeverDownloaded(isOffline: Boolean) {
    if (isOffline) {
        OfflineState(
            title = stringResource(R.string.climate_offline_title),
            description = stringResource(R.string.climate_offline_description),
        )
    } else {
        EmptyState(
            title = stringResource(R.string.climate_none_title),
            description = stringResource(R.string.climate_none_description),
        )
    }
}

@Composable
private fun Details(uiState: ClimateUiState, series: ClimateSeries, onOpenSource: (String) -> Unit) {
    val units = rememberUnitFormatter()
    val format = remember(series.indicator, units) { ClimateValueFormat(series.indicator, units) }
    val texts = series.indicator.texts
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .widthIn(max = CONTENT_MAX_WIDTH)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            FreshnessLabel(uiState.freshness)
            LatestValue(series, format)
            Chart(series, format)
            SectionTitle(stringResource(R.string.climate_about))
            Text(stringResource(texts.explanation), style = MaterialTheme.typography.bodyMedium)
            SectionTitle(stringResource(R.string.climate_source))
            Text(
                stringResource(texts.credit),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(onClick = { onOpenSource(texts.sourceUrl) }) {
                Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null)
                Text(stringResource(R.string.climate_open_source), Modifier.padding(start = 8.dp))
            }
        }
    }
}

@Composable
private fun LatestValue(series: ClimateSeries, format: ClimateValueFormat) {
    val units = rememberUnitFormatter()
    val ppm = rememberPpm()
    val latest = series.points.last()
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            stringResource(series.indicator.texts.subtitle),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(format.value(latest.value, ppm), style = MaterialTheme.typography.headlineMedium)
        series.change()?.let { change ->
            Text(
                stringResource(R.string.climate_change_since, format.change(change.delta, ppm), change.sinceYear),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            stringResource(R.string.climate_last_value, units.month(latest.date)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** The whole record, labelled in the user's units, with a readout of the touched month. */
@Composable
private fun Chart(series: ClimateSeries, format: ClimateValueFormat) {
    val units = rememberUnitFormatter()
    val resources = LocalResources.current
    val ppm = rememberPpm()
    val data = remember(series, format) { format.chartData(series) }
    val ticks = remember(series) { yearTicks(series) }
    val first = series.points.first()
    val latest = series.points.last()
    LineChart(
        data = data,
        xTicks = ticks,
        formatX = { LocalDate.ofEpochDay(it.toLong()).year.toString() },
        formatY = { format.axis(it, ppm) },
        readout = { point ->
            val month = units.month(LocalDate.ofEpochDay(point.x.toLong()))
            resources.getString(R.string.climate_readout, month, format.value(point.y / format.scale, ppm))
        },
        description = stringResource(
            R.string.climate_chart_description,
            stringResource(series.indicator.texts.title),
            first.date.year,
            latest.date.year,
            format.value(latest.value, ppm),
        ),
        modifier = Modifier.fillMaxWidth(),
    )
    Text(
        stringResource(R.string.climate_chart_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private val CONTENT_MAX_WIDTH = 720.dp
