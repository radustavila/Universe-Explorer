package com.radustavila.nasaexplorer.feature.sky.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EditLocationAlt
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.radustavila.nasaexplorer.core.designsystem.component.SectionTitle
import com.radustavila.nasaexplorer.core.model.City
import com.radustavila.nasaexplorer.core.model.GeoCoordinate
import com.radustavila.nasaexplorer.core.model.LocationSource
import com.radustavila.nasaexplorer.core.model.ObserverLocation
import com.radustavila.nasaexplorer.feature.sky.CitySearchUiState
import com.radustavila.nasaexplorer.feature.sky.R

const val SKY_LOCATION_PROMPT_TEST_TAG = "sky-location-prompt"
const val SKY_USE_LOCATION_BUTTON_TEST_TAG = "sky-use-location"
const val SKY_CHOOSE_CITY_BUTTON_TEST_TAG = "sky-choose-city"
const val SKY_LOCATION_HEADER_TEST_TAG = "sky-location-header"
const val SKY_CITY_SEARCH_FIELD_TEST_TAG = "sky-city-search"
const val SKY_LATITUDE_FIELD_TEST_TAG = "sky-latitude"
const val SKY_LONGITUDE_FIELD_TEST_TAG = "sky-longitude"
const val SKY_USE_COORDINATES_TEST_TAG = "sky-use-coordinates"

const val SKY_CITY_RESULTS_TEST_TAG = "sky-city-results"

private const val MAX_RESULTS = 4
private val RESULT_VERTICAL_PADDING = 6.dp

/** The results area never takes more than this share of the window, however large the font. */
private const val RESULTS_MAX_WINDOW_FRACTION = 0.35f

/** First visit: why the screen needs a location, and the two ways to give one. */
@Composable
internal fun LocationPrompt(
    isLocating: Boolean,
    onUseMyLocation: () -> Unit,
    onChooseCity: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SkySection(
        title = stringResource(R.string.sky_location_prompt_title),
        modifier = modifier.testTag(SKY_LOCATION_PROMPT_TEST_TAG),
    ) {
        Text(stringResource(R.string.sky_location_prompt_rationale), style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(
                onClick = onUseMyLocation,
                enabled = !isLocating,
                modifier = Modifier.testTag(SKY_USE_LOCATION_BUTTON_TEST_TAG),
            ) {
                Icon(Icons.Outlined.MyLocation, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(stringResource(R.string.sky_use_my_location), modifier = Modifier.padding(start = 8.dp))
            }
            OutlinedButton(onClick = onChooseCity, modifier = Modifier.testTag(SKY_CHOOSE_CITY_BUTTON_TEST_TAG)) {
                Text(stringResource(R.string.sky_choose_city))
            }
        }
        if (isLocating) LocatingRow()
    }
}

@Composable
private fun LocatingRow() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
        Text(stringResource(R.string.sky_locating), style = MaterialTheme.typography.bodySmall)
    }
}

