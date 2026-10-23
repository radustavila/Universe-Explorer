package com.radustavila.nasaexplorer.core.designsystem.earthquake

import android.content.res.Resources
import androidx.annotation.StringRes
import com.radustavila.nasaexplorer.core.designsystem.R
import com.radustavila.nasaexplorer.core.designsystem.units.UnitFormatter
import com.radustavila.nasaexplorer.core.model.Earthquake
import com.radustavila.nasaexplorer.core.model.EarthquakeAlertLevel
import com.radustavila.nasaexplorer.core.model.EarthquakeDepth

/**
 * How a quake is worded, shared by the events map, the satellite map and the strong-quake
 * notification so all say the same thing. Distances and depths follow the user's units.
 */
object EarthquakeText {

    /** "M 5.7". */
    fun magnitude(resources: Resources, formatter: UnitFormatter, magnitude: Double): String =
        resources.getString(R.string.earthquake_magnitude, formatter.number(magnitude, fractionDigits = 1))

    /** "61 km WNW of Leni, Italy" (or "38 mi WNW of …"), USGS's own text for other places. */
    fun place(resources: Resources, formatter: UnitFormatter, quake: Earthquake): String {
        val place = quake.parsedPlace ?: return resources.getString(R.string.earthquake_place_unknown)
        val distance = place.distanceKm
        val direction = place.direction
        return if (distance != null && direction != null) {
            resources.getString(R.string.earthquake_place_offset, formatter.distance(distance), direction, place.name)
        } else {
            place.name
        }
    }

    /** "M 5.7, 61 km WNW of Leni, Italy". */
    fun title(resources: Resources, formatter: UnitFormatter, quake: Earthquake): String = resources.getString(
        R.string.earthquake_title,
        magnitude(resources, formatter, quake.magnitude),
        place(resources, formatter, quake),
    )

    /** "10 km deep, shallow". */
    fun depth(resources: Resources, formatter: UnitFormatter, quake: Earthquake): String = resources.getString(
        R.string.earthquake_depth,
        formatter.distance(quake.depthKm),
        resources.getString(depthRes(quake.depth)),
    )

    @StringRes
    fun depthRes(depth: EarthquakeDepth): Int = when (depth) {
        EarthquakeDepth.SHALLOW -> R.string.earthquake_depth_shallow
        EarthquakeDepth.INTERMEDIATE -> R.string.earthquake_depth_intermediate
        EarthquakeDepth.DEEP -> R.string.earthquake_depth_deep
    }

    /** "Shallow", for the map legend. */
    @StringRes
    fun depthLegendRes(depth: EarthquakeDepth): Int = when (depth) {
        EarthquakeDepth.SHALLOW -> R.string.earthquake_depth_legend_shallow
        EarthquakeDepth.INTERMEDIATE -> R.string.earthquake_depth_legend_intermediate
        EarthquakeDepth.DEEP -> R.string.earthquake_depth_legend_deep
    }

    @StringRes
    fun alertRes(level: EarthquakeAlertLevel): Int = when (level) {
        EarthquakeAlertLevel.GREEN -> R.string.earthquake_alert_green
        EarthquakeAlertLevel.YELLOW -> R.string.earthquake_alert_yellow
        EarthquakeAlertLevel.ORANGE -> R.string.earthquake_alert_orange
        EarthquakeAlertLevel.RED -> R.string.earthquake_alert_red
    }
}
