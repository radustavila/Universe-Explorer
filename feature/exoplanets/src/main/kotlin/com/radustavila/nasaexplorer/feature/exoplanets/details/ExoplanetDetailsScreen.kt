package com.radustavila.nasaexplorer.feature.exoplanets.details

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.TravelExplore
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.radustavila.nasaexplorer.core.designsystem.component.FavoriteButton
import com.radustavila.nasaexplorer.core.designsystem.component.LoadingState
import com.radustavila.nasaexplorer.core.designsystem.component.MessageState
import com.radustavila.nasaexplorer.core.designsystem.component.OfflineBanner
import com.radustavila.nasaexplorer.core.designsystem.component.SectionTitle
import com.radustavila.nasaexplorer.core.designsystem.error.asText
import com.radustavila.nasaexplorer.core.model.Exoplanet
import com.radustavila.nasaexplorer.core.model.ExoplanetUnits
import com.radustavila.nasaexplorer.feature.exoplanets.PROXIMA_CENTAURI_LIGHT_YEARS
import com.radustavila.nasaexplorer.feature.exoplanets.R
import com.radustavila.nasaexplorer.feature.exoplanets.SizeComparison
import com.radustavila.nasaexplorer.feature.exoplanets.currentLocale
import com.radustavila.nasaexplorer.feature.exoplanets.explanationRes
import com.radustavila.nasaexplorer.feature.exoplanets.formatLightYears
import com.radustavila.nasaexplorer.feature.exoplanets.formatQuantity
import com.radustavila.nasaexplorer.feature.exoplanets.formatWhole
import com.radustavila.nasaexplorer.feature.exoplanets.formatYearMonth
import com.radustavila.nasaexplorer.feature.exoplanets.labelRes

@Composable
fun ExoplanetDetailsScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ExoplanetDetailsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    ExoplanetDetailsContent(
        uiState = uiState,
        onBackClick = onBackClick,
        onToggleFavorite = viewModel::onToggleFavorite,
        onErrorDismissed = viewModel::onErrorDismissed,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExoplanetDetailsContent(
    uiState: ExoplanetDetailsUiState,
    onBackClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onErrorDismissed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val content = uiState as? ExoplanetDetailsUiState.Content
    val errorText = content?.error?.asText()
    LaunchedEffect(errorText) {
        if (errorText != null) {
            snackbarHostState.showSnackbar(errorText)
            onErrorDismissed()
        }
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    val title = when (uiState) {
                        is ExoplanetDetailsUiState.Content -> uiState.planet.name
                        is ExoplanetDetailsUiState.NotFound -> uiState.name
                        ExoplanetDetailsUiState.Loading -> ""
                    }
                    Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.exoplanets_back),
                        )
                    }
                },
                actions = {
                    if (content != null) {
                        FavoriteButton(isFavorite = content.isFavorite, onToggle = onToggleFavorite)
                    }
                },
            )
        },
    ) { padding ->
        when (uiState) {
            ExoplanetDetailsUiState.Loading -> LoadingState(Modifier.padding(padding))

            is ExoplanetDetailsUiState.NotFound -> MessageState(
                icon = Icons.Outlined.TravelExplore,
                title = stringResource(R.string.exoplanet_details_not_found_title),
                description = stringResource(R.string.exoplanet_details_not_found_description),
                modifier = Modifier.padding(padding),
            )

            is ExoplanetDetailsUiState.Content -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                if (uiState.isOffline) OfflineBanner()
                PlanetDetails(planet = uiState.planet)
            }
        }
    }
}

