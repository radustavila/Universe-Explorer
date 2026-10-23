package com.radustavila.nasaexplorer.core.designsystem.earthquake

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.radustavila.nasaexplorer.core.model.Earthquake
import com.radustavila.nasaexplorer.core.model.EarthquakeDepth

/**
 * Marker colour by depth: hot for shallow quakes (the damaging ones), cooler deeper down. Bright
 * enough for both the dark Blue Marble and daylight imagery.
 */
val EarthquakeDepth.color: Color
    get() = when (this) {
        EarthquakeDepth.SHALLOW -> Color(0xFFFF3D00)
        EarthquakeDepth.INTERMEDIATE -> Color(0xFFFFC400)
        EarthquakeDepth.DEEP -> Color(0xFF00E676)
    }

/**
 * Marker radius by magnitude, on screen: each step of one magnitude adds a little over 2 dp, from
 * 3 dp for an M2.5 to 16 dp for an M8.
 */
val Earthquake.markerRadius: Dp
    get() = (MIN_RADIUS_DP + (magnitude - SMALLEST_MAGNITUDE) * DP_PER_MAGNITUDE)
        .coerceIn(MIN_RADIUS_DP, MAX_RADIUS_DP)
        .toFloat()
        .dp

private const val SMALLEST_MAGNITUDE = 2.5
private const val MIN_RADIUS_DP = 3.0
private const val MAX_RADIUS_DP = 16.0
private const val DP_PER_MAGNITUDE = 2.35
