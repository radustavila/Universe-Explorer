package com.radustavila.nasaexplorer.feature.moonmap

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.radustavila.nasaexplorer.core.map.overlay.GeoPoint
import com.radustavila.nasaexplorer.core.map.overlay.MapMarker
import com.radustavila.nasaexplorer.core.map.overlay.MapOverlay
import com.radustavila.nasaexplorer.core.map.overlay.MapPolygon
import com.radustavila.nasaexplorer.core.model.LunarLandingSite
import com.radustavila.nasaexplorer.core.model.LunarProgramme
import com.radustavila.nasaexplorer.core.model.SelenographicPoint

internal const val NIGHT_OVERLAY_ID = "moon-night"
internal const val SITES_OVERLAY_ID = "moon-sites"
internal const val SELECTED_OVERLAY_ID = "moon-site-selected"

/**
 * The map's overlays, bottom to top: the night side shaded, the landing sites coloured by
 * programme, and a ring around the selected one.
 */
internal fun moonMapOverlays(
    nightSide: List<List<SelenographicPoint>>,
    sites: List<LunarLandingSite>,
    selected: LunarLandingSite?,
): List<MapOverlay> = listOf(
    MapOverlay(
        id = NIGHT_OVERLAY_ID,
        shapes = nightSide.mapIndexed { index, ring ->
            MapPolygon(id = "night-$index", outline = ring.map(SelenographicPoint::toGeoPoint), fillColor = NIGHT_SHADE)
        },
    ),
    MapOverlay(
        id = SITES_OVERLAY_ID,
        shapes = sites.map { site ->
            MapMarker(id = site.id, position = site.position.toGeoPoint(), color = site.programme.color, radius = 6.dp)
        },
    ),
    MapOverlay(
        id = SELECTED_OVERLAY_ID,
        shapes = listOfNotNull(
            selected?.let { site ->
                MapMarker(
                    id = site.id,
                    position = site.position.toGeoPoint(),
                    color = site.programme.color,
                    radius = 10.dp,
                    strokeWidth = 3.dp,
                )
            },
        ),
    ),
)

internal fun SelenographicPoint.toGeoPoint() = GeoPoint(
    latitude.coerceIn(-MAX_LATITUDE, MAX_LATITUDE),
    longitude.coerceIn(-MAX_LONGITUDE, MAX_LONGITUDE),
)

private const val MAX_LATITUDE = 90.0
private const val MAX_LONGITUDE = 180.0

/** The night side: dark enough to read as night, light enough to keep the craters under it. */
private val NIGHT_SHADE = Color.Black.copy(alpha = 0.55f)

/** One colour per programme, for the markers and the chips' dots. */
internal val LunarProgramme.color: Color
    get() = when (this) {
        LunarProgramme.APOLLO -> Color(0xFFFFC857)
        LunarProgramme.LUNA -> Color(0xFFEF6461)
        LunarProgramme.SURVEYOR -> Color(0xFF5DADE2)
        LunarProgramme.CHANGE -> Color(0xFFFF9248)
        LunarProgramme.CHANDRAYAAN -> Color(0xFF7BD389)
        LunarProgramme.SLIM -> Color(0xFFC39BD3)
        LunarProgramme.CLPS -> Color(0xFF4DD0E1)
    }