@Composable
private fun PlanetDetails(planet: Exoplanet) {
    val locale = currentLocale()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.exoplanet_details_orbits, planet.hostName),
            style = MaterialTheme.typography.titleMedium,
        )

        Section(title = stringResource(R.string.exoplanet_details_size_title)) {
            val radius = planet.radiusEarth
            val radiusJupiter = planet.radiusJupiter
            if (radius == null || radiusJupiter == null) {
                Text(stringResource(R.string.exoplanet_details_size_unknown))
            } else {
                val times = formatQuantity(radius, locale)
                SizeComparison(
                    planetName = planet.name,
                    radiusEarth = radius,
                    description = stringResource(R.string.exoplanet_details_size_description, planet.name, times),
                )
                Text(
                    text = stringResource(
                        R.string.exoplanet_details_size_summary,
                        times,
                        formatQuantity(radiusJupiter, locale),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        Section(title = stringResource(R.string.exoplanet_details_distance_title)) {
            val lightYears = planet.distanceLightYears
            val parsecs = planet.distanceParsecs
            if (lightYears == null || parsecs == null) {
                Text(stringResource(R.string.exoplanet_details_distance_unknown))
            } else {
                Text(
                    text = stringResource(
                        R.string.exoplanet_details_distance_value,
                        formatLightYears(lightYears, locale),
                        formatQuantity(parsecs, locale),
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                )
                val timesProxima = lightYears / PROXIMA_CENTAURI_LIGHT_YEARS
                Text(
                    // "1× as far as Proxima Centauri" says nothing about Proxima's own planets.
                    text = if (timesProxima < NEIGHBOUR_TIMES_PROXIMA) {
                        stringResource(R.string.exoplanet_details_distance_neighbour)
                    } else {
                        stringResource(
                            R.string.exoplanet_details_distance_compared,
                            formatQuantity(timesProxima, locale),
                        )
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        val facts = buildList {
            val mass = planet.massEarth
            val massJupiter = planet.massJupiter
            if (mass != null && massJupiter != null) {
                add(
                    stringResource(R.string.exoplanet_details_mass) to stringResource(
                        R.string.exoplanet_details_mass_value,
                        formatQuantity(mass, locale),
                        formatQuantity(massJupiter, locale),
                    ),
                )
            }
            planet.orbitalPeriodDays?.let { days ->
                add(
                    stringResource(R.string.exoplanet_details_year) to
                        stringResource(R.string.exoplanet_details_year_value, formatQuantity(days, locale)),
                )
            }
            planet.equilibriumTemperatureKelvin?.let { kelvin ->
                add(
                    stringResource(R.string.exoplanet_details_temperature) to stringResource(
                        R.string.exoplanet_details_temperature_value,
                        formatWhole(kelvin, locale),
                        formatWhole(ExoplanetUnits.kelvinToCelsius(kelvin), locale),
                    ),
                )
            }
        }
        if (facts.isNotEmpty()) {
            Section(title = stringResource(R.string.exoplanet_details_facts_title)) {
                facts.forEach { (label, value) -> Fact(label, value) }
            }
        }

        Section(title = stringResource(R.string.exoplanet_details_discovery_title)) {
            Fact(
                label = stringResource(R.string.exoplanet_details_discovered),
                value = planet.discoveryPublished?.let {
                    stringResource(
                        R.string.exoplanet_details_discovered_value,
                        planet.discoveryYear.toString(),
                        formatYearMonth(it, locale),
                    )
                } ?: planet.discoveryYear.toString(),
            )
            planet.discoveryFacility?.let { Fact(stringResource(R.string.exoplanet_details_facility), it) }
            Fact(
                label = stringResource(R.string.exoplanet_details_method),
                value = stringResource(planet.discoveryMethod.labelRes),
            )
            Text(
                text = stringResource(planet.discoveryMethod.explanationRes),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SectionTitle(text = title, style = MaterialTheme.typography.titleSmall)
            content()
        }
    }
}

@Composable
private fun Fact(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.35f),
        )
        Text(text = value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(0.65f))
    }
}

/** Closer than this many times Proxima Centauri's distance, a planet is a neighbour of the Sun. */
private const val NEIGHBOUR_TIMES_PROXIMA = 2.0
