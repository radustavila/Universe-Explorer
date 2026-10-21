package com.radustavila.nasaexplorer.feature.sky.location

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LocationOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.radustavila.nasaexplorer.core.model.City
import com.radustavila.nasaexplorer.core.model.GeoCoordinate
import com.radustavila.nasaexplorer.feature.sky.R
import com.radustavila.nasaexplorer.feature.sky.component.LocationDialog
import com.radustavila.nasaexplorer.feature.sky.component.LocationHeader
import com.radustavila.nasaexplorer.feature.sky.component.LocationPrompt
import com.radustavila.nasaexplorer.feature.sky.component.rememberCoarseLocationRequest

const val OBSERVER_LOCATION_CLEAR_TEST_TAG = "observer-location-clear"

/**
 * The saved observer location, for screens outside Sky tonight (Settings, through the app, which
 * keeps features independent). Sky tonight's own prompt, header and city picker: where the app
 * thinks the user is, how to change it, and how to forget it.
 */
@Composable
fun ObserverLocationSection(
    modifier: Modifier = Modifier,
    viewModel: ObserverLocationViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    ObserverLocationContent(
        uiState = uiState,
        onLocateDevice = viewModel::onLocateDevice,
        onLocationPermissionDenied = viewModel::onLocationPermissionDenied,
        onQueryChange = viewModel::onCityQueryChange,
        onCitySelected = viewModel::onCitySelected,
        onCoordinatesEntered = viewModel::onCoordinatesEntered,
        onClear = viewModel::onClear,
        modifier = modifier,
    )
}

@Composable
internal fun ObserverLocationContent(
    uiState: ObserverLocationUiState,
    onLocateDevice: () -> Unit,
    onLocationPermissionDenied: () -> Unit,
    onQueryChange: (String) -> Unit,
    onCitySelected: (City) -> Unit,
    onCoordinatesEntered: (GeoCoordinate) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showDialog by rememberSaveable { mutableStateOf(false) }
    val onUseMyLocation = rememberCoarseLocationRequest(onGranted = onLocateDevice, onDenied = onLocationPermissionDenied)

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val location = uiState.location
        when {
            !uiState.isLoaded -> Unit
            location == null -> LocationPrompt(
                isLocating = uiState.isLocating,
                onUseMyLocation = onUseMyLocation,
                onChooseCity = { showDialog = true },
            )

            else -> {
                LocationHeader(
                    location = location,
                    isLocating = uiState.isLocating,
                    onUseMyLocation = onUseMyLocation,
                    onChangeLocation = { showDialog = true },
                )
                TextButton(onClick = onClear, modifier = Modifier.testTag(OBSERVER_LOCATION_CLEAR_TEST_TAG)) {
                    Icon(Icons.Outlined.LocationOff, contentDescription = null)
                    Text(stringResource(R.string.sky_location_clear), modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
        uiState.message?.let { message ->
            Text(
                text = stringResource(
                    when (message) {
                        ObserverLocationMessage.PERMISSION_DENIED -> R.string.sky_location_denied
                        ObserverLocationMessage.UNAVAILABLE -> R.string.sky_location_unavailable
                    },
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }

    if (showDialog) {
        LocationDialog(
            citySearch = uiState.citySearch,
            onQueryChange = onQueryChange,
            onCitySelected = { city ->
                onCitySelected(city)
                showDialog = false
            },
            onCoordinatesEntered = { coordinate ->
                onCoordinatesEntered(coordinate)
                showDialog = false
            },
            onDismiss = {
                onQueryChange("")
                showDialog = false
            },
        )
    }
}