/** The place the screen is computed for, with ways to change it. */
@Composable
internal fun LocationHeader(
    location: ObserverLocation,
    isLocating: Boolean,
    onUseMyLocation: () -> Unit,
    onChangeLocation: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .testTag(SKY_LOCATION_HEADER_TEST_TAG),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.LocationOn, contentDescription = null)
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp),
        ) {
            val title = location.name
                ?: stringResource(R.string.sky_location_device).takeIf { location.source == LocationSource.DEVICE }
            Text(
                text = title ?: location.coordinate.label(),
                style = MaterialTheme.typography.titleMedium,
            )
            if (title != null) {
                Text(
                    text = location.coordinate.label(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (isLocating) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
        } else {
            IconButton(onClick = onUseMyLocation) {
                Icon(Icons.Outlined.MyLocation, contentDescription = stringResource(R.string.sky_use_my_location))
            }
        }
        IconButton(onClick = onChangeLocation) {
            Icon(Icons.Outlined.EditLocationAlt, contentDescription = stringResource(R.string.sky_change_location))
        }
    }
}

/** Manual fallback: a city from the bundled list, or raw coordinates. Works offline. */
@Composable
internal fun LocationDialog(
    citySearch: CitySearchUiState,
    onQueryChange: (String) -> Unit,
    onCitySelected: (City) -> Unit,
    onCoordinatesEntered: (GeoCoordinate) -> Unit,
    onDismiss: () -> Unit,
) {
    // The field owns its text: echoing it back through the ViewModel's asynchronous state drops
    // keystrokes while the IME is typing. The ViewModel only gets the query to search for.
    var query by rememberSaveable { mutableStateOf(citySearch.query) }
    var latitude by rememberSaveable { mutableStateOf("") }
    var longitude by rememberSaveable { mutableStateOf("") }
    var showCoordinateError by rememberSaveable { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sky_city_dialog_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = {
                        query = it
                        onQueryChange(it)
                    },
                    label = { Text(stringResource(R.string.sky_city_search_label)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(SKY_CITY_SEARCH_FIELD_TEST_TAG),
                )
                // A fixed-height area: if results changed the dialog's height while typing, the
                // dialog window would resize under the keyboard and drop keystrokes. The height
                // follows the font size and scrolls when large text no longer fits four rows.
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(rememberResultsHeight())
                        .verticalScroll(rememberScrollState())
                        .testTag(SKY_CITY_RESULTS_TEST_TAG),
                ) {
                    citySearch.results.take(MAX_RESULTS).forEach { city ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .clickable { onCitySelected(city) }
                                .padding(vertical = RESULT_VERTICAL_PADDING),
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Text(city.name, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                text = city.country,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (query.isNotBlank() && citySearch.query == query && citySearch.results.isEmpty()) {
                        Text(stringResource(R.string.sky_city_no_results), style = MaterialTheme.typography.bodySmall)
                    }
                }
                HorizontalDivider()
                SectionTitle(stringResource(R.string.sky_coordinates_title), style = MaterialTheme.typography.titleSmall)
                CoordinateField(
                    value = latitude,
                    onValueChange = { latitude = it; showCoordinateError = false },
                    label = stringResource(R.string.sky_latitude_label),
                    testTag = SKY_LATITUDE_FIELD_TEST_TAG,
                )
                CoordinateField(
                    value = longitude,
                    onValueChange = { longitude = it; showCoordinateError = false },
                    label = stringResource(R.string.sky_longitude_label),
                    testTag = SKY_LONGITUDE_FIELD_TEST_TAG,
                )
                if (showCoordinateError) {
                    Text(
                        text = stringResource(R.string.sky_coordinates_invalid),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                TextButton(
                    onClick = {
                        val coordinate = parseCoordinate(latitude, longitude)
                        if (coordinate == null) showCoordinateError = true else onCoordinatesEntered(coordinate)
                    },
                    modifier = Modifier.testTag(SKY_USE_COORDINATES_TEST_TAG),
                ) { Text(stringResource(R.string.sky_use_coordinates)) }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.sky_cancel)) } },
    )
}

/**
 * Room for [MAX_RESULTS] two-line rows at the current font scale (about 210 dp at 100 %), capped to a
 * share of the window so the coordinate fields stay reachable at 200 %; the rest scrolls.
 */
@Composable
private fun rememberResultsHeight(): Dp {
    val typography = MaterialTheme.typography
    val density = LocalDensity.current
    val windowHeightPx = LocalWindowInfo.current.containerSize.height
    return remember(typography, density, windowHeightPx) {
        with(density) {
            val row = (typography.bodyLarge.lineHeight.toDp() + typography.bodySmall.lineHeight.toDp() +
                RESULT_VERTICAL_PADDING * 2).coerceAtLeast(48.dp)
            val cap = (windowHeightPx * RESULTS_MAX_WINDOW_FRACTION).toDp()
            val wanted = row * MAX_RESULTS
            if (cap > 0.dp) wanted.coerceAtMost(cap) else wanted
        }
    }
}

@Composable
private fun CoordinateField(value: String, onValueChange: (String) -> Unit, label: String, testTag: String) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag),
    )
}

/** Accepts a decimal point or comma and a leading minus; null when out of range or not a number. */
internal fun parseCoordinate(latitude: String, longitude: String): GeoCoordinate? {
    val lat = latitude.trim().replace(',', '.').replace('−', '-').toDoubleOrNull() ?: return null
    val lon = longitude.trim().replace(',', '.').replace('−', '-').toDoubleOrNull() ?: return null
    if (lat !in -90.0..90.0 || lon !in -180.0..180.0) return null
    return GeoCoordinate(lat, lon)
}
