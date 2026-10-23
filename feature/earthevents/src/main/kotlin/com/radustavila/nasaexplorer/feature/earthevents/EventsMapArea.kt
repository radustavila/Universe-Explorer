package com.radustavila.nasaexplorer.feature.earthevents

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.radustavila.nasaexplorer.core.designsystem.earthquake.EarthquakeDepthLegend
import com.radustavila.nasaexplorer.core.designsystem.earthquake.EarthquakeSheet
import com.radustavila.nasaexplorer.core.model.reportAgeDays

@Composable
internal fun RowScope.TopBarActions(
    uiState: EarthEventsUiState,
    onRefresh: () -> Unit,
    onEarthquakeListToggled: () -> Unit,
) {
    if (uiState.earthquakeFilter != null) {
        val showsList = uiState.showEarthquakeList
        IconButton(onClick = onEarthquakeListToggled) {
            Icon(
                imageVector = if (showsList) Icons.Outlined.Map else Icons.AutoMirrored.Outlined.List,
                contentDescription = stringResource(
                    if (showsList) R.string.earth_events_quakes_show_map else R.string.earth_events_quakes_show_list,
                ),
            )
        }
    }
    if (uiState.isRefreshing) {
        CircularProgressIndicator(modifier = Modifier.padding(12.dp).size(24.dp), strokeWidth = 2.dp)
    } else {
        IconButton(onClick = onRefresh) {
            Icon(Icons.Outlined.Refresh, contentDescription = stringResource(R.string.earth_events_refresh))
        }
    }
}

/** What taps on the map, its sheets and the quake list do. */
internal class EventsMapActions(
    val onEventSelected: (String) -> Unit,
    val onEventDismissed: () -> Unit,
    val onShowImagery: () -> Unit,
    val onHideImagery: () -> Unit,
    val onOpenSource: (String) -> Unit,
    val onEarthquakeSelected: (String) -> Unit,
    val onEarthquakeDismissed: () -> Unit,
    val onEarthquakeListItemSelected: (String) -> Unit,
    val onOpenEarthquakePage: (String) -> Unit,
) {
    fun onShapeClick(overlayId: String, shapeId: String) {
        if (isEarthquakeOverlay(overlayId)) onEarthquakeSelected(shapeId) else onEventSelected(eventIdOf(shapeId))
    }

    fun onMapClick() {
        onEventDismissed()
        onEarthquakeDismissed()
    }
}

/** The quake list, or the map with its overlays, notes and the selected event's or quake's sheet. */
@Composable
internal fun EventsMapArea(
    uiState: EarthEventsUiState,
    isLoadingMapImagery: Boolean,
    actions: EventsMapActions,
    map: EarthEventsMap,
    modifier: Modifier = Modifier,
) {
    if (uiState.showEarthquakeList) {
        EarthquakeList(uiState.visibleEarthquakes, actions.onEarthquakeListItemSelected, modifier)
        return
    }
    BoxWithConstraints(modifier) {
        val shownEvents = uiState.shownEvents
        // The selection lives in its own overlay, so picking an event leaves the (large) base
        // overlays untouched and the map only redraws the highlight.
        val baseOverlays = remember(shownEvents, uiState.hiddenCategories, uiState.today) {
            baseEventOverlays(shownEvents, uiState.hiddenCategories, uiState.today)
        }
        val selection = remember(shownEvents, uiState.hiddenCategories, uiState.selectedEventId) {
            selectionOverlay(shownEvents, uiState.hiddenCategories, uiState.selectedEventId)
        }
        val quakes = uiState.visibleEarthquakes
        val quakeOverlay = remember(quakes) { earthquakeOverlay(quakes) }
        val selectedQuake = uiState.selectedEarthquake
        val quakeSelection = remember(selectedQuake) { earthquakeSelectionOverlay(selectedQuake) }
        val overlays = remember(baseOverlays, selection, quakeOverlay, quakeSelection) {
            baseOverlays + quakeOverlay + selection + quakeSelection
        }
        map(uiState.imagery, overlays, actions::onShapeClick, actions::onMapClick, Modifier.fillMaxSize())

        MapNotes(
            isLoadingImagery = isLoadingMapImagery,
            // The sheets cover the bottom of the map; the note can wait until they close.
            showsGapNote = uiState.imagery?.layers.orEmpty().any { it.hasNoDataGaps } &&
                uiState.selectedEvent == null && selectedQuake == null,
        )
        if (quakes.isNotEmpty()) EarthquakeDepthLegend(Modifier.align(Alignment.TopEnd))

        if (uiState.isLoaded && shownEvents.isEmpty() && quakes.isEmpty()) {
            EmptyNotice(
                isOffline = uiState.isOffline,
                isRefreshing = uiState.isRefreshing,
                modifier = Modifier.align(Alignment.Center).padding(24.dp),
            )
        }

        val isLandscape = maxWidth > maxHeight
        val sheetModifier = Modifier
            .align(if (isLandscape) Alignment.BottomStart else Alignment.BottomCenter)
            .padding(12.dp)
            .then(if (isLandscape) Modifier.width(SHEET_LANDSCAPE_WIDTH) else Modifier.fillMaxWidth())
        selectedQuake?.let { quake ->
            EarthquakeSheet(quake, actions.onOpenEarthquakePage, actions.onEarthquakeDismissed, sheetModifier)
        }
        uiState.selectedEvent?.let { event ->
            EventSheet(
                event = event,
                reportAgeDays = event.reportAgeDays(uiState.today),
                isShowingImagery = uiState.eventImagery?.eventId == event.id,
                isLoadingImagery = uiState.isLoadingImagery,
                onShowImagery = actions.onShowImagery,
                onHideImagery = actions.onHideImagery,
                onOpenSource = actions.onOpenSource,
                onClose = actions.onEventDismissed,
                modifier = sheetModifier,
            )
        }
    }
}
