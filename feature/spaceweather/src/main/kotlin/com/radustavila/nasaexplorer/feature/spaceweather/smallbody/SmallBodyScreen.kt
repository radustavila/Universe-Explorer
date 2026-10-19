package com.radustavila.nasaexplorer.feature.spaceweather.smallbody

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import com.radustavila.nasaexplorer.core.designsystem.error.asText
import com.radustavila.nasaexplorer.core.designsystem.util.formatDateTime
import com.radustavila.nasaexplorer.core.model.CloseApproach
import com.radustavila.nasaexplorer.core.model.SentryRisk
import com.radustavila.nasaexplorer.core.model.SizeEstimate
import com.radustavila.nasaexplorer.core.model.SmallBodyDetails
import com.radustavila.nasaexplorer.core.model.SmallBodyUnits
import com.radustavila.nasaexplorer.feature.spaceweather.R
import com.radustavila.nasaexplorer.feature.spaceweather.components.currentLocale
import com.radustavila.nasaexplorer.feature.spaceweather.neighbourhood.SENTRY_COLOR
import com.radustavila.nasaexplorer.feature.spaceweather.neighbourhood.SentryBadge
import com.radustavila.nasaexplorer.feature.spaceweather.neighbourhood.formatKilometres
import com.radustavila.nasaexplorer.feature.spaceweather.neighbourhood.formatLunarDistance
import com.radustavila.nasaexplorer.feature.spaceweather.neighbourhood.formatOdds
import com.radustavila.nasaexplorer.feature.spaceweather.neighbourhood.formatSize
import java.util.Locale

const val SMALL_BODY_DETAILS_TEST_TAG = "small-body-details"
const val SMALL_BODY_SENTRY_TEST_TAG = "small-body-sentry"

@Composable
internal fun SmallBodyScreen(
    onBackClick: () -> Unit,
    viewModel: SmallBodyViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    SmallBodyContent(uiState = uiState, onBackClick = onBackClick, onRetry = viewModel::onRetry)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmallBodyContent(
    uiState: SmallBodyUiState,
    onBackClick: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = uiState.details?.name ?: uiState.approach?.name ?: uiState.designation
    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.space_weather_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val sectionModifier = Modifier.widthIn(max = CONTENT_MAX_WIDTH).fillMaxWidth()
            uiState.approach?.let { ApproachCard(it, sectionModifier) }
            uiState.approach?.sentry?.let { SentryCard(it, sectionModifier) }
            when {
                uiState.details != null -> DetailsCard(uiState.details, sectionModifier)
                uiState.isLoadingDetails -> CircularProgressIndicator()
                uiState.detailsError != null -> Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = sectionModifier,
                ) {
                    Text(
                        stringResource(R.string.small_body_details_failed, uiState.detailsError.asText()),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (uiState.detailsError.isRetryable) {
                        Button(onClick = onRetry) { Text(stringResource(R.string.small_body_retry)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ApproachCard(approach: CloseApproach, modifier: Modifier) {
    Card(modifier) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.small_body_approach_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (approach.sentry != null) SentryBadge()
            }
            Fact(stringResource(R.string.small_body_closest), approach.time.formatDateTime())
            Fact(
                stringResource(R.string.small_body_distance),
                stringResource(
                    R.string.small_body_distance_value,
                    formatLunarDistance(approach.distanceLunar, currentLocale()),
                    formatKilometres(approach.distanceKm, currentLocale()),
                ),
            )
            Fact(
                stringResource(R.string.small_body_speed),
                stringResource(R.string.neighbourhood_speed, String.format(currentLocale(), "%.1f", approach.relativeVelocityKmPerSecond)),
            )
            approach.size?.let { Fact(stringResource(sizeLabel(it)), formatSize(it, currentLocale())) }
        }
    }
}

@Composable
private fun SentryCard(sentry: SentryRisk, modifier: Modifier) {
    Card(modifier.testTag(SMALL_BODY_SENTRY_TEST_TAG)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.small_body_sentry_title), style = MaterialTheme.typography.titleMedium, color = SENTRY_COLOR)
            Text(stringResource(R.string.small_body_sentry_explanation), style = MaterialTheme.typography.bodySmall)
            Fact(stringResource(R.string.small_body_sentry_odds), formatOdds(sentry.impactProbability, currentLocale()))
            sentry.yearRange?.let { Fact(stringResource(R.string.small_body_sentry_years), it) }
            sentry.potentialImpacts?.let { Fact(stringResource(R.string.small_body_sentry_impacts), it.toString()) }
            sentry.torinoMax?.let { Fact(stringResource(R.string.small_body_sentry_torino), it.toString()) }
            sentry.palermoMax?.let { Fact(stringResource(R.string.small_body_sentry_palermo), String.format(currentLocale(), "%.2f", it)) }
        }
    }
}

@Composable
private fun DetailsCard(details: SmallBodyDetails, modifier: Modifier) {
    Card(modifier.testTag(SMALL_BODY_DETAILS_TEST_TAG)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.small_body_orbit_title), style = MaterialTheme.typography.titleMedium)
            details.orbitClass?.let { Fact(stringResource(R.string.small_body_orbit_class), it) }
            Fact(
                stringResource(R.string.small_body_hazardous),
                stringResource(if (details.isPotentiallyHazardous) R.string.small_body_yes else R.string.small_body_no),
            )
            details.earthMoidAu?.let { moid ->
                Fact(
                    stringResource(R.string.small_body_moid),
                    stringResource(R.string.small_body_ld, formatLunarDistance(moid * SmallBodyUnits.LUNAR_DISTANCES_PER_AU, currentLocale())),
                )
            }
            details.orbitalPeriodDays?.let {
                Fact(stringResource(R.string.small_body_period), stringResource(R.string.small_body_days, String.format(currentLocale(), "%.0f", it)))
            }
            details.semiMajorAxisAu?.let { Fact(stringResource(R.string.small_body_semi_major_axis), au(it, currentLocale())) }
            if (details.perihelionAu != null && details.aphelionAu != null) {
                Fact(stringResource(R.string.small_body_sun_distance), "${au(details.perihelionAu!!, currentLocale())} – ${au(details.aphelionAu!!, currentLocale())}")
            }
            details.eccentricity?.let { Fact(stringResource(R.string.small_body_eccentricity), String.format(currentLocale(), "%.3f", it)) }
            details.inclinationDegrees?.let { Fact(stringResource(R.string.small_body_inclination), String.format(currentLocale(), "%.1f°", it)) }
            details.absoluteMagnitude?.let { Fact(stringResource(R.string.small_body_magnitude), String.format(currentLocale(), "%.1f", it)) }
            val size = details.diameterKm?.let { SizeEstimate(it * 1_000, it * 1_000) }
                ?: details.absoluteMagnitude?.let(SizeEstimate::fromAbsoluteMagnitude)
            size?.let { Fact(stringResource(sizeLabel(it)), formatSize(it, currentLocale())) }
            if (details.firstObserved != null && details.lastObserved != null) {
                Fact(stringResource(R.string.small_body_observed), "${details.firstObserved} – ${details.lastObserved}")
            }
            details.observationsUsed?.let { Fact(stringResource(R.string.small_body_observations), it.toString()) }
        }
    }
}

private fun sizeLabel(size: SizeEstimate): Int =
    if (size.isRange) R.string.small_body_size_estimated else R.string.small_body_size

private fun au(value: Double, locale: Locale): String = String.format(locale, "%.2f au", value)

@Composable
private fun Fact(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

private val CONTENT_MAX_WIDTH = 640.dp
