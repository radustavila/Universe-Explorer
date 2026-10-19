package com.radustavila.nasaexplorer.feature.earthevents

import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AcUnit
import androidx.compose.material.icons.outlined.Air
import androidx.compose.material.icons.outlined.Cyclone
import androidx.compose.material.icons.outlined.Factory
import androidx.compose.material.icons.outlined.Flood
import androidx.compose.material.icons.outlined.Landslide
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.Thermostat
import androidx.compose.material.icons.outlined.Volcano
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material.icons.outlined.Waves
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.radustavila.nasaexplorer.core.model.EarthEventCategory

/**
 * Marker colour of each category. Bright and saturated so they stand out on satellite imagery,
 * which is mostly dark blue, green and white.
 */
internal val EarthEventCategory.color: Color
    get() = when (this) {
        EarthEventCategory.WILDFIRES -> Color(0xFFFF6D00)
        EarthEventCategory.SEVERE_STORMS -> Color(0xFFD500F9)
        EarthEventCategory.VOLCANOES -> Color(0xFFFF1744)
        EarthEventCategory.SEA_LAKE_ICE -> Color(0xFF18FFFF)
        EarthEventCategory.FLOODS -> Color(0xFF2979FF)
        EarthEventCategory.DROUGHT -> Color(0xFFC6A15B)
        EarthEventCategory.DUST_HAZE -> Color(0xFFFFD180)
        EarthEventCategory.EARTHQUAKES -> Color(0xFFFFEA00)
        EarthEventCategory.LANDSLIDES -> Color(0xFFA1887F)
        EarthEventCategory.MANMADE -> Color(0xFFB0BEC5)
        EarthEventCategory.SNOW -> Color(0xFFFFFFFF)
        EarthEventCategory.TEMP_EXTREMES -> Color(0xFFFFAB40)
        EarthEventCategory.WATER_COLOR -> Color(0xFF1DE9B6)
    }

internal val EarthEventCategory.icon: ImageVector
    get() = when (this) {
        EarthEventCategory.WILDFIRES -> Icons.Outlined.LocalFireDepartment
        EarthEventCategory.SEVERE_STORMS -> Icons.Outlined.Cyclone
        EarthEventCategory.VOLCANOES -> Icons.Outlined.Volcano
        EarthEventCategory.SEA_LAKE_ICE, EarthEventCategory.SNOW -> Icons.Outlined.AcUnit
        EarthEventCategory.FLOODS -> Icons.Outlined.Flood
        EarthEventCategory.DROUGHT -> Icons.Outlined.WbSunny
        EarthEventCategory.DUST_HAZE -> Icons.Outlined.Air
        EarthEventCategory.EARTHQUAKES -> Icons.Outlined.Waves
        EarthEventCategory.LANDSLIDES -> Icons.Outlined.Landslide
        EarthEventCategory.MANMADE -> Icons.Outlined.Factory
        EarthEventCategory.TEMP_EXTREMES -> Icons.Outlined.Thermostat
        EarthEventCategory.WATER_COLOR -> Icons.Outlined.WaterDrop
    }

@get:StringRes
internal val EarthEventCategory.labelRes: Int
    get() = when (this) {
        EarthEventCategory.DROUGHT -> R.string.earth_events_category_drought
        EarthEventCategory.DUST_HAZE -> R.string.earth_events_category_dust_haze
        EarthEventCategory.EARTHQUAKES -> R.string.earth_events_category_earthquakes
        EarthEventCategory.FLOODS -> R.string.earth_events_category_floods
        EarthEventCategory.LANDSLIDES -> R.string.earth_events_category_landslides
        EarthEventCategory.MANMADE -> R.string.earth_events_category_manmade
        EarthEventCategory.SEA_LAKE_ICE -> R.string.earth_events_category_sea_lake_ice
        EarthEventCategory.SEVERE_STORMS -> R.string.earth_events_category_severe_storms
        EarthEventCategory.SNOW -> R.string.earth_events_category_snow
        EarthEventCategory.TEMP_EXTREMES -> R.string.earth_events_category_temp_extremes
        EarthEventCategory.VOLCANOES -> R.string.earth_events_category_volcanoes
        EarthEventCategory.WATER_COLOR -> R.string.earth_events_category_water_color
        EarthEventCategory.WILDFIRES -> R.string.earth_events_category_wildfires
    }

/** "192 active wildfires", for the Today card. */
@get:PluralsRes
internal val EarthEventCategory.countRes: Int
    get() = when (this) {
        EarthEventCategory.DROUGHT -> R.plurals.earth_events_count_drought
        EarthEventCategory.DUST_HAZE -> R.plurals.earth_events_count_dust_haze
        EarthEventCategory.EARTHQUAKES -> R.plurals.earth_events_count_earthquakes
        EarthEventCategory.FLOODS -> R.plurals.earth_events_count_floods
        EarthEventCategory.LANDSLIDES -> R.plurals.earth_events_count_landslides
        EarthEventCategory.MANMADE -> R.plurals.earth_events_count_manmade
        EarthEventCategory.SEA_LAKE_ICE -> R.plurals.earth_events_count_sea_lake_ice
        EarthEventCategory.SEVERE_STORMS -> R.plurals.earth_events_count_severe_storms
        EarthEventCategory.SNOW -> R.plurals.earth_events_count_snow
        EarthEventCategory.TEMP_EXTREMES -> R.plurals.earth_events_count_temp_extremes
        EarthEventCategory.VOLCANOES -> R.plurals.earth_events_count_volcanoes
        EarthEventCategory.WATER_COLOR -> R.plurals.earth_events_count_water_color
        EarthEventCategory.WILDFIRES -> R.plurals.earth_events_count_wildfires
    }
