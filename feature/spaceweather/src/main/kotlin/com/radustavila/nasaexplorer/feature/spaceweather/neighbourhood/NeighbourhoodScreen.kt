package com.radustavila.nasaexplorer.feature.spaceweather.neighbourhood

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.radustavila.nasaexplorer.core.designsystem.component.EmptyState
import com.radustavila.nasaexplorer.core.designsystem.component.FreshnessLabel
import com.radustavila.nasaexplorer.core.designsystem.component.LoadingState
import com.radustavila.nasaexplorer.core.designsystem.component.OfflineBanner
import com.radustavila.nasaexplorer.core.designsystem.component.OfflineState
import com.radustavila.nasaexplorer.core.designsystem.component.SectionTitle
import com.radustavila.nasaexplorer.core.designsystem.units.formatLocalDateTime
import com.radustavila.nasaexplorer.core.designsystem.units.rememberUnitFormatter
import com.radustavila.nasaexplorer.core.model.CloseApproach
import com.radustavila.nasaexplorer.core.model.Fireball
import com.radustavila.nasaexplorer.feature.spaceweather.R
import com.radustavila.nasaexplorer.feature.spaceweather.components.currentLocale
import java.util.Locale

const val NEIGHBOURHOOD_LIST_TEST_TAG = "neighbourhood-list"
const val SENTRY_BADGE_TEST_TAG = "sentry-badge"

fun closeApproachTestTag(designation: String) = "close-approach-$designation"

@Composable
internal fun NeighbourhoodScreen(
    onBackClick: () -> Unit,
    onApproachClick: (String) -> Unit,
    viewModel: NeighbourhoodViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    NeighbourhoodContent(
        uiState = uiState,
        onBackClick = onBackClick,
        onRefresh = viewModel::onRefresh,
        onApproachClick = onApproachClick,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NeighbourhoodContent(
    uiState: NeighbourhoodUiState,
    onBackClick: () -> Unit,
    onRefresh: () -> Unit,
    onApproachClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.neighbourhood_title)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.space_weather_back))
                    }
                },
            )
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing && uiState.hasData,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            when {
                uiState.hasData || uiState.hasSynced -> NeighbourhoodList(uiState, onApproachClick)
                uiState.isOffline -> OfflineState(
                    title = stringResource(R.string.neighbourhood_offline_title),
                    description = stringResource(R.string.space_weather_offline_description),
                    onRetry = onRefresh,
                )

                uiState.lastRefreshFailed && !uiState.isRefreshing -> EmptyState(
                    title = stringResource(R.string.space_weather_error_title),
                    description = stringResource(R.string.space_weather_error_description),
                    onRetry = onRefresh,
                )

                else -> LoadingState()
            }
        }
    }
}

@Composable
private fun NeighbourhoodList(uiState: NeighbourhoodUiState, onApproachClick: (String) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag(NEIGHBOURHOOD_LIST_TEST_TAG),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (uiState.isOffline) item(key = "offline", contentType = "offline") { OfflineBanner() }
        // Always an item (see SkyTonightScreen): the list keeps it, not the first row, in place.
        item(key = "freshness", contentType = "freshness") {
            FreshnessLabel(
                uiState.freshness,
                modifier = Modifier.widthIn(max = CONTENT_MAX_WIDTH).fillMaxWidth().padding(horizontal = 16.dp),
            )
        }
        item(key = "approaches-header", contentType = "header") {
            Header(
                title = stringResource(R.string.neighbourhood_approaches_title),
                subtitle = stringResource(R.string.neighbourhood_approaches_subtitle),
            )
        }
        if (uiState.approaches.isEmpty()) {
            item(key = "approaches-empty", contentType = "note") { Note(stringResource(R.string.neighbourhood_approaches_empty)) }
        }
        items(uiState.approaches, key = { "${it.designation}-${it.time}" }, contentType = { "approach" }) { approach ->
            CloseApproachRow(approach, onClick = { onApproachClick(approach.designation) })
        }
        item(key = "fireballs-header", contentType = "header") {
            Header(
                title = stringResource(R.string.neighbourhood_fireballs_title),
                subtitle = stringResource(R.string.neighbourhood_fireballs_subtitle),
            )
        }
        if (uiState.fireballs.isEmpty()) {
            item(key = "fireballs-empty", contentType = "note") { Note(stringResource(R.string.neighbourhood_fireballs_empty)) }
        }
        items(uiState.fireballs, key = { "fireball-${it.time}" }, contentType = { "fireball" }) { fireball ->
            FireballRow(fireball)
        }
        item(key = "lunar-distance", contentType = "note") {
            Note(stringResource(R.string.neighbourhood_lunar_distance_note))
        }
    }
}

@Composable
private fun CloseApproachRow(approach: CloseApproach, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .widthIn(max = CONTENT_MAX_WIDTH)
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clickable(onClick = onClick)
            .testTag(closeApproachTestTag(approach.designation)),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = approach.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (approach.sentry != null) SentryBadge()
            }
            Text(approach.time.formatLocalDateTime(), style = MaterialTheme.typography.bodyMedium)
            val units = rememberUnitFormatter()
            Text(
                text = listOfNotNull(
                    stringResource(R.string.neighbourhood_distance_ld, formatLunarDistance(approach.distanceLunar, units.locale)),
                    units.speedPerSecond(approach.relativeVelocityKmPerSecond),
                    approach.size?.let { stringResource(R.string.neighbourhood_size, it.format(units)) },
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Marks an object on JPL's Sentry list of possible future impacts. Not a button: just a label. */
@Composable
internal fun SentryBadge(modifier: Modifier = Modifier) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = SENTRY_COLOR.copy(alpha = 0.15f),
        contentColor = SENTRY_COLOR,
        modifier = modifier.testTag(SENTRY_BADGE_TEST_TAG),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(Icons.Outlined.Warning, contentDescription = null, modifier = Modifier.size(16.dp))
            Text(stringResource(R.string.neighbourhood_sentry_badge), style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun FireballRow(fireball: Fireball) {
    Card(Modifier.widthIn(max = CONTENT_MAX_WIDTH).fillMaxWidth().padding(horizontal = 16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(fireball.time.formatLocalDateTime(), style = MaterialTheme.typography.titleSmall)
            Text(
                text = stringResource(R.string.neighbourhood_fireball_energy, formatKilotons(fireball.impactEnergyKilotons, currentLocale())),
                style = MaterialTheme.typography.bodyMedium,
            )
            val latitude = fireball.latitude
            val longitude = fireball.longitude
            val units = rememberUnitFormatter()
            val details = listOfNotNull(
                if (latitude != null && longitude != null) units.coordinate(latitude, longitude) else null,
                fireball.altitudeKm?.let { stringResource(R.string.neighbourhood_fireball_altitude, units.distance(it)) },
                fireball.velocityKmPerSecond?.let { units.speedPerSecond(it) },
            ).joinToString(" · ")
            if (details.isNotEmpty()) {
                Text(details, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun formatKilotons(kilotons: Double, locale: Locale): String =
    if (kilotons < 1) String.format(locale, "%.2f", kilotons) else String.format(locale, "%.1f", kilotons)

@Composable
private fun Header(title: String, subtitle: String) {
    Column(Modifier.widthIn(max = CONTENT_MAX_WIDTH).fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        SectionTitle(title, style = MaterialTheme.typography.titleLarge)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Note(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.widthIn(max = CONTENT_MAX_WIDTH).fillMaxWidth().padding(horizontal = 16.dp),
    )
}

internal val SENTRY_COLOR = Color(0xFFFFB74D)
private val CONTENT_MAX_WIDTH = 640.dp
