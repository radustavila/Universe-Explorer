package com.radustavila.nasaexplorer.core.map

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.radustavila.nasaexplorer.core.map.gibs.GibsImagery
import com.radustavila.nasaexplorer.core.map.internal.SpaceMapHostEffects
import com.radustavila.nasaexplorer.core.map.overlay.GeoPoint
import com.radustavila.nasaexplorer.core.map.overlay.MapOverlay
import com.radustavila.nasaexplorer.core.map.overlay.MapShapeClick

const val SPACE_MAP_TEST_TAG = "space-map"

/**
 * A pannable, zoomable map of Earth from orbit, drawn from NASA GIBS tiles by MapLibre.
 *
 * Blue Marble (cloud-free relief) is always the base, so the map shows something even where the
 * selected [imagery] has no tiles. Pass `null` to show the base alone. [overlays] are drawn above
 * the imagery; taps on their shapes arrive in [onShapeClick], other taps in [onMapClick].
 *
 * No MapLibre type appears in this API. Offline, MapLibre serves tiles from its ambient cache;
 * [SpaceMapState.tileStatus] says when tiles could not be fetched.
 */
@Composable
fun SpaceMap(
    imagery: GibsImagery?,
    modifier: Modifier = Modifier,
    state: SpaceMapState = rememberSpaceMapState(),
    overlays: List<MapOverlay> = emptyList(),
    onShapeClick: ((MapShapeClick) -> Unit)? = null,
    onMapClick: ((GeoPoint) -> Unit)? = null,
    showAttribution: Boolean = true,
) {
    Box(modifier.testTag(SPACE_MAP_TEST_TAG)) {
        if (LocalInspectionMode.current) {
            Box(Modifier.fillMaxSize().background(Color.Black))
        } else {
            MapLibreView(imagery, state, overlays, onShapeClick, onMapClick)
        }
        if (showAttribution) {
            Text(
                text = stringResource(R.string.core_map_attribution_gibs),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp)
                    .background(Color.Black.copy(alpha = ATTRIBUTION_SCRIM_ALPHA), MaterialTheme.shapes.small)
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
    }
}

@Composable
private fun MapLibreView(
    imagery: GibsImagery?,
    state: SpaceMapState,
    overlays: List<MapOverlay>,
    onShapeClick: ((MapShapeClick) -> Unit)?,
    onMapClick: ((GeoPoint) -> Unit)?,
) {
    // A state built with the plain constructor owns no view yet; the map then lives as long as
    // this composable does.
    val ownsHost = remember(state) { state.host == null }
    if (ownsHost) SpaceMapHostEffects(state)
    val host = state.host ?: return

    SideEffect {
        host.controller.onShapeClick = onShapeClick
        host.controller.onMapClick = onMapClick
        host.controller.setImagery(imagery)
        host.controller.setOverlays(overlays)
    }

    key(host) {
        AndroidView(factory = { host.detachedMapView() }, modifier = Modifier.fillMaxSize())
    }
}

private const val ATTRIBUTION_SCRIM_ALPHA = 0.55f
