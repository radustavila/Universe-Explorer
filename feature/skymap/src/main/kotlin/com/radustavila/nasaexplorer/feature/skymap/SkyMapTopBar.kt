package com.radustavila.nasaexplorer.feature.skymap

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.ViewInAr
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

const val SKY_MAP_SEARCH_FIELD_TEST_TAG = "sky-map-search-field"

private val TOP_BAR_MAX_WIDTH = 720.dp
private val RESULTS_MAX_HEIGHT = 360.dp

/**
 * Back, title or search field, and the switches for compass, AR and night mode, over the sky. Red
 * night mode is off while AR shows the camera, whose bright picture spoils dark-adapted eyes anyway.
 */
@Composable
internal fun SkyMapTopBar(
    uiState: SkyMapUiState,
    camera: SkyCameraState,
    hasCompass: Boolean,
    isArOn: Boolean,
    onArClick: () -> Unit,
    onBackClick: () -> Unit,
    onSelect: (SkyTarget?) -> Unit,
    onNightModeChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var isSearching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    val names = rememberSkyNames()
    val closeSearch = {
        isSearching = false
        query = ""
    }
    Column(modifier.statusBarsPadding().widthIn(max = TOP_BAR_MAX_WIDTH).fillMaxWidth().padding(horizontal = 4.dp)) {
        Surface(
            color = translucentSurface(),
            contentColor = MaterialTheme.colorScheme.onSurface,
            shape = MaterialTheme.shapes.extraLarge,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = stringResource(R.string.sky_map_back),
                    )
                }
                if (isSearching) {
                    SearchField(query, onQueryChange = { query = it }, onClose = closeSearch)
                } else {
                    Title(canSearch = uiState.sky != null, onSearchClick = { isSearching = true })
                }
                if (hasCompass) CompassToggle(camera)
                ArToggle(isArOn, onArClick)
                NightModeToggle(uiState.isNightMode && !isArOn, onNightModeChange, enabled = !isArOn)
            }
        }
        val sky = uiState.sky
        if (isSearching && sky != null && query.isNotBlank()) {
            val results = remember(query, sky.chart, uiState.bodies?.satellites, names) {
                searchSky(query, sky.chart.catalog, uiState.bodies, names)
            }
            SearchResults(results) { target ->
                closeSearch()
                onSelect(target)
            }
        }
    }
}

@Composable
private fun RowScope.Title(canSearch: Boolean, onSearchClick: () -> Unit) {
    Text(
        text = stringResource(R.string.sky_map_title),
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.weight(1f).padding(start = 4.dp),
    )
    IconButton(onClick = onSearchClick, enabled = canSearch) {
        Icon(Icons.Outlined.Search, contentDescription = stringResource(R.string.sky_map_search))
    }
}

@Composable
private fun RowScope.SearchField(query: String, onQueryChange: (String) -> Unit, onClose: () -> Unit) {
    val focusRequester = remember { FocusRequester() }
    TextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = {
            Text(stringResource(R.string.sky_map_search_hint), maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
        ),
        modifier = Modifier.weight(1f).focusRequester(focusRequester).testTag(SKY_MAP_SEARCH_FIELD_TEST_TAG),
    )
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    IconButton(onClick = onClose) {
        Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.sky_map_close_search))
    }
}

@Composable
private fun CompassToggle(camera: SkyCameraState) {
    val isCompass = camera.mode == CameraMode.COMPASS
    IconToggleButton(
        checked = isCompass,
        onCheckedChange = { camera.mode = if (it) CameraMode.COMPASS else CameraMode.TOUCH },
    ) {
        Icon(
            imageVector = if (isCompass) Icons.Outlined.Explore else Icons.Outlined.TouchApp,
            contentDescription = stringResource(
                if (isCompass) R.string.sky_map_touch_mode else R.string.sky_map_compass_mode,
            ),
        )
    }
}

@Composable
private fun ArToggle(isArOn: Boolean, onArClick: () -> Unit) {
    IconToggleButton(checked = isArOn, onCheckedChange = { onArClick() }) {
        Icon(
            imageVector = Icons.Outlined.ViewInAr,
            contentDescription = stringResource(if (isArOn) R.string.sky_map_ar_off else R.string.sky_map_ar_on),
        )
    }
}

@Composable
private fun NightModeToggle(isNightMode: Boolean, onNightModeChange: (Boolean) -> Unit, enabled: Boolean) {
    IconToggleButton(checked = isNightMode, onCheckedChange = onNightModeChange, enabled = enabled) {
        Icon(
            imageVector = Icons.Outlined.DarkMode,
            contentDescription = stringResource(
                if (isNightMode) R.string.sky_map_night_mode_off else R.string.sky_map_night_mode_on,
            ),
        )
    }
}

@Composable
private fun SearchResults(results: List<SkySearchResult>, onResultClick: (SkyTarget) -> Unit) {
    Surface(
        color = translucentSurface(),
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.padding(top = 4.dp).fillMaxWidth(),
    ) {
        if (results.isEmpty()) {
            Text(stringResource(R.string.sky_map_no_results), Modifier.padding(16.dp))
        } else {
            LazyColumn(Modifier.heightIn(max = RESULTS_MAX_HEIGHT)) {
                items(results, key = { it.target.toString() }) { result ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onResultClick(result.target) }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                    ) {
                        Text(result.name, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            text = stringResource(result.target.kindRes()),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
