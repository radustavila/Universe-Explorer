package com.radustavila.nasaexplorer.feature.moonmap

import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.radustavila.nasaexplorer.core.designsystem.units.rememberUnitFormatter
import com.radustavila.nasaexplorer.core.designsystem.util.formatMedium
import com.radustavila.nasaexplorer.core.designsystem.util.rememberCustomTabOpener
import com.radustavila.nasaexplorer.core.domain.moon.MoonTerminator
import com.radustavila.nasaexplorer.core.map.MapCamera
import com.radustavila.nasaexplorer.core.map.SpaceMap
import com.radustavila.nasaexplorer.core.map.SpaceMapStyle
import com.radustavila.nasaexplorer.core.map.overlay.GeoPoint
import com.radustavila.nasaexplorer.core.map.overlay.MapOverlay
import com.radustavila.nasaexplorer.core.map.rememberSpaceMapState
import com.radustavila.nasaexplorer.core.model.LunarLandingSite
import com.radustavila.nasaexplorer.core.model.LunarProgramme
import com.radustavila.nasaexplorer.core.model.MoonIllumination
import com.radustavila.nasaexplorer.core.model.MoonTilesPack
import kotlin.math.roundToInt

const val MOON_MAP_FILTERS_TEST_TAG = "moon-map-filters"
const val MOON_MAP_TILES_PANEL_TEST_TAG = "moon-map-tiles"
const val MOON_MAP_SITE_SHEET_TEST_TAG = "moon-map-site-sheet"

/**
 * The map's slot, so tests can render the controls without MapLibre's native renderer. A tap on a
 * landing site comes back as its id through `onSiteClick`, any other tap through `onMapClick`.
 */
typealias MoonMapSlot = @Composable (
    deepTilesDirectory: String?,
    overlays: List<MapOverlay>,
    onSiteClick: (siteId: String) -> Unit,
    onMapClick: () -> Unit,
    modifier: Modifier,
) -> Unit

@Composable
internal fun MoonMapRoute(
    onBackClick: () -> Unit,
    viewModel: MoonMapViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val mapState = rememberSpaceMapState(initialCamera = MOON_START)
    val openPage = rememberCustomTabOpener()

    MoonMapScreen(
        uiState = uiState,
        onBackClick = onBackClick,
        onProgrammeToggled = viewModel::onProgrammeToggled,
        onSiteSelected = viewModel::onSiteSelected,
        onSiteDismissed = viewModel::onSiteDismissed,
        onDownloadTiles = viewModel::onDownloadTiles,
        onRemoveTiles = viewModel::onRemoveTiles,
        onOpenPage = openPage,
        map = { deepTilesDirectory, overlays, onSiteClick, onMapClick, modifier ->
            SpaceMap(
                style = SpaceMapStyle.Moon(deepTilesDirectory),
                state = mapState,
                overlays = overlays,
                onShapeClick = { click -> if (click.overlayId != NIGHT_OVERLAY_ID) onSiteClick(click.shapeId) },
                onMapClick = { onMapClick() },
                modifier = modifier,
            )
        },
    )
}

/**
 * Stateless Moon map screen: the map fills it, filter chips (one per programme) sit on top, and a
 * panel at the bottom offers the detailed tiles and says how old the terminator is. A tapped site
 * opens its sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoonMapScreen(
    uiState: MoonMapUiState,
    onBackClick: () -> Unit,
    onProgrammeToggled: (LunarProgramme) -> Unit,
    onSiteSelected: (String) -> Unit,
    onSiteDismissed: () -> Unit,
    onDownloadTiles: () -> Unit,
    onRemoveTiles: () -> Unit,
    onOpenPage: (String) -> Unit,
    map: MoonMapSlot,
    modifier: Modifier = Modifier,
) {
    val overlays = remember(uiState.nightSide, uiState.sites, uiState.selectedSite) {
        moonMapOverlays(uiState.nightSide, uiState.sites, uiState.selectedSite)
    }
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.moon_map_title)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.moon_map_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Box(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            map(
                (uiState.tiles as? MoonTilesPack.Installed)?.directory,
                overlays,
                onSiteSelected,
                onSiteDismissed,
                Modifier.fillMaxSize(),
            )
            ProgrammeChips(
                programmes = uiState.programmes,
                shown = uiState.shownProgrammes,
                onToggle = onProgrammeToggled,
                modifier = Modifier.align(Alignment.TopStart),
            )
            BottomPanel(
                illumination = uiState.illumination,
                tiles = uiState.tiles,
                onDownloadTiles = onDownloadTiles,
                onRemoveTiles = onRemoveTiles,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = 12.dp, end = 12.dp, bottom = 32.dp)
                    .widthIn(max = 560.dp),
            )
        }
    }

    uiState.selectedSite?.let { site ->
        ModalBottomSheet(
            onDismissRequest = onSiteDismissed,
            modifier = Modifier.testTag(MOON_MAP_SITE_SHEET_TEST_TAG),
        ) {
            SiteDetails(site = site, illumination = uiState.illumination, onOpenPage = onOpenPage)
        }
    }
}

@Composable
private fun ProgrammeChips(
    programmes: List<LunarProgramme>,
    shown: Set<LunarProgramme>,
    onToggle: (LunarProgramme) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier.fillMaxWidth().testTag(MOON_MAP_FILTERS_TEST_TAG),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(programmes, key = LunarProgramme::name) { programme ->
            FilterChip(
                selected = programme in shown,
                onClick = { onToggle(programme) },
                label = { Text(stringResource(programme.labelRes)) },
                leadingIcon = {
                    Box(Modifier.size(10.dp).background(programme.color, CircleShape))
                },
            )
        }
    }
}

@Composable
private fun BottomPanel(
    illumination: MoonIllumination?,
    tiles: MoonTilesPack?,
    onDownloadTiles: () -> Unit,
    onRemoveTiles: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val units = rememberUnitFormatter()
    Surface(
        modifier = modifier.fillMaxWidth().testTag(MOON_MAP_TILES_PANEL_TEST_TAG),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = PANEL_ALPHA),
    ) {
        Column(
            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (tiles != null) TilesState(tiles, onDownloadTiles, onRemoveTiles)
            illumination?.let {
                Text(
                    text = stringResource(R.string.moon_map_lit_as_of, units.time(it.instant)),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Text(
                text = stringResource(R.string.moon_map_pole_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TilesState(tiles: MoonTilesPack, onDownloadTiles: () -> Unit, onRemoveTiles: () -> Unit) {
    val context = LocalContext.current
    fun size(bytes: Long) = Formatter.formatShortFileSize(context, bytes)
    when (tiles) {
        is MoonTilesPack.NotDownloaded -> ActionRow(
            text = stringResource(R.string.moon_map_tiles_offer),
            action = tiles.sizeBytes?.let { stringResource(R.string.moon_map_tiles_download_size, size(it)) }
                ?: stringResource(R.string.moon_map_tiles_download),
            onAction = onDownloadTiles,
        )
        is MoonTilesPack.Downloading -> {
            val fraction = tiles.fraction
            Text(
                text = fraction?.let {
                    stringResource(R.string.moon_map_tiles_downloading_progress, (it * PERCENT).roundToInt())
                } ?: stringResource(R.string.moon_map_tiles_downloading),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (fraction != null) {
                LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }
        MoonTilesPack.WaitingForWifi -> Text(
            text = stringResource(R.string.moon_map_tiles_waiting_for_wifi),
            style = MaterialTheme.typography.bodyMedium,
        )
        is MoonTilesPack.Installed -> ActionRow(
            text = stringResource(R.string.moon_map_tiles_installed, size(tiles.sizeBytes)),
            action = stringResource(R.string.moon_map_tiles_remove),
            onAction = onRemoveTiles,
            tonal = false,
        )
        is MoonTilesPack.Failed -> ActionRow(
            text = stringResource(tiles.reason.labelRes),
            action = stringResource(R.string.moon_map_tiles_retry),
            onAction = onDownloadTiles,
        )
        MoonTilesPack.Unavailable -> Text(
            text = stringResource(R.string.moon_map_tiles_unavailable),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun ActionRow(text: String, action: String, onAction: () -> Unit, tonal: Boolean = true) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text = text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Spacer(Modifier.size(8.dp))
        if (tonal) {
            FilledTonalButton(onClick = onAction) { Text(action) }
        } else {
            TextButton(onClick = onAction) { Text(action) }
        }
    }
}

@Composable
internal fun SiteDetails(site: LunarLandingSite, illumination: MoonIllumination?, onOpenPage: (String) -> Unit) {
    val units = rememberUnitFormatter()
    Column(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(site.mission, style = MaterialTheme.typography.headlineSmall)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(10.dp).background(site.programme.color, CircleShape))
            Text(
                text = stringResource(
                    R.string.moon_map_site_programme_kind,
                    stringResource(site.programme.labelRes),
                    stringResource(site.kind.labelRes),
                ),
                style = MaterialTheme.typography.titleSmall,
            )
        }
        Text(stringResource(R.string.moon_map_site_landed, site.landedOn.formatMedium()))
        Text(stringResource(R.string.moon_map_site_region, site.region))
        Text(
            stringResource(
                R.string.moon_map_site_position,
                units.coordinate(site.position.latitude, site.position.longitude, fractionDigits = 2),
            ),
        )
        if (site.crew.isNotEmpty()) Text(stringResource(R.string.moon_map_site_crew, site.crew.joinToString()))
        site.rover?.let { Text(stringResource(R.string.moon_map_site_rover, it)) }
        illumination?.let {
            val dark = MoonTerminator.isDark(site.position, it.subsolarPoint)
            Text(
                text = stringResource(
                    if (dark) R.string.moon_map_site_in_night else R.string.moon_map_site_in_daylight,
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        site.nssdcaUrl?.let { url ->
            TextButton(onClick = { onOpenPage(url) }) { Text(stringResource(R.string.moon_map_site_record)) }
        }
    }
}

/** The near side, whole Moon in view. */
private val MOON_START = MapCamera(center = GeoPoint(latitude = 0.0, longitude = 0.0), zoom = 1.0)

private const val PANEL_ALPHA = 0.92f
private const val PERCENT = 100
